package com.smart.kf.utils.pagination;

import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

public record PageResult<T>(
    List<T> records,
    long total,
    int page,
    int size,
    int totalPages,
    boolean hasNext,
    String nextCursor
) {
    public static <T> PageResult<T> fromPage(Page<T> page) {
        int currentPage = page.getNumber() + 1;
        int nextOffset = currentPage * page.getSize();
        return new PageResult<>(
            page.getContent(),
            page.getTotalElements(),
            currentPage,
            page.getSize(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasNext() ? PageQuery.encodeCursor(nextOffset) : null
        );
    }

    public static <T> PageResult<T> fromList(List<T> source, PageQuery query) {
        List<T> safeSource = source == null ? Collections.emptyList() : source;
        int start = Math.min(query.startOffset(), safeSource.size());
        int end = Math.min(start + query.size(), safeSource.size());
        List<T> records = safeSource.subList(start, end);
        boolean hasNext = end < safeSource.size();
        int totalPages = query.size() <= 0 ? 0 : (int) Math.ceil((double) safeSource.size() / query.size());
        int currentPage = query.cursor() == null || query.cursor().isBlank()
            ? query.page()
            : (start / query.size()) + 1;

        return new PageResult<>(
            records,
            safeSource.size(),
            currentPage,
            query.size(),
            totalPages,
            hasNext,
            hasNext ? PageQuery.encodeCursor(end) : null
        );
    }
}
