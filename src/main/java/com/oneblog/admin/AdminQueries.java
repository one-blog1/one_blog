package com.oneblog.admin;

/** 관리자 검색어를 LIKE 패턴으로 (%, _, \ 는 글자 그대로 찾도록 이스케이프). 값은 항상 바인딩 파라미터로 넣는다. */
final class AdminQueries {

    private AdminQueries() {
    }

    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
