package com.kdd.faq.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqCandidateService {

    private final FaqCandidateRepository faqCandidateRepository;
    private final FaqRepository faqRepository;

    // FAQ/Document 컨벤션과 동일 — pageSize 상한으로 인증된 클라이언트의 비정상 페이로드 차단 (#58 패턴).
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort SORT_LATEST = Sort.by("createdAt", "id").descending();

    @Transactional(readOnly = true)
    public PageResponse<FaqCandidateResponse> getCandidates(FaqCandidateStatus status, int page, int pageSize) {
        validatePageParams(page, pageSize);
        // 요구사항 4-(3)-1: "관리자가 반려해도 상태만 REJECTED로 변하고 목록에서 제거되지 않는다" — 기본은 전체 상태 반환.
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
}
