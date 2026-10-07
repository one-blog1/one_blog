package com.oneblog.notification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;

/**
 * 알림함 (SOC-04, 3.6): 탭별 목록, 안 읽은 수(30초 폴링, D-72), 읽음 처리, 전체 삭제, 알림 설정.
 * 모든 쿼리는 user_id = 나 조건으로 본인 알림만 다룬다. 보관 기간(30일 또는 7일)이 지난 알림은 보이지 않고
 * 04:00 배치(012)가 지운다 (4.5).
 */
@Service
public class NotificationQueryService {

    private static final List<String> TABS = List.of("COMMENT", "LIKE", "FOLLOW", "BLOG", "OPERATION");
    private static final String MINE = """
            FROM notifications n JOIN users u ON u.id = n.user_id
            WHERE n.user_id = :userId
              AND n.created_at >= DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL u.notification_retention_days DAY)""";

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository userRepository;

    public NotificationQueryService(NamedParameterJdbcTemplate jdbc, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    public record Item(Long id, String type, String tab, String message, String linkUrl, boolean read,
            OffsetDateTime createdAt) {
    }

    public record Page(List<Item> items, String tab, long unreadCount, int page, int size, long totalItems,
            int totalPages) {
    }

    public record SettingItem(String type, String label, String tab, boolean enabled) {
    }

    public record Settings(int retentionDays, List<SettingItem> items) {
    }

    public record SettingsRequest(Integer retentionDays, Map<String, Boolean> settings) {
    }

    @Transactional(readOnly = true)
    public Page list(AuthenticatedUser principal, String rawTab, PageParams params) {
        Long userId = member(principal);
        String tab = rawTab != null && TABS.contains(rawTab) ? rawTab : "ALL";
        MapSqlParameterSource args = new MapSqlParameterSource("userId", userId);
        String where = MINE;
        if (!"ALL".equals(tab)) {
            args.addValue("tab", tab);
            where += " AND n.tab = :tab";
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + where, args, Long.class);
        long totalItems = total == null ? 0 : total;
        int totalPages = (int) Math.max(1, (totalItems + params.size() - 1) / params.size());
        if (params.page() > totalPages) {
            params = params.firstPage();
        }
        args.addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        List<Item> items = jdbc.query("SELECT n.id, n.type, n.tab, n.message, n.link_url, n.is_read, n.created_at "
                + where + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, i) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getBoolean(6), Times.toOffset(rs.getTimestamp(7).toLocalDateTime())));
        return new Page(items, tab, unreadCount(userId), params.page(), params.size(), totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthenticatedUser principal) {
        return unreadCount(member(principal));
    }

    private long unreadCount(Long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + MINE + " AND n.is_read = 0",
                new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(AuthenticatedUser principal, Long id) {
        int updated = jdbc.update("""
                UPDATE notifications SET is_read = 1, read_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND user_id = :userId AND is_read = 0
                """, new MapSqlParameterSource().addValue("id", id).addValue("userId", member(principal)));
        if (updated == 0) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id = :id AND user_id = :userId",
                    new MapSqlParameterSource().addValue("id", id).addValue("userId", principal.id()), Integer.class);
            if (exists == null || exists == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");
            }
        }
    }

    @Transactional
    public void markAllRead(AuthenticatedUser principal) {
        jdbc.update("""
                UPDATE notifications SET is_read = 1, read_at = CURRENT_TIMESTAMP(6)
                WHERE user_id = :userId AND is_read = 0
                """, new MapSqlParameterSource("userId", member(principal)));
    }

    /** 전체 삭제 (SOC-04, 4.5 "직접 일괄 삭제"). */
    @Transactional
    public void deleteAll(AuthenticatedUser principal) {
        jdbc.update("DELETE FROM notifications WHERE user_id = :userId",
                new MapSqlParameterSource("userId", member(principal)));
    }

    @Transactional(readOnly = true)
    public Settings settings(AuthenticatedUser principal) {
        Long userId = member(principal);
        User user = userRepository.findById(userId).orElseThrow();
        Map<String, Boolean> saved = new HashMap<>();
        jdbc.query("SELECT type, enabled FROM notification_settings WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), rs -> {
                    saved.put(rs.getString(1), rs.getBoolean(2));
                });
        List<SettingItem> items = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            if (type.optional()) {
                items.add(new SettingItem(type.name(), type.label(), type.tab(), saved.getOrDefault(type.name(), true)));
            }
        }
        return new Settings(user.getNotificationRetentionDays(), items);
    }

    /** 끌 수 있는 알림만 바꾼다. 끌 수 없는 종류나 모르는 종류는 거부한다 (3.6). */
    @Transactional
    public Settings updateSettings(AuthenticatedUser principal, SettingsRequest request) {
        Long userId = member(principal);
        if (request.retentionDays() != null) {
            if (request.retentionDays() != 7 && request.retentionDays() != 30) {
                throw new ValidationFailedException(List.of(
                        new ErrorResponse.FieldError("retentionDays", "알림 보관 기간은 7일이나 30일만 고를 수 있습니다.")));
            }
            User user = userRepository.findById(userId).orElseThrow();
            user.changeNotificationRetentionDays(request.retentionDays());
            userRepository.saveAndFlush(user);
        }
        if (request.settings() != null) {
            for (Map.Entry<String, Boolean> entry : request.settings().entrySet()) {
                NotificationType type = Arrays.stream(NotificationType.values())
                        .filter(t -> t.name().equals(entry.getKey())).findFirst().orElse(null);
                if (type == null || !type.optional() || entry.getValue() == null) {
                    throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("settings",
                            "끌 수 없는 알림입니다: " + entry.getKey())));
                }
                jdbc.update("""
                        INSERT INTO notification_settings (user_id, type, enabled) VALUES (:userId, :type, :enabled)
                        ON DUPLICATE KEY UPDATE enabled = :enabled
                        """, new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name())
                        .addValue("enabled", entry.getValue()));
            }
        }
        return settings(principal);
    }

    private static Long member(AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 알림을 쓰지 않습니다.");
        }
        return principal.id();
    }
}
