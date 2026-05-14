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
     * 1) 관리자가 지정한 카테고리를 검증한다 (FaqTopic enum에 존재해야 함).<br>
     * 2) PENDING 상태인 후보만 승인 가능 (도메인 invariant는 엔티티 approve()에서 검증).<br>
     * 3) FAQ row를 생성하고 후보 상태를 APPROVED로 전이한다 (같은 트랜잭션).<br>
     * <p>
     * AI가 만든 draftAnswer를 그대로 FAQ.answer로 등록한다 — 추가 AI 호출 없음. 관리자가 답변을
     * 다듬어야 하면 등록 후 일반 FAQ 수정 API를 사용한다.
     */
    @Transactional
    public FaqResponse approve(Long candidateId, FaqCandidateApproveRequest request) {
        FaqTopic chosenTopic = parseTopic(request.topic());
        // 다수 관리자가 동시에 같은 후보를 승인할 때 PENDING 검사 → 상태 전이 사이의 race를
        // 막기 위해 FOR UPDATE로 row를 직렬화한다. 후행 트랜잭션은 락 해제 후 APPROVED 상태를
        // 읽고 도메인 invariant에서 CANDIDATE_ALREADY_PROCESSED로 거절된다.
        FaqCandidate candidate = findCandidateForUpdateOrThrow(candidateId);

        try {
            candidate.approve(chosenTopic, LocalDateTime.now());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.CANDIDATE_ALREADY_PROCESSED);
        } catch (IllegalArgumentException e) {
            // 도메인 가드(예: topic == null) 발화. parseTopic에서 이미 막혀 도달 불가하지만,
            // 만약 발화되면 500이 아니라 400으로 응답하도록 INVALID_INPUT 매핑.
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Faq faq = Faq.builder()
                .question(candidate.getQuestion())
                .answer(candidate.getDraftAnswer())
                .topic(chosenTopic)
                .build();
        return FaqResponse.from(faqRepository.save(faq));
    }

    /**
     * FAQ 후보 반려. PENDING 상태에서만 가능. 반려된 후보는 audit 용도로 보존된다 (요구사항 4-(3)-1).
     */
    @Transactional
    public void reject(Long candidateId) {
        // approve와 동일한 사유로 FOR UPDATE — 승인/반려가 엇갈려도 첫 트랜잭션이 종료된 후
        // 후행은 변경된 상태를 읽고 CANDIDATE_ALREADY_PROCESSED로 거절된다.
        FaqCandidate candidate = findCandidateForUpdateOrThrow(candidateId);
        try {
            candidate.reject(LocalDateTime.now());
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
