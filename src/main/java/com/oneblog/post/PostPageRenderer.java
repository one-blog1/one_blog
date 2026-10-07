package com.oneblog.post;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.web.ApiException;
import com.oneblog.file.FilePurpose;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.member.UserDisplayService;

/**
 * 글 상세 화면에 SNS 미리보기(Open Graph) 태그를 채운다 (BRD-07, 6.2, D-66). 카톡·X는 JS를 실행하지 않아서다.
 * - 비회원이 볼 수 있는 글만 채운다(전체 공개, 또는 일부 공개 + 맞는 key). 볼 수 없으면 기본 문구만 넣어 아무것도 새지 않게 한다
 * - 순서: 주소(og:url) → 제목(og:title, 닉네임까지 og:description) → 이미지(og:image: 글의 첫 이미지, 없으면 블로그 대표 이미지)
 * - 값은 모두 HTML 특수문자를 바꿔 넣는다 (SEC-06)
 * 본문은 화면이 API로 채우므로 HTML에는 넣지 않는다.
 */
@Service
public class PostPageRenderer {

    private static final String MARKER = "</head>";

    private final PostRepository postRepository;
    private final BlogRepository blogRepository;
    private final BlogAccessService accessService;
    private final StoredFileRepository fileRepository;
    private final UserDisplayService userDisplay;
    private final String baseUrl;
    private volatile String template;

    public PostPageRenderer(PostRepository postRepository, BlogRepository blogRepository,
            BlogAccessService accessService, StoredFileRepository fileRepository, UserDisplayService userDisplay,
            @Value("${app.public-base-url:}") String baseUrl) {
        this.postRepository = postRepository;
        this.blogRepository = blogRepository;
        this.accessService = accessService;
        this.fileRepository = fileRepository;
        this.userDisplay = userDisplay;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /**
     * 트랜잭션을 걸지 않는다: 볼 수 없는 글이면 BlogAccessService가 예외를 던지는데, 같은 트랜잭션이면
     * 그 예외를 잡아도 트랜잭션이 롤백 전용이 되어 응답이 실패한다.
     */
    public String render(String rawId, String key, String requestBase, String path) {
        String base = baseUrl.isEmpty() ? requestBase : baseUrl;
        String tags = tagsFor(rawId, key, base, path);
        String html = template();
        int at = html.indexOf(MARKER);
        return at < 0 ? html : html.substring(0, at) + tags + html.substring(at);
    }

    private String tagsFor(String rawId, String key, String base, String path) {
        String url = base + path + (key == null || key.isBlank() ? "" : "?key=" + urlEncode(key));
        Post post = parse(rawId) == null ? null : postRepository.findById(parse(rawId))
                .filter(p -> p.isVisible() && p.getBlogId() != null).orElse(null);
        Blog blog = post == null ? null : blogRepository.findById(post.getBlogId()).orElse(null);
        if (post == null || blog == null || !visibleToGuests(blog, key)) {
            return meta("og:site_name", "One Blog") + meta("og:title", "One Blog") + meta("og:type", "website");
        }
        String author = UserDisplayService.authorName(userDisplay.names(List.of(post.getUserId())), post.getUserId(),
                post.isAuthorDetached());
        String image = firstImage(post);
        if (image == null && blog.getCoverImageUrl() != null && blog.getCoverImageUrl().startsWith("/files/")) {
            image = blog.getCoverImageUrl();
        }
        StringBuilder tags = new StringBuilder()
                .append(meta("og:url", url))
                .append(meta("og:type", "article"))
                .append(meta("og:site_name", "One Blog"))
                .append(meta("og:title", post.getTitle()))
                .append(meta("og:description", author + " · " + blog.getName()))
                .append("<meta name=\"twitter:card\" content=\"")
                .append(image == null ? "summary" : "summary_large_image").append("\">\n");
        if (image != null) {
            tags.append(meta("og:image", base + image));
        }
        tags.append("<title>").append(escape(post.getTitle() + " - " + blog.getName())).append("</title>\n");
        return tags.toString();
    }

    /** 로그인하지 않은 사람(=미리보기 수집기)이 볼 수 있는지. */
    private boolean visibleToGuests(Blog blog, String key) {
        try {
            accessService.check(blog, key, null);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private String firstImage(Post post) {
        for (Object[] row : fileRepository.findImageRows(List.of(post.getId()), FilePurpose.POST)) {
            return "/files/" + row[1];
        }
        return null;
    }

    private String template() {
        String cached = template;
        if (cached == null) {
            try (InputStream in = new ClassPathResource("static/post.html").getInputStream()) {
                cached = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                        // 서버가 제목을 넣으므로 기본 제목은 뺀다 (제목이 두 개가 되지 않게)
                        .replace("<title>글 - One Blog</title>", "");
            } catch (IOException e) {
                throw new IllegalStateException("post.html을 읽지 못했습니다.", e);
            }
            template = cached;
        }
        return cached;
    }

    private static Long parse(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String meta(String property, String content) {
        return "<meta property=\"" + property + "\" content=\"" + escape(content) + "\">\n";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
