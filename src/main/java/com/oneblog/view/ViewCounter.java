package com.oneblog.view;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 조회수 (BRD-11, 6.7, D-77). 같은 사람이 같은 날(한국 날짜) 같은 글을 보면 한 번만 센다.
 * - 사람: 회원은 회원 ID, 비회원은 IP + 브라우저 정보. 둘 다 해시로만 남긴다
 * - SNS 미리보기 수집기·검색 로봇처럼 이름에 bot·crawler 등이 들어간 요청은 세지 않는다
 * 조회 기록은 오늘·어제 것만 의미가 있어 04:00 배치가 이틀 지난 것을 지운다.
 */
@Service
public class ViewCounter {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|facebookexternalhit|kakaotalk-scrap|preview|headless|curl|wget|python-requests",
            Pattern.CASE_INSENSITIVE);

    private final NamedParameterJdbcTemplate jdbc;

    public ViewCounter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 세었으면 true (조회수 +1). */
    @Transactional
    public boolean record(Long postId, Long userId, String ip, String userAgent) {
        if (isBot(userAgent)) {
            return false;
        }
        String key = userId != null ? sha256("user:" + userId)
                : sha256("guest:" + (ip == null ? "" : ip) + "|" + (userAgent == null ? "" : userAgent));
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("postId", postId).addValue("key", key)
                .addValue("date", LocalDate.now(SEOUL));
        int inserted = jdbc.update("""
                INSERT IGNORE INTO post_views (post_id, viewer_key, view_date) VALUES (:postId, :key, :date)
                """, args);
        if (inserted == 0) {
            return false;
        }
        jdbc.update("UPDATE posts SET view_count = view_count + 1 WHERE id = :postId", args);
        return true;
    }

    static boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent.toLowerCase(Locale.ROOT)).find();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
