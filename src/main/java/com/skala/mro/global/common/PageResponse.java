package com.skala.mro.global.common;

import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageResponse<T>(List<T> items, long total) {

    public static final int MAX_SIZE = 100;

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements());
    }

    /** API의 page는 1부터 시작합니다. */
    public static Pageable pageable(int page, int size, Sort sort) {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "page는 1 이상, size는 1~%d이어야 합니다.".formatted(MAX_SIZE));
        }
        return PageRequest.of(page - 1, size, sort);
    }
}
