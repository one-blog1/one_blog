package com.oneblog.notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 알림 보내기 (SOC-04, 3.6). 알림을 만든 기능의 트랜잭션 안에서 행만 넣는다(화면은 30초마다 가져간다, D-72).
 * - 받는 사람이 끈 알림(끌 수 있는 종류만)은 만들지 않는다. 이미 받은 알림은 그대로
 * - dedupe_key가 같으면 두 번 만들지 않는다 (uk_notifications_user_id_dedupe_key, INSERT IGNORE)
 * - 탈퇴한 회원·관리자에게는 보내지 않는다
 * 알림이 실패해도 원래 기능(댓글·좋아요 등)은 성공해야 하므로 예외를 밖으로 던지지 않는다.
 * 이 클래스에는 @Transactional을 붙이지 않는다: 실패가 부르는 쪽 트랜잭션을 롤백 전용으로 만들지 않게.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    static final int MESSAGE_MAX = 300;

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void send(Long userId, NotificationType type, String message, String linkUrl, String dedupeKey) {
        if (userId == null) {
            return;
        }
        sendAll(List.of(userId), type, message, linkUrl, dedupeKey);
    }

    /** 여러 사람에게 같은 알림. 받는 사람 목록은 중복을 뺀다. */
    public void sendAll(Collection<Long> userIds, NotificationType type, String message, String linkUrl,
            String dedupeKey) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<MapSqlParameterSource> rows = new ArrayList<>();
            for (Long id : ids) {
                rows.add(new MapSqlParameterSource()
                        .addValue("userId", id)
                        .addValue("type", type.name())
                        .addValue("tab", type.tab())
                        .addValue("message", shorten(message))
                        .addValue("link", linkUrl)
                        .addValue("dedupe", dedupeKey));
            }
            jdbc.batchUpdate("""
                    INSERT IGNORE INTO notifications (user_id, type, tab, message, link_url, dedupe_key)
                    SELECT u.id, :type, :tab, :message, :link, :dedupe FROM users u
                    WHERE u.id = :userId AND u.role = 'USER' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                      AND NOT EXISTS (SELECT 1 FROM notification_settings s
                                      WHERE s.user_id = u.id AND s.type = :type AND s.enabled = 0)
                    """, rows.toArray(MapSqlParameterSource[]::new));
        } catch (RuntimeException e) {
            log.warn("알림을 만들지 못했습니다: type={}, users={}", type, ids.size(), e);
        }
    }

    /** 활성 회원 모두에게 (메인 공지, 3.6). 한 문장으로 넣는다. */
    public void sendToAllMembers(NotificationType type, String message, String linkUrl, String dedupeKey) {
        try {
            jdbc.update("""
                    INSERT IGNORE INTO notifications (user_id, type, tab, message, link_url, dedupe_key)
                    SELECT u.id, :type, :tab, :message, :link, :dedupe FROM users u
                    WHERE u.role = 'USER' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                      AND NOT EXISTS (SELECT 1 FROM notification_settings s
                                      WHERE s.user_id = u.id AND s.type = :type AND s.enabled = 0)
                    """, new MapSqlParameterSource()
                    .addValue("type", type.name())
                    .addValue("tab", type.tab())
                    .addValue("message", shorten(message))
                    .addValue("link", linkUrl)
                    .addValue("dedupe", dedupeKey));
        } catch (RuntimeException e) {
            log.warn("전체 알림을 만들지 못했습니다: type={}", type, e);
        }
    }

    static String shorten(String message) {
        if (message == null) {
            return "";
        }
        return message.codePointCount(0, message.length()) <= MESSAGE_MAX ? message
                : message.substring(0, message.offsetByCodePoints(0, MESSAGE_MAX - 1)) + "…";
    }
}
