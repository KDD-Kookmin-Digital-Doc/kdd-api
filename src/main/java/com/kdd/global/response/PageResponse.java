package com.kdd.global.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 모든 페이지네이션 응답의 통일 포맷.
 * <p>
 * 명세는 모든 페이지네이션 API에 대해 {@code page} 기본값 0(0-based), {@code pageSize} 기본값 20을 규정하며,
 * Spring Data의 페이지 인덱싱과도 일치한다. 컨트롤러는 받은 page/pageSize를 변환 없이 그대로 서비스에 전달한다.
 */
public record PageResponse<T>(
        List<T> data,
        long totalCount,
        int page,
        int pageSize,
        int totalPages
) {
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages()
        );
    }
}
