package com.example.networktraffic.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pagination {
    public static final int MAX_PAGE_SIZE = 100;

    private Pagination() {}

    public static Pageable pageable(int page, int size, String sortProperty) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortProperty));
    }
}
