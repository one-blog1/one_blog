package com.oneblog.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 파일 앞부분으로 형식 판단 (6.3, SEC-08). */
class ImageTypeDetectorTest {

    @Test
    void 허용하는_네_형식을_알아본다() {
        assertThat(ImageTypeDetector.detect(TestImages.jpeg())).isEqualTo(ImageType.JPEG);
        assertThat(ImageTypeDetector.detect(TestImages.png())).isEqualTo(ImageType.PNG);
        assertThat(ImageTypeDetector.detect(TestImages.gif())).isEqualTo(ImageType.GIF);
        assertThat(ImageTypeDetector.detect(TestImages.webp())).isEqualTo(ImageType.WEBP);
    }

    @Test
    void svg_실행파일_텍스트는_받지_않는다() {
        assertThat(ImageTypeDetector.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ImageTypeDetector.detect(new byte[] {'M', 'Z', (byte) 0x90, 0})).isNull();
        assertThat(ImageTypeDetector.detect("hello".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ImageTypeDetector.detect(new byte[0])).isNull();
    }

    @Test
    void 확장자와_MIME_타입() {
        assertThat(ImageType.fromFilename("a.JPEG")).isEqualTo(ImageType.JPEG);
        assertThat(ImageType.fromFilename("a.jpg")).isEqualTo(ImageType.JPEG);
        assertThat(ImageType.fromFilename("a.svg")).isNull();
        assertThat(ImageType.fromFilename("a.png.exe")).isNull();
        assertThat(ImageType.fromFilename("noext")).isNull();
        assertThat(ImageType.fromMimeType("image/webp")).isEqualTo(ImageType.WEBP);
        assertThat(ImageType.fromMimeType("image/svg+xml")).isNull();
    }
}
