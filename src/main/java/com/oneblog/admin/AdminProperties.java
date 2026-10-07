package com.oneblog.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 처음 관리자 계정 (D-94: 운영자가 초기 데이터로 만든다). 환경변수로만 넣고 저장소에 올리지 않는다 (constitution III).
 * 둘 다 있고 같은 아이디가 없을 때 서버가 시작하면서 한 번 만든다.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String initialLoginId, String initialPassword) {
}
