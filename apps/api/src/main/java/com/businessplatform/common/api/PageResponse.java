package com.businessplatform.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

/** One page of a list. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements());
    }
}
