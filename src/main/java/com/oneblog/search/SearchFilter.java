package com.oneblog.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * 검색·피드의 글 목록에서 뺄 조건 (예: 내가 차단한 회원의 글, 013).
 * 돌려주는 SQL 조각은 코드에 적힌 고정 문자열이어야 하고(별칭 p = posts, b = blogs), 값은 args에 바인딩으로 넣는다.
 */
public interface SearchFilter {

    /** 조건이 없으면 null. viewerId는 비회원이면 null. */
    String postCondition(Long viewerId, MapSqlParameterSource args);
}
