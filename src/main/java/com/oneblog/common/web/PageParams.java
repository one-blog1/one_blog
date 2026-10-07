package com.oneblog.common.web;

import java.util.List;

/**
 * 번호 페이지 값 (D-06, D-76, 6.6). page는 1부터, size는 10·20·30 중 하나.
 * 규칙 밖의 값은 기본값(1페이지, 10개)으로 바꾼다.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 10;
    private static final List<Integer> SIZES = List.of(10, 20, 30);

    public static PageParams of(String rawPage, String rawSize) {
        Integer page = parse(rawPage);
        Integer size = parse(rawSize);
        return new PageParams(page == null || page < 1 ? 1 : page,
                size != null && SIZES.contains(size) ? size : DEFAULT_SIZE);
    }

    public int zeroBasedPage() {
        return page - 1;
    }

    public PageParams firstPage() {
        return new PageParams(1, size);
    }

    private static Integer parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
