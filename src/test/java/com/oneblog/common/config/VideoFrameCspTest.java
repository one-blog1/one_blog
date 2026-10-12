package com.oneblog.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.oneblog.common.text.VideoEmbeds;

/** 글 안 영상 플레이어 사이트(021)가 보안 헤더 frame-src에 모두 있는지. 한쪽만 고치면 영상이 빈칸으로 보인다. */
class VideoFrameCspTest {

    @Test
    void 플레이어_사이트는_모두_frame_src에_있다() {
        String frameSrc = Arrays.stream(SecurityConfig.CONTENT_SECURITY_POLICY.split("; "))
                .filter(d -> d.startsWith("frame-src "))
                .findFirst().orElseThrow();
        assertThat(frameSrc.split(" ")).contains(VideoEmbeds.FRAME_ORIGINS.toArray(String[]::new));
    }
}
