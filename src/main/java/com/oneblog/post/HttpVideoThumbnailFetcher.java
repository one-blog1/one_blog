package com.oneblog.post;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 영상 사이트에서 썸네일을 받는다 (D-128). 글을 저장한 뒤 한 번만 부르고, 받은 사진은 우리 서버에 둔다.
 * - 유튜브: 썸네일 주소가 영상 번호로 정해져 있다 (i.ytimg.com/vi/{번호}/mqdefault.jpg, 320×180, 위아래 검은 띠 없음)
 * - 치지직: 공식이 아닌 API의 content.thumbnailImageUrl (클립 /service/v1/clips/{번호}/detail, 다시보기 /service/v2/videos/{번호})
 * - SOOP: 공식이 아닌 API(api.m.sooplive.com/station/video/a/view, POST nTitleNo)의 data.thumb
 * 응답은 Content-Type과 관계없이 글자로 받아 JSON으로 읽는다(JSON을 text/html로 보내는 곳이 있어도 읽히게).
 * 치지직·SOOP API는 바뀔 수 있다. 실패하면 썸네일 없이 둔다(글 저장에는 영향 없음).
 *
 * 남의 주소로 서버가 요청을 보내는 일이라(SSRF) API 주소는 여기서 정한 것만, 사진 주소는 아래 사이트의 https만 받고
 * API는 다른 곳으로 넘기는 응답(redirect)을 따르지 않고, 사진은 허용한 사이트 안에서만 3번까지 따라간다. 연결 2초·응답 3초, 사진은 2MB까지.
 */
@Component
public class HttpVideoThumbnailFetcher implements VideoThumbnailFetcher {

    static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(HttpVideoThumbnailFetcher.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 OneBlog";
    /** 영상 번호에 쓸 수 있는 글자 (VideoEmbeds 규칙과 같은 범위). */
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    /** 썸네일 사진을 받아도 되는 곳: 이 이름이거나 이 이름으로 끝나는 https 주소. */
    private static final List<String> IMAGE_HOST_SUFFIXES = List.of(
            "ytimg.com", "pstatic.net", "naver.net", "sooplive.com", "sooplive.co.kr", "afreecatv.com");
    private static final Set<String> IMAGE_HOSTS = Set.of("livecloud-thumb.akamaized.net");

    private final boolean enabled;
    private final RestClient client;

    @Autowired
    public HttpVideoThumbnailFetcher(@Value("${app.video-thumbnail.enabled:true}") boolean enabled) {
        this(enabled, defaultClient());
    }

    /** HTTP 클라이언트를 받아 만든다. 테스트가 가짜 응답(리다이렉트·큰 사진)을 넣을 때 쓴다 (029 research R6). 동작은 같다. */
    HttpVideoThumbnailFetcher(boolean enabled, RestClient client) {
        this.enabled = enabled;
        this.client = client;
    }

    private static RestClient defaultClient() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    @Override
    public Optional<byte[]> fetch(String key) {
        if (!enabled || key == null) {
            return Optional.empty();
        }
        int colon = key.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        String kind = key.substring(0, colon);
        String id = key.substring(colon + 1);
        if (!ID.matcher(id).matches()) {
            return Optional.empty();
        }
        try {
            String imageUrl = switch (kind) {
                case "youtube" -> "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
                case "chzzk-clip" -> chzzkThumbnail("https://api.chzzk.naver.com/service/v1/clips/" + id + "/detail");
                case "chzzk-video" -> chzzkThumbnail("https://api.chzzk.naver.com/service/v2/videos/" + id);
                case "soop" -> soopThumbnail(id);
                default -> null;
            };
            imageUrl = normalize(imageUrl);
            if (imageUrl == null || !allowedImageUrl(imageUrl)) {
                log.info("영상 썸네일 주소를 받지 못했거나 허용하지 않은 주소입니다: {} → {}", key, imageUrl);
                return Optional.empty();
            }
            return Optional.ofNullable(download(imageUrl));
        } catch (RuntimeException e) {
            log.info("영상 썸네일을 받지 못했습니다: {} ({})", key, e.toString());
            return Optional.empty();
        }
    }

    private String chzzkThumbnail(String apiUrl) {
        Map<String, Object> body = json(client.get().uri(URI.create(apiUrl)).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(String.class));
        if (body.get("content") instanceof Map<?, ?> content && content.get("thumbnailImageUrl") instanceof String url) {
            return url;
        }
        log.info("치지직 썸네일 응답에 사진 주소가 없습니다: {}", preview(body));
        return null;
    }

    private String soopThumbnail(String id) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nTitleNo", id);
        form.add("nApiLevel", "10");
        Map<String, Object> body = json(client.post().uri(URI.create("https://api.m.sooplive.com/station/video/a/view"))
                .header(HttpHeaders.REFERER, "https://vod.sooplive.com/player/" + id)
                .header(HttpHeaders.ORIGIN, "https://vod.sooplive.com")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML, MediaType.ALL)
                .body(form)
                .retrieve().body(String.class));
        if (body.get("data") instanceof Map<?, ?> data && data.get("thumb") instanceof String url) {
            return url;
        }
        log.info("SOOP 썸네일 응답에 사진 주소가 없습니다: {}", preview(body));
        return null;
    }

    /**
     * 응답 글자를 JSON으로 읽는다. SOOP처럼 JSON을 text/html로 보내는 곳이 있어 Content-Type을 믿지 않고 직접 읽는다.
     * 읽을 수 없으면 빈 Map.
     */
    static Map<String, Object> json(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return JsonParserFactory.getJsonParser().parseMap(text.strip());
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private static String preview(Map<String, Object> body) {
        String text = String.valueOf(body);
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    /** //로 시작하는 주소는 https로, http는 https로. 치지직 다시보기 주소의 {type} 자리는 480으로. */
    static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.strip().replace("{type}", "480");
        if (value.startsWith("//")) {
            value = "https:" + value;
        } else if (value.startsWith("http://")) {
            value = "https://" + value.substring("http://".length());
        }
        return value;
    }

    /** https이고, 사용자 정보·다른 포트가 없고, 허용한 사이트의 주소인지. */
    static boolean allowedImageUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (IMAGE_HOSTS.contains(host)) {
            return true;
        }
        return IMAGE_HOST_SUFFIXES.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
    }

    /** 다른 주소로 넘기는 응답(SOOP 스냅샷 주소 등)은 3번까지 따라가되, 넘어갈 주소도 허용한 사이트인지 매번 확인한다. */
    private byte[] download(String imageUrl) {
        URI uri = URI.create(imageUrl);
        for (int hop = 0; hop <= 3; hop++) {
            Fetched fetched = fetchOnce(uri);
            if (fetched.data() != null || fetched.location() == null) {
                if (fetched.data() == null) {
                    log.info("영상 썸네일 사진을 받지 못했습니다: {} ({})", uri, fetched.status());
                }
                return fetched.data();
            }
            URI next = uri.resolve(fetched.location());
            if (!allowedImageUrl(normalize(next.toString()))) {
                log.info("영상 썸네일이 허용하지 않은 주소로 넘어가려 해서 멈췄습니다: {} → {}", uri, next);
                return null;
            }
            uri = URI.create(normalize(next.toString()));
        }
        return null;
    }

    /** 한 번 요청한 결과: 받은 사진(없으면 null), 넘어갈 주소, 응답 상태. 배열은 내용으로 비교한다 (S6218). */
    record Fetched(byte[] data, String location, int status) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Fetched that && status == that.status
                    && Objects.equals(location, that.location) && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(data), location, status);
        }

        @Override
        public String toString() {
            return "Fetched[data=" + Arrays.toString(data) + ", location=" + location + ", status=" + status + "]";
        }
    }

    private Fetched fetchOnce(URI uri) {
        return client.get().uri(uri).accept(MediaType.ALL).exchange((request, response) -> {
            int status = response.getStatusCode().value();
            if (response.getStatusCode().is3xxRedirection()) {
                return new Fetched(null, response.getHeaders().getFirst(HttpHeaders.LOCATION), status);
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                return new Fetched(null, null, status);
            }
            long length = response.getHeaders().getContentLength();
            if (length > MAX_BYTES) {
                return new Fetched(null, null, status);
            }
            try (InputStream in = response.getBody()) {
                byte[] data = in.readNBytes(MAX_BYTES + 1);
                return new Fetched(data.length > MAX_BYTES || data.length == 0 ? null : data, null, status);
            }
        });
    }
}
