package com.kdd.faq.service;

import com.kdd.faq.dto.FaqCreateRequest;
import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.dto.FaqTopicResponse;
import com.kdd.faq.dto.FaqUpdateRequest;
import com.kdd.faq.entity.Faq;
import com.kdd.faq.entity.FaqTopic;
import com.kdd.faq.repository.FaqRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;

    private static final Sort SORT_LATEST = Sort.by("createdAt", "id").descending();
    // 인증된 클라이언트가 pageSize=Integer.MAX_VALUE 등 비정상 값으로 전체 테이블을 읽어가는 것을 차단
    private static final int MAX_PAGE_SIZE = 100;

    // 컴파일 타임 상수인 enum 9개를 매 호출마다 stream으로 빌드하지 않도록 1회만 캐시
    private static final List<FaqTopicResponse> TOPICS_CACHE = Arrays.stream(FaqTopic.values())
            .map(FaqTopicResponse::from)
            .toList();

    @Transactional(readOnly = true)
    public PageResponse<FaqResponse> getFaqs(FaqTopic topic, int page, int pageSize) {
        validatePageParams(page, pageSize);
        PageRequest pageable = PageRequest.of(page, pageSize, SORT_LATEST);

        Page<Faq> result = (topic == null)
                ? faqRepository.findAll(pageable)
                : faqRepository.findByTopic(topic, pageable);

        return PageResponse.from(result, FaqResponse::from);
    }

    @Transactional(readOnly = true)
    public FaqResponse getFaq(Long faqId) {
        return FaqResponse.from(findFaqOrThrow(faqId));
    }

    public List<FaqTopicResponse> getTopics() {
        return TOPICS_CACHE;
    }

    @Transactional
    public FaqResponse create(FaqCreateRequest request) {
        Faq faq = Faq.builder()
                .question(request.question())
                .answer(request.answer())
                .topic(request.topic())
                .build();
        return FaqResponse.from(faqRepository.save(faq));
    }

    @Transactional
    public FaqResponse update(Long faqId, FaqUpdateRequest request) {
        // PATCH 시맨틱: 필드 미전송(null)은 허용하되 빈 문자열은 명시적으로 거부한다.
        // (전송 안 함 = 변경 없음, 빈 값 = 잘못된 입력)
        rejectIfBlank(request.question());
        rejectIfBlank(request.answer());
        Faq faq = findFaqOrThrow(faqId);
        faq.update(request.question(), request.answer(), request.topic());
        return FaqResponse.from(faq);
    }

    private void rejectIfBlank(String value) {
        if (value != null && value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    @Transactional
    public void delete(Long faqId) {
        Faq faq = findFaqOrThrow(faqId);
        faqRepository.delete(faq);
    }

    private Faq findFaqOrThrow(Long faqId) {
        return faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
    }

    private void validatePageParams(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
