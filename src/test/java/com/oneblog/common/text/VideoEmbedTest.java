package com.oneblog.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 글 안 영상 플레이어 (021, D-125). 허용한 사이트 링크만 서버가 만든 iframe으로 바뀌고, 나머지는 그대로다 (SEC-06). */
class VideoEmbedTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    private Element onlyFrame(String markdown) {
        Document doc = Jsoup.parseBodyFragment(renderer.toSafeHtml(markdown));
        assertThat(doc.select("iframe")).hasSize(1);
        return doc.selectFirst("iframe");
    }

    @ParameterizedTest
    @CsvSource({
            // 유튜브: 여러 링크 모양이 모두 같은 nocookie 플레이어로
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ, https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=30, https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ, https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc, https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ, https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ, https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            // 치지직 클립·다시보기
            "https://chzzk.naver.com/clips/AbC123xyz, https://chzzk.naver.com/embed/clip/AbC123xyz",
            "https://chzzk.naver.com/embed/clip/12341234, https://chzzk.naver.com/embed/clip/12341234",
            "https://chzzk.naver.com/video/4567890, https://chzzk.naver.com/embed/vod/4567890",
            // 휴대폰에서 복사한 치지직 주소
            "https://m.chzzk.naver.com/video/15513352, https://chzzk.naver.com/embed/vod/15513352",
            "https://m.chzzk.naver.com/clips/AbC123xyz, https://chzzk.naver.com/embed/clip/AbC123xyz",
            // SOOP 다시보기: 새 도메인과 예전 도메인 모두
            "https://vod.sooplive.com/player/139470149, https://vod.sooplive.com/player/139470149/embed?showChat=false&autoPlay=false&mutePlay=false",
            "https://vod.sooplive.co.kr/player/139470149?change_second=10, https://vod.sooplive.com/player/139470149/embed?showChat=false&autoPlay=false&mutePlay=false",
            "https://vod.afreecatv.com/ST/139470149, https://vod.sooplive.com/player/139470149/embed?showChat=false&autoPlay=false&mutePlay=false"
    })
    void 한_줄에_영상_링크만_있으면_플레이어로_바뀐다(String link, String player) {
        Element frame = onlyFrame("앞 문단\n\n" + link + "\n\n뒤 문단");
        assertThat(frame.attr("src")).isEqualTo(player);
        assertThat(frame.parent().hasClass("video-embed")).isTrue();
        assertThat(frame.hasAttr("sandbox")).isTrue();
        assertThat(frame.attr("sandbox")).doesNotContain("allow-top-navigation").doesNotContain("allow-forms");
    }

    @Test
    void 마크다운_링크나_꺾쇠_링크로_써도_바뀐다() {
        assertThat(onlyFrame("[https://youtu.be/dQw4w9WgXcQ](https://youtu.be/dQw4w9WgXcQ)").attr("src"))
                .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
        assertThat(onlyFrame("<https://chzzk.naver.com/clips/AbC123>").attr("src"))
                .isEqualTo("https://chzzk.naver.com/embed/clip/AbC123");
    }

    @Test
    void 문장_중간의_링크나_글자가_다른_링크는_그대로_링크다() {
        String html = renderer.toSafeHtml("이 영상 보세요 https://youtu.be/dQw4w9WgXcQ 재밌어요\n\n"
                + "[여기](https://youtu.be/dQw4w9WgXcQ)");
        assertThat(html).doesNotContain("<iframe");
        assertThat(html).contains("<a href=\"https://youtu.be/dQw4w9WgXcQ\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=short",
            "https://chzzk.naver.com/clips/abc\"onload=\"alert(1)",
            "https://vod.sooplive.com/player/notanumber",
            "javascript:alert(1)//https://youtu.be/dQw4w9WgXcQ",
            "https://twitch.tv/videos/123456"
    })
    void 허용하지_않은_주소는_플레이어로_바꾸지_않는다(String link) {
        assertThat(renderer.toSafeHtml(link)).doesNotContain("<iframe");
    }

    @Test
    void 글쓴이가_직접_쓴_iframe은_허용한_사이트여도_지운다() {
        String html = renderer.toSafeHtml("<iframe src=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ\"></iframe>\n\n"
                + "<iframe src=\"https://evil.example.com\"></iframe>");
        assertThat(Jsoup.parseBodyFragment(html).select("iframe")).isEmpty();
    }

    @Test
    void 목록_미리보기_글자에는_플레이어가_끼지_않는다() {
        assertThat(renderer.toPlainText("오늘 본 영상\n\nhttps://youtu.be/dQw4w9WgXcQ", 100)).isEqualTo("오늘 본 영상");
    }
}
