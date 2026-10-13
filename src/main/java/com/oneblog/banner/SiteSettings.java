package com.oneblog.banner;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 관리자가 고치는 사이트 설정 한 줄씩 (Crowfoot ERD site_settings, D-132). 행이 없으면 부르는 쪽의 기본값을 쓴다. */
@Component
public class SiteSettings {

    private final JdbcTemplate jdbc;

    public SiteSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> get(String key) {
        List<String> values = jdbc.queryForList("SELECT setting_value FROM site_settings WHERE setting_key = ?",
                String.class, key);
        return values.stream().findFirst();
    }

    public int getInt(String key, int fallback) {
        return get(key).map(v -> {
            try {
                return Integer.parseInt(v.strip());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    public void put(String key, String value) {
        jdbc.update("""
                INSERT INTO site_settings (setting_key, setting_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """, key, value);
    }
}
