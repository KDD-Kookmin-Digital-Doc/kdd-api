package com.kdd.faq.service;

import com.kdd.faq.dto.FaqCandidateApproveRequest;
import com.kdd.faq.dto.FaqCandidateResponse;
import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.entity.Faq;
import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqTopic;
import com.kdd.faq.repository.FaqCandidateRepository;
import com.kdd.faq.repository.FaqRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FaqCandidateService {

    private final FaqCandidateRepository faqCandidateRepository;
    private final FaqRepository faqRepository;

    // FAQ/Document 컨벤션과 동일 — pageSize 상한으로 인증된 클라이언트의 비정상 페이로드 차단 (#58 패턴).
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort SORT_LATEST = Sort.by("createdAt", "id").descending();

    @Transactional(readOnly = true)
    public PageResponse<FaqCandidateResponse> getCandidates(int page, int pageSize) {
        validatePageParams(page, pageSize);
        return PageResponse.from(
                faqCandidateRepository.findAll(PageRequest.of(page, pageSize, SORT_LATEST)),
                FaqCandidateResponse::from
        );
    }

    /**
     * FAQ 후보 승인 → FAQ 등록.
     * <p>
     * 흐름 (모두 같은 트랜잭션):<br>
     * 1) 관리자가 지정한 카테고리 검증 (FaqTopic enum)<br>
     * 2) PESSIMISTIC_WRITE로 후보 row 락 — 다수 관리자 동시 승인 race 차단<br>
     * 3) AI 답변 초안(answer_draft)으로 FAQ row 생성 — Faq.answer가 NOT NULL이라 null이면 거절<br>
     * 4) candidate.approve(topic, faqId, now) — 상태 전이 + faq_id 매핑<br>
     * <p>
     * FAQ 저장 실패(예: DB 제약 위반) 시 트랜잭션 전체 롤백되어 candidate 상태도 PENDING 유지.
     */
    @Transactional
    public FaqResponse approve(Long candidateId, FaqCandidateApproveRequest request) {
        FaqTopic chosenTopic = parseTopic(request.topic());
        FaqCandidate candidate = findCandidateForUpdateOrThrow(candidateId);

        // answer_draft가 ERD상 nullable이지만 Faq.answer는 NOT NULL이라, 답변 없는 후보는 승인 불가.
        // 정상 흐름(AI가 draft 생성)에서는 발생하지 않으나 방어적으로 막는다.
        if (candidate.getAnswerDraft() == null || candidate.getAnswerDraft().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Faq faq = faqRepository.save(Faq.builder()
                .question(candidate.getQuestion())
                .answer(candidate.getAnswerDraft())
                .topic(chosenTopic)
                .build());

        try {
            candidate.approve(chosenTopic, faq.getId(), LocalDateTime.now());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.CANDIDATE_ALREADY_PROCESSED);
        } catch (IllegalArgumentException e) {
            // 도메인 가드 — parseTopic이 이미 막아서 도달 불가하지만 발화 시 500 대신 400으로 응답.
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return FaqResponse.from(faq);
    }

    /**
     * FAQ 후보 반려. PENDING 상태에서만 가능. 반려 시각은 ERD에 별도 컬럼이 없어 updated_at이 자동 갱신된다.
     * 반려된 후보는 audit 용도로 보존된다 (요구사항 4-(3)-1).
     */
    @Transactional
    public void reject(Long candidateId) {
        FaqCandidate candidate = findCandidateForUpdateOrThrow(candidateId);
        try {
            candidate.reject();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.CANDIDATE_ALREADY_PROCESSED);
        }
    }

    private FaqCandidate findCandidateForUpdateOrThrow(Long candidateId) {
        return faqCandidateRepository.findByIdForUpdate(candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
    }

    private FaqTopic parseTopic(String value) {
        try {
            return FaqTopic.from(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePageParams(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
