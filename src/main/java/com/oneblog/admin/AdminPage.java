package com.oneblog.admin;

import java.util.List;

/** 관리자 목록 공통 (번호 페이지). */
public record AdminPage<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> AdminPage<T> of(List<T> items, int page, int size, long total) {
        int pages = (int) Math.max(1, (total + size - 1) / size);
        return new AdminPage<>(items, page, size, total, pages);
    }
}
