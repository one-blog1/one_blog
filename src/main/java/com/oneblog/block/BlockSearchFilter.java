package com.oneblog.block;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import com.oneblog.search.SearchFilter;

/**
 * 검색·피드·태그 목록에서 내가 차단한 회원의 글을 뺀다 (6.1, SOC-05).
 * 단, 내가 멤버인 블로그 안의 글은 그대로 보인다 (D-36).
 */
@Component
public class BlockSearchFilter implements SearchFilter {

    @Override
    public String postCondition(Long viewerId, MapSqlParameterSource args) {
        if (viewerId == null) {
            return null;
        }
        args.addValue("blockViewerId", viewerId);
        return """
                NOT (p.user_id IN (SELECT bk.blocked_user_id FROM blocks bk WHERE bk.user_id = :blockViewerId)
                     AND NOT EXISTS (SELECT 1 FROM blog_members bm WHERE bm.blog_id = p.blog_id
                                     AND bm.user_id = :blockViewerId AND bm.status = 'ACTIVE'))""";
    }
}
