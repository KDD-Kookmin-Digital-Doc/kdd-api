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

    @Transactional(readOnly = true)
    public List<FaqTopicResponse> getTopics() {
        return Arrays.stream(FaqTopic.values())
                .map(FaqTopicResponse::from)
                .toList();
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
        Faq faq = findFaqOrThrow(faqId);
        faq.update(request.question(), request.answer(), request.topic());
        return FaqResponse.from(faq);
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
        if (page < 0 || pageSize < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
