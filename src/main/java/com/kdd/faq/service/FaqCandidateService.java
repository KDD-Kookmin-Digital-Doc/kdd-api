package com.kdd.faq.service;

import com.kdd.ai.dto.AiFaqAnalyzeResponse;
import com.kdd.faq.dto.FaqCandidateApproveRequest;
import com.kdd.faq.dto.FaqCandidateResponse;
import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.entity.Faq;
import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqCandidateStatus;
import com.kdd.faq.entity.FaqTopic;
import com.kdd.faq.repository.FaqCandidateRepository;
import com.kdd.faq.repository.FaqRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqCandidateService {

    private final FaqCandidateRepository faqCandidateRepository;
    private final FaqRepository faqRepository;

    // FAQ/Document 컨벤션과 동일 — pageSize 상한으로 인증된 클라이언트의 비정상 페이로드 차단 (#58 패턴).
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort SORT_LATEST = Sort.by("createdAt", "id").descending();

    // AI 응답에서 inject되는 후보 텍스트의 최대 길이 — TEXT 컬럼이라 DB 제약은 없지만, 비정상 응답이나
    // 프롬프트 인젝션이 거대 페이로드로 후보 테이블/관리자 UI를 망가뜨리는 경로를 차단한다.
    private static final int MAX_QUESTION_LENGTH = 2000;
    private static final int MAX_ANSWER_DRAFT_LENGTH = 4000;
    // 추천 질문 API limit 상한 — FE는 보통 5건만 노출하므로 20이면 충분한 여유. 큰 값으로 호출돼도
    // PENDING 후보 전체 스캔/메모리 적재로 이어지지 않도록 차단한다.
    private static final int MAX_RECOMMENDED_LIMIT = 20;

    // PII 정규식 — 학번(20YYXXXX), 이메일, 전화번호. 사용자 질문 원문이 AI 클러스터링/요약을 거쳐도
    // 그대로 후보 question에 들어올 수 있어, 추천 질문(FE 노출)로 새기 전 BE 단에서 한 번 더 마스킹한다.
    // 보수적으로 마스킹만 한다 — 후보를 통째로 버리면 인기 클러스터 손실이 크다.
    private static final Pattern PII_STUDENT_ID = Pattern.compile("\\b20\\d{6}\\b");
    private static final Pattern PII_EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    // 모바일(01X), 서울 국번(02), 지역 국번(03X~06X), 인터넷 전화(070), 평생번호(050X)까지 커버.
    // 학교 부서/교무팀 전화(02-910-xxxx, 02-6324-xxxx 등)가 추천 질문/관리자 UI에 그대로 노출되는 경로 차단.
    private static final Pattern PII_PHONE = Pattern.compile(
            "\\b(?:01[016789]|02|0[3-6][1-5]|070|050\\d)[-\\s]?\\d{3,4}[-\\s]?\\d{4}\\b");

    @Transactional(readOnly = true)
    public PageResponse<FaqCandidateResponse> getCandidates(FaqCandidateStatus status, int page, int pageSize) {
        validatePageParams(page, pageSize);
        // 요구사항 4-(3)-1: "반려해도 상태만 REJECTED, 목록에서 제거되지 않는다" — 기본은 전체 상태 반환.
        // status 파라미터가 지정되면 해당 상태로 필터하며 ERD 인덱스 (status, ...)를 활용한다.
        PageRequest pageRequest = PageRequest.of(page, pageSize, SORT_LATEST);
        return PageResponse.from(
                status == null
                        ? faqCandidateRepository.findAll(pageRequest)
                        : faqCandidateRepository.findByStatus(status, pageRequest),
                FaqCandidateResponse::from
        );
    }

    /**
     * FAQ 후보 승인 → FAQ 등록.
     * <p>
     * 흐름 (모두 같은 트랜잭션):<br>
     * 1) 관리자가 지정한 카테고리 검증 (FaqTopic enum)<br>
     * 2) PESSIMISTIC_WRITE로 후보 row 락 — 다수 관리자 동시 승인 race 차단<br>
     * 3) 상태/답변 초안 가드 — 락 안에서 PENDING 아니거나 answer_draft 비어있으면 즉시 거절
     *    (Faq INSERT 전에 가드해서 race 시 faqs id sequence가 무의미하게 advance하지 않도록)<br>
     * 4) AI 답변 초안으로 FAQ row 생성<br>
     * 5) candidate.approve(topic, faqId, now) — 상태 전이 + faq_id 매핑<br>
     * <p>
     * FAQ 저장 실패(예: DB 제약 위반) 시 트랜잭션 전체 롤백되어 candidate 상태도 PENDING 유지.
     */
    @Transactional
    public FaqResponse approve(Long candidateId, FaqCandidateApproveRequest request) {
        FaqTopic chosenTopic = FaqTopic.parseOrThrow(request.topic());
        FaqCandidate candidate = findCandidateForUpdateOrThrow(candidateId);

        // Faq INSERT 전에 가드: race 시 sequence gap 방지 + 더 빠른 실패.
        if (candidate.getStatus() != FaqCandidateStatus.PENDING) {
            throw new BusinessException(ErrorCode.CANDIDATE_ALREADY_PROCESSED);
        }
        // answer_draft가 ERD상 nullable이지만 Faq.answer는 NOT NULL이라, 답변 없는 후보는 승인 불가.
        if (candidate.getAnswerDraft() == null || candidate.getAnswerDraft().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Faq faq = faqRepository.save(Faq.builder()
                .question(candidate.getQuestion())
                .answer(candidate.getAnswerDraft())
                .topic(chosenTopic)
                .build());

        candidate.approve(chosenTopic, faq.getId(), LocalDateTime.now());

        log.info("[FAQ] candidate {} approved → faq {} (topic={})", candidate.getId(), faq.getId(), chosenTopic.getValue());
        return FaqResponse.from(faq);
    }

    /**
     * FAQ 후보 반려. PENDING 상태에서만 가능. 반려 시각은 ERD에 별도 컬럼이 없어 updated_at이 자동 갱신된다.
     * 반려된 후보는 audit 용도로 보존된다 (요구사항 4-(3)-1).
     */
    @Transactional
    public void reject(Long candidateId) {
        FaqCandidate candidate = findCandidateForUpdateOrThrow(candidateId);
        if (candidate.getStatus() != FaqCandidateStatus.PENDING) {
            throw new BusinessException(ErrorCode.CANDIDATE_ALREADY_PROCESSED);
        }
        candidate.reject();
        log.info("[FAQ] candidate {} rejected", candidate.getId());
    }

    private FaqCandidate findCandidateForUpdateOrThrow(Long candidateId) {
        return faqCandidateRepository.findByIdForUpdate(candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
    }

    private void validatePageParams(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /**
     * AI 서버 {@code /api/faq/analyze} 응답을 받아 모든 후보를 batch insert 한다.
     * 스케줄러({@code FaqCandidateScheduler})에서 단일 호출된다.
     * <p>
     * 요구사항 4-(3)-1: "FAQ 후보는 중복 여부와 관계없이 생성된 모든 항목이 등록된다 — 별도의 중복 검사 없이
     * 항상 신규 후보로 저장된다" 정책을 그대로 구현한다. 응답 내 중복이든 기존 FAQ/후보와 동일하든 모두 신규 row.
     * <ul>
     *     <li>응답이 error 또는 candidates null/empty면 0 반환 후 정상 종료</li>
     *     <li>question이 null이거나 trim 후 빈 문자열이면 NOT NULL 제약 회피용으로 스킵</li>
     *     <li>category는 AI가 분류한 FaqTopic 키 — 파싱 실패(null/blank/unknown)는 null로 영속화하며,
     *         관리자가 승인 시 최종 카테고리를 다시 지정한다 (분류 오류 보정 여지 보장)</li>
     *     <li>draftAnswer/frequency는 AI 응답 그대로 — 누락 시 각각 null/0으로 영속화</li>
     * </ul>
     */
    @Transactional
    public int ingestFromAi(AiFaqAnalyzeResponse response) {
        if (response == null || !response.isSuccess()
                || response.candidates() == null || response.candidates().isEmpty()) {
            log.info("[FAQ] ingest skipped — empty/error response: status={}, message={}",
                    response == null ? "null" : response.status(),
                    response == null ? null : response.message());
            return 0;
        }

        // 요구사항 4-(3)-1: "중복 여부와 관계없이 생성된 모든 항목이 등록된다.
        // 별도의 중복 검사 없이 항상 신규 후보로 저장된다." — 응답 내 중복도, 기존 FAQ/후보와의 중복도 검사하지 않는다.
        List<AiFaqAnalyzeResponse.Candidate> incoming = response.candidates();
        List<FaqCandidate> toInsert = new ArrayList<>(incoming.size());
        for (AiFaqAnalyzeResponse.Candidate c : incoming) {
            if (c == null || c.question() == null) continue;
            String trimmed = c.question().trim();
            // question NOT NULL 제약 위반 회피용 빈 문자열 가드.
            if (trimmed.isEmpty()) continue;
            // PII 마스킹 → 길이 제한 순으로 정규화. 마스킹이 끝난 결과로 길이를 자른다.
            String sanitizedQuestion = truncate(redactPii(trimmed), MAX_QUESTION_LENGTH);
            // AI 응답의 frequency가 음수로 도착하면 entity 생성자가 0으로 silently 정규화하므로
            // upstream 회귀를 운영에서 인지할 수 있도록 warn 로깅만 남기고 정상 진행.
            // 로그 노출되는 question은 마스킹 후 문자열만 사용한다.
            if (c.frequency() != null && c.frequency() < 0) {
                log.warn("[FAQ] AI returned negative frequency — coerced to 0: question='{}', frequency={}",
                        sanitizedQuestion, c.frequency());
            }
            String sanitizedAnswer = c.draftAnswer() == null
                    ? null
                    : truncate(redactPii(c.draftAnswer()), MAX_ANSWER_DRAFT_LENGTH);
            // AI가 분류한 category를 FaqTopic으로 파싱. 알 수 없는 값/blank/null은 null로 두고
             // 관리자 승인 시 최종 카테고리를 지정하도록 한다 — parseOrThrow를 쓰면 단일 후보의 잘못된
             // 카테고리 값 때문에 batch 전체가 INVALID_INPUT으로 막혀 스케줄러가 사일런트로 0건 인입하는
             // 회귀가 생길 수 있어 일부러 catch 후 null로 폴백한다.
            FaqTopic category = parseAiCategoryOrNull(c.category(), sanitizedQuestion);
            toInsert.add(FaqCandidate.builder()
                    .question(sanitizedQuestion)
                    .answerDraft(sanitizedAnswer)
                    .category(category)
                    .frequency(c.frequency())
                    .build());
        }

        if (toInsert.isEmpty()) {
            log.info("[FAQ] ingest skipped — all candidates empty after trim");
            return 0;
        }

        faqCandidateRepository.saveAll(toInsert);
        log.info("[FAQ] ingested {} new candidates (received={})", toInsert.size(), incoming.size());
        return toInsert.size();
    }

    /**
     * AI가 클러스터링·요약한 후보 텍스트에 남아있을 수 있는 PII를 마스킹한다.
     * 학번/이메일/전화번호 패턴을 placeholder로 치환 — 후보 자체를 버리지 않고 그대로 노출되는 risk만 차단.
     * 명세상 AI 서버가 학습 단계에서 PII를 거른다고 가정하지만, 추천 질문/관리자 UI에 노출되기 직전
     * BE 최후방 가드를 둔다.
     */
    static String redactPii(String input) {
        if (input == null || input.isEmpty()) return input;
        String s = PII_STUDENT_ID.matcher(input).replaceAll("[학번]");
        s = PII_EMAIL.matcher(s).replaceAll("[이메일]");
        s = PII_PHONE.matcher(s).replaceAll("[전화번호]");
        return s;
    }

    private static String truncate(String input, int max) {
        if (input == null) return null;
        return input.length() <= max ? input : input.substring(0, max);
    }

    /**
     * AI가 분류한 category 키를 FaqTopic으로 파싱. 알 수 없는 값/blank/null은 null 반환 후 운영 추적용 warn 로깅.
     * FaqTopic.parseOrThrow는 BusinessException(INVALID_INPUT)을 던지므로 batch 인입 흐름을 끊지 않도록
     * IllegalArgumentException 경로(FaqTopic.from)를 직접 사용한다.
     */
    private FaqTopic parseAiCategoryOrNull(String aiCategory, String questionForLog) {
        if (aiCategory == null || aiCategory.isBlank()) {
            return null;
        }
        try {
            return FaqTopic.from(aiCategory);
        } catch (IllegalArgumentException e) {
            // AI 서버 카테고리 enum 변경/오타 회귀를 빨리 잡기 위한 운영 신호.
            log.warn("[FAQ] AI returned unknown category — persisted as null: category='{}', question='{}'",
                    aiCategory, questionForLog);
            return null;
        }
    }

    /**
     * 채팅 시작 전 추천 질문(/chat/recommended-questions) — PENDING 후보 중 frequency 내림차순 상위 N건 반환.
     * 명세 §13의 TOP 5가 FAQ 후보 생성과 동일 소스(faq_candidates)를 공유한다는 정의를 그대로 구현한다.
     * <p>
     * 별도 캐시/테이블 없이 후보 row를 단일 source of truth로 사용 — 인입 스케줄러 결과가 그대로 반영되며
     * 관리자가 승인/반려한 후보는 자연스럽게 다음 추천에서 빠진다.
     */
    @Transactional(readOnly = true)
    public List<FaqCandidate> getRecommendedQuestions(int limit) {
        if (limit < 1 || limit > MAX_RECOMMENDED_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return faqCandidateRepository.findTopRecommended(PageRequest.of(0, limit));
    }

}
