package com.oneblog.post;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 영상 썸네일 기능(D-128) 이전에 쓴 글도 썸네일이 붙도록, 서버가 뜬 뒤 한 번 훑는다.
 * 영상 사이트 주소가 들어 있고 아직 영상 썸네일이 없는 블로그 글만, 최근 글부터 최대 500개.
 * 썸네일이 필요 없는 글(사진이 먼저 나오는 글 등)은 VideoThumbnailService가 아무것도 하지 않는다.
 * 요청을 기다리게 하지 않도록 따로 돈다. 영상 썸네일이 꺼져 있으면(테스트 등) 하지 않는다.
 */
@Component
public class VideoThumbnailBackfill {

    static final int LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(VideoThumbnailBackfill.class);

    private final JdbcTemplate jdbc;
    private final VideoThumbnailService thumbnailService;
    private final boolean enabled;

    public VideoThumbnailBackfill(JdbcTemplate jdbc, VideoThumbnailService thumbnailService,
            @Value("${app.video-thumbnail.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.thumbnailService = thumbnailService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            Thread.ofVirtual().name("video-thumbnail-backfill").start(this::run);
        }
    }

    void run() {
        try {
            List<Long> ids = candidates();
            for (Long id : ids) {
                thumbnailService.refresh(id);
            }
            if (!ids.isEmpty()) {
                log.info("예전 글 {}개의 영상 썸네일을 확인했습니다.", ids.size());
            }
        } catch (RuntimeException e) {
            log.warn("예전 글의 영상 썸네일을 확인하지 못했습니다.", e);
        }
    }

    List<Long> candidates() {
        return jdbc.queryForList("""
                SELECT p.id FROM posts p
                WHERE p.deleted_at IS NULL AND p.post_type IN ('BLOG', 'BLOG_NOTICE')
                  AND (p.content LIKE '%youtu%' OR p.content LIKE '%chzzk.naver.com%'
                       OR p.content LIKE '%sooplive%' OR p.content LIKE '%afreecatv%' OR p.content LIKE '%afree.ca%')
                  AND NOT EXISTS (SELECT 1 FROM files f
                                  WHERE f.post_id = p.id AND f.purpose = 'VIDEO_THUMB' AND f.deleted_at IS NULL)
                ORDER BY p.id DESC
                LIMIT ?
                """, Long.class, LIMIT);
    }
}
