package com.oneblog.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 저장과 조회 (research R5). SQL은 모두 바인딩 파라미터로만 만든다 (constitution III).
 * 같은 새 태그를 두 요청이 동시에 만들어도 uk_tags_name 덕분에 하나만 생긴다 (INSERT IGNORE).
 */
@Service
public class TagService {

    private final NamedParameterJdbcTemplate jdbc;

    public TagService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 정리된 태그 이름들을 블로그에 단다. 호출하는 쪽 트랜잭션 안에서 실행한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachToBlog(Long blogId, List<String> tagNames) {
        if (tagNames.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] inserts = tagNames.stream()
                .map(name -> new MapSqlParameterSource("name", name))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT IGNORE INTO tags (name) VALUES (:name)", inserts);

        List<Long> tagIds = jdbc.queryForList("SELECT id FROM tags WHERE name IN (:names)",
                new MapSqlParameterSource("names", tagNames), Long.class);
        MapSqlParameterSource[] links = tagIds.stream()
                .map(tagId -> new MapSqlParameterSource().addValue("blogId", blogId).addValue("tagId", tagId))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO blog_tags (blog_id, tag_id) VALUES (:blogId, :tagId)", links);
    }

    /** 블로그별 태그 이름 (태그를 만든 순서). 한 번의 조회로 여러 블로그를 읽는다 (research R8). */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> findBlogTags(Collection<Long> blogIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (blogIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT bt.blog_id, t.name FROM blog_tags bt JOIN tags t ON t.id = bt.tag_id
                WHERE bt.blog_id IN (:blogIds) ORDER BY bt.blog_id, t.id
                """, new MapSqlParameterSource("blogIds", blogIds), rs -> {
                    result.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>()).add(rs.getString(2));
                });
        return result;
    }

    public List<String> findBlogTags(Long blogId) {
        return findBlogTags(List.of(blogId)).getOrDefault(blogId, List.of());
    }

    /**
     * 글의 태그를 통째로 바꾼다 (BRD-04). 정리된 태그 이름만 받는다 (TagPolicy.normalizeAll).
     * 호출하는 쪽 트랜잭션 안에서 실행한다. 글 행이 먼저 저장(flush)돼 있어야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replacePostTags(Long postId, List<String> tagNames) {
        jdbc.update("DELETE FROM post_tags WHERE post_id = :postId", new MapSqlParameterSource("postId", postId));
        if (tagNames.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] inserts = tagNames.stream()
                .map(name -> new MapSqlParameterSource("name", name))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT IGNORE INTO tags (name) VALUES (:name)", inserts);

        Map<String, Long> ids = new LinkedHashMap<>();
        jdbc.query("SELECT id, name FROM tags WHERE name IN (:names)", new MapSqlParameterSource("names", tagNames),
                rs -> {
                    ids.put(rs.getString(2), rs.getLong(1));
                });
        // 입력 순서대로 넣어 post_tags.id 순서가 사용자가 단 순서가 되게 한다
        MapSqlParameterSource[] links = tagNames.stream()
                .filter(ids::containsKey)
                .map(name -> new MapSqlParameterSource().addValue("postId", postId).addValue("tagId", ids.get(name)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO post_tags (post_id, tag_id) VALUES (:postId, :tagId)", links);
    }

    /** 글별 태그 이름 (단 순서). 한 번의 조회로 여러 글을 읽는다. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> findPostTags(Collection<Long> postIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT pt.post_id, t.name FROM post_tags pt JOIN tags t ON t.id = pt.tag_id
                WHERE pt.post_id IN (:postIds) ORDER BY pt.post_id, pt.id
                """, new MapSqlParameterSource("postIds", postIds), rs -> {
                    result.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>()).add(rs.getString(2));
                });
        return result;
    }

    /** 태그 ID. 없는 태그면 null. */
    @Transactional(readOnly = true)
    public Long findTagId(String name) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM tags WHERE name = :name",
                new MapSqlParameterSource("name", name), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
