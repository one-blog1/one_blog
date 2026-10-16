package com.oneblog.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.oneblog.file.ImageType;

/** 바이트 배열을 담은 record는 배열 내용으로 비교한다 (030, S6218·S6206). */
class ThumbnailRecordsTest {

    @Test
    void 썸네일은_같은_바이트와_형식이면_같다() {
        VideoThumbnailService.Thumbnail a = new VideoThumbnailService.Thumbnail(new byte[] {1, 2}, ImageType.PNG);
        VideoThumbnailService.Thumbnail b = new VideoThumbnailService.Thumbnail(new byte[] {1, 2}, ImageType.PNG);

        assertThat(a).isEqualTo(b)
                .hasSameHashCodeAs(b)
                .isNotEqualTo(new VideoThumbnailService.Thumbnail(new byte[] {1, 3}, ImageType.PNG))
                .isNotEqualTo(new VideoThumbnailService.Thumbnail(new byte[] {1, 2}, ImageType.JPEG))
                .hasToString("Thumbnail[type=PNG, data=[1, 2]]");
    }

    @Test
    void 요청_결과는_같은_바이트_주소_상태면_같다() {
        HttpVideoThumbnailFetcher.Fetched a = new HttpVideoThumbnailFetcher.Fetched(new byte[] {7}, null, 200);
        HttpVideoThumbnailFetcher.Fetched b = new HttpVideoThumbnailFetcher.Fetched(new byte[] {7}, null, 200);

        assertThat(a).isEqualTo(b)
                .hasSameHashCodeAs(b)
                .isNotEqualTo(new HttpVideoThumbnailFetcher.Fetched(new byte[] {8}, null, 200))
                .isNotEqualTo(new HttpVideoThumbnailFetcher.Fetched(new byte[] {7}, "/next", 200))
                .isNotEqualTo(new HttpVideoThumbnailFetcher.Fetched(new byte[] {7}, null, 302))
                .hasToString("Fetched[data=[7], location=null, status=200]");
    }
}
