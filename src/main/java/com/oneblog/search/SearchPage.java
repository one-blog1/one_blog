package com.oneblog.search;

import java.util.List;

/** 검색·피드 결과의 번호 페이지 (D-76). 결과가 없으면 items가 비고 화면이 "검색 결과가 없습니다"를 보여준다. */
public record SearchPage<T>(
        String q,
        String type,
        String target,
        String sort,
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
