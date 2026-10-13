package com.oneblog.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 영상 썸네일을 받을 주소 검사 (D-128). 남의 주소로 서버가 요청을 보내므로 허용한 사이트의 https만. */
class HttpVideoThumbnailFetcherTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
            "https://video-phinf.pstatic.net/20261012_1/thumb.jpg",
            "https://livecloud-thumb.akamaized.net/chzzk/clip/a.jpg",
            "https://videoimg.sooplive.co.kr/php/SnapshotLoad.php?rowKey=1",
            "https://videoimg.sooplive.com/a.jpg"
    })
    void 허용한_사이트의_https_사진_주소(String url) {
        assertThat(HttpVideoThumbnailFetcher.allowedImageUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://i.ytimg.com/vi/a/mqdefault.jpg",
            "https://evil.example.com/a.jpg",
            "https://i.ytimg.com.evil.example/a.jpg",
            "https://evilpstatic.net/a.jpg",
            "https://user@i.ytimg.com/a.jpg",
            "https://i.ytimg.com:8443/a.jpg",
            "https://127.0.0.1/a.jpg",
            "https://other.akamaized.net/a.jpg",
            "file:///etc/passwd",
            "not a url"
    })
    void 그_밖의_주소는_받지_않는다(String url) {
        assertThat(HttpVideoThumbnailFetcher.allowedImageUrl(url)).isFalse();
    }

    @Test
    void 주소_다듬기() {
        assertThat(HttpVideoThumbnailFetcher.normalize("//videoimg.sooplive.co.kr/a.jpg"))
                .isEqualTo("https://videoimg.sooplive.co.kr/a.jpg");
        assertThat(HttpVideoThumbnailFetcher.normalize("http://video-phinf.pstatic.net/a_{type}.jpg"))
                .isEqualTo("https://video-phinf.pstatic.net/a_480.jpg");
        assertThat(HttpVideoThumbnailFetcher.normalize(" ")).isNull();
        assertThat(HttpVideoThumbnailFetcher.normalize(null)).isNull();
    }

    @Test
    void 꺼져_있거나_영상_구분이_이상하면_요청하지_않는다() {
        assertThat(new HttpVideoThumbnailFetcher(false).fetch("youtube:dQw4w9WgXcQ")).isEmpty();
        HttpVideoThumbnailFetcher on = new HttpVideoThumbnailFetcher(true);
        assertThat(on.fetch(null)).isEmpty();
        assertThat(on.fetch("youtube")).isEmpty();
        assertThat(on.fetch("youtube:../../etc")).isEmpty();
        assertThat(on.fetch("unknown:abc")).isEmpty();
    }
}
