package com.oneblog.common.text;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 글 본문에 한 줄로 붙여 넣은 영상 링크를 글 안에서 재생하는 플레이어 주소로 바꾼다 (021, D-125).
 * 허용한 사이트(유튜브, 치지직, SOOP)만 바꾸고, 나머지 링크는 그대로 링크로 둔다.
 * 플레이어 주소는 링크에서 뽑은 영상 번호(정해진 글자만)로 서버가 직접 만든다. 사용자가 쓴 HTML이나 주소를 그대로 넣지 않는다.
 * 여기 사이트를 더하면 SecurityConfig의 frame-src에도 같은 주소를 더한다.
 * 치지직 다시보기는 외부 재생기 주소가 없어(embed/vod를 넣으면 "존재하지 않는 채널") 재생기 대신 치지직으로 가는 카드로 둔다 (D-127).
 */
public final class VideoEmbeds {

    /**
     * 바꾼 결과. provider는 화면 class 이름(video-embed-youtube 등)과 iframe title에 쓴다.
     * frame이 false면 src는 재생기가 아니라 그 사이트의 영상 화면 주소이고, 화면은 링크 카드로 그린다.
     * key는 영상 구분(youtube:번호, chzzk-clip:번호, chzzk-video:번호, soop:번호). 목록 썸네일을 받을 때 쓴다 (D-128).
     */
    public record Embed(String provider, String title, String src, boolean frame, String key) {
    }

    private record Rule(String provider, String kind, String title, Pattern pattern, Function<Matcher, String> src,
            boolean frame) {

        Rule(String provider, String kind, String title, Pattern pattern, Function<Matcher, String> src) {
            this(provider, kind, title, pattern, src, true);
        }
    }

    private static final List<Rule> RULES = List.of(
            // 유튜브: watch?v=, youtu.be/, shorts/, live/, embed/ (www·m·music 포함). 영상 번호는 11자
            new Rule("youtube", "youtube", "YouTube 동영상", Pattern.compile(
                    "^https?://(?:(?:www|m|music)\\.)?(?:youtube\\.com/(?:watch\\?(?:[^#\\s]*&)?v=|shorts/|live/|embed/)"
                            + "|youtu\\.be/)([A-Za-z0-9_-]{11})(?:[?&#][^\\s]*)?$"),
                    m -> "https://www.youtube-nocookie.com/embed/" + m.group(1)),
            // 치지직 클립: /clips/{id} 또는 /embed/clip/{id}. 휴대폰 주소(m.chzzk.naver.com)도 받는다
            new Rule("chzzk", "chzzk-clip", "치지직 클립", Pattern.compile(
                    "^https?://(?:m\\.)?chzzk\\.naver\\.com/(?:clips|embed/clip)/([A-Za-z0-9_-]{1,40})(?:[?#][^\\s]*)?$"),
                    m -> "https://chzzk.naver.com/embed/clip/" + m.group(1)),
            // 치지직 다시보기: /video/{번호}. 휴대폰 주소도. 치지직이 다시보기 퍼가기를 주지 않아 카드로 (D-127)
            new Rule("chzzk", "chzzk-video", "치지직 다시보기", Pattern.compile(
                    "^https?://(?:m\\.)?chzzk\\.naver\\.com/(?:video|embed/vod)/(\\d{1,15})(?:[?#][^\\s]*)?$"),
                    m -> "https://chzzk.naver.com/video/" + m.group(1), false),
            // SOOP 다시보기: vod.sooplive.com(2026-03 도메인 변경), 예전 vod.sooplive.co.kr·vod.afreecatv.com·v.afree.ca도 받는다
            new Rule("soop", "soop", "SOOP 다시보기", Pattern.compile(
                    "^https?://(?:vod\\.sooplive\\.com|vod\\.sooplive\\.co\\.kr|vod\\.afreecatv\\.com|v\\.afree\\.ca)"
                            + "/(?:player|ST)/(\\d{1,15})(?:[/?#][^\\s]*)?$"),
                    m -> "https://vod.sooplive.com/player/" + m.group(1) + "/embed?showChat=false&autoPlay=false&mutePlay=false"));

    /** 플레이어를 띄울 수 있는 사이트. SecurityConfig의 frame-src와 맞춘다. */
    public static final List<String> FRAME_ORIGINS = List.of(
            "https://www.youtube-nocookie.com", "https://chzzk.naver.com", "https://vod.sooplive.com");

    private VideoEmbeds() {
    }

    /** 영상 링크면 플레이어 정보, 아니면 빈 값. 앞뒤 공백은 무시한다. */
    public static Optional<Embed> from(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.length() > 500) {
            return Optional.empty();
        }
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(trimmed);
            if (matcher.matches()) {
                return Optional.of(new Embed(rule.provider(), rule.title(), rule.src().apply(matcher), rule.frame(),
                        rule.kind() + ":" + matcher.group(1)));
            }
        }
        return Optional.empty();
    }
}
