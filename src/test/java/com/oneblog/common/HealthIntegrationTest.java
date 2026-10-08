package com.oneblog.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import com.oneblog.IntegrationTestSupport;

/** 배포 기본 동작 (D-117): 상태 확인 주소와 서버·DB 시간대. */
class HealthIntegrationTest extends IntegrationTestSupport {

    @Test
    void 상태_확인은_로그인_없이_UP() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void 서버와_DB_세션은_한국_시간() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+09:00");
    }
}
