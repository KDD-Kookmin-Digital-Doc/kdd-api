package com.kdd.global.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Getter
@Builder
public class PageResponse<T> {

    private List<T> data;
    private long totalCount;
    private int page;
    private int pageSize;
    private int totalPages;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .data(page.getContent().stream().map(mapper).toList())
                .totalCount(page.getTotalElements())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }
}
