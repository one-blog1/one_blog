package com.oneblog.common.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 상태 확인 (D-117, D-120). 로드밸런서·배포 도구·CI가 "서버가 요청을 받을 수 있는지" 묻는 주소.
 * DB에 SELECT 1을 보내 되면 200 {"status":"UP","version":"0.1.0"}, 안 되면 503 {"status":"DOWN",...}.
 * version은 지금 떠 있는 서비스 버전(build.gradle.kts의 version). 로그인 없이 부를 수 있고 그 밖의 내부 정보는 담지 않는다.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbc;
    private final String version;

    public HealthController(JdbcTemplate jdbc, ObjectProvider<BuildProperties> buildProperties) {
        this.jdbc = jdbc;
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build == null || build.getVersion() == null ? "unknown" : build.getVersion();
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "version", version));
        } catch (DataAccessException e) {
            log.warn("상태 확인: DB에 연결하지 못했습니다 ({})", e.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "version", version));
        }
    }
}
