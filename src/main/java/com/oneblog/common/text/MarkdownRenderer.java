package com.oneblog.common.text;

import java.util.List;
import java.util.Optional;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 글 본문 마크다운을 HTML로 바꾸고 위험한 태그·속성·주소를 지운다 (SEC-06, D-64, 6.6 에디터).
 * 1) 마크다운 안의 HTML은 글자로 바꾸고(escapeHtml) javascript: 같은 주소를 지운다(sanitizeUrls).
 * 2) 그 결과를 jsoup 허용 목록으로 한 번 더 거른다. 화면은 이 결과에만 innerHTML을 쓴다 (constitution III).
 * 3) 거른 뒤에, 한 줄에 영상 링크만 있는 문단은 서버가 만든 플레이어(iframe)로 바꾼다 (021, VideoEmbeds).
 *    사용자가 쓴 iframe은 2)에서 이미 지워졌고, 여기서 넣는 iframe은 허용한 사이트 주소로만 만든다.
 */
@Component
public class MarkdownRenderer {

    /** 업로드 이미지 주소(/files/...) 같은 상대 주소를 판단할 때 쓰는 가상의 기준 주소. 결과에는 남지 않는다. */
    private static final String BASE_URI = "https://oneblog.invalid/";

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Safelist safelist;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create(), StrikethroughExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .build();
        this.safelist = Safelist.relaxed()
                .addTags("hr", "del", "s")
                .addAttributes("code", "class")
                .removeProtocols("img", "src", "http")
                .preserveRelativeLinks(true);
    }

    public String toSafeHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String html = renderer.render(parser.parse(markdown));
        String cleaned = Jsoup.clean(html, BASE_URI, safelist);
        Document document = Jsoup.parseBodyFragment(cleaned, BASE_URI);
        document.outputSettings().prettyPrint(false);
        // 다른 사이트로 가는 링크가 이 페이지를 조작하거나 주소(공유 링크 key 등)를 넘겨받지 못하게
        document.select("a[href]").attr("rel", "nofollow noopener noreferrer").attr("target", "_blank");
        // 이미지는 이 사이트에 올린 것(/files/...)만 보여준다. 다른 사이트 이미지는 읽는 사람의 IP를 넘기고 추적에 쓰일 수 있다
        document.select("img").forEach(img -> {
            if (!img.attr("src").startsWith("/files/")) {
                img.remove();
            } else {
                img.attr("loading", "lazy");
            }
        });
        embedVideos(document);
        return document.body().html();
    }

    /** 한 줄에 영상 링크만 있는 문단(글자 그대로 또는 링크 하나)을 플레이어로 바꾼다. 문장 중간의 링크는 그대로 둔다. */
    private static void embedVideos(Document document) {
        for (Element paragraph : document.select("p")) {
            videoOf(paragraph).ifPresent(embed -> paragraph.replaceWith(player(embed)));
        }
    }

    /** 문단이 영상 링크 한 줄이면 그 영상. 링크 하나만 있고 그 글자가 주소 그대로일 때만 (<주소>, [주소](주소)). */
    private static Optional<VideoEmbeds.Embed> videoOf(Element paragraph) {
        String url = null;
        if (paragraph.childrenSize() == 0) {
            url = paragraph.text();
        } else if (paragraph.childrenSize() == 1 && paragraph.child(0).is("a[href]")) {
            // [여기](주소)처럼 이름을 붙인 링크는 링크로 둔다
            Element link = paragraph.child(0);
            String href = link.attr("href");
            if (paragraph.text().trim().equals(href) && link.text().trim().equals(href)) {
                url = href;
            }
        }
        return VideoEmbeds.from(url);
    }

    /**
     * 본문에서 처음 나오는 사진·영상 중 첫 번째가 영상 링크 한 줄이면 그 영상 (목록 썸네일, D-128).
     * 이 사이트에 올린 사진(/files/...)이 먼저 나오면 빈 값(사진이 썸네일).
     */
    public Optional<VideoEmbeds.Embed> leadingVideo(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Optional.empty();
        }
        String cleaned = Jsoup.clean(renderer.render(parser.parse(markdown)), BASE_URI, safelist);
        Document document = Jsoup.parseBodyFragment(cleaned, BASE_URI);
        // select는 문서 순서대로 돌려준다. 문단이 그 안의 사진보다 먼저 온다
        for (Element element : document.select("p, img")) {
            if (element.is("img")) {
                if (element.attr("src").startsWith("/files/")) {
                    return Optional.empty();
                }
                continue;
            }
            Optional<VideoEmbeds.Embed> video = videoOf(element);
            if (video.isPresent()) {
                return video;
            }
        }
        return Optional.empty();
    }

    private static Element player(VideoEmbeds.Embed embed) {
        if (!embed.frame()) {
            return linkCard(embed);
        }
        Element frame = new Element("iframe")
                .attr("src", embed.src())
                .attr("title", embed.title())
                .attr("loading", "lazy")
                .attr("allow", "autoplay; encrypted-media; picture-in-picture; fullscreen")
                .attr("allowfullscreen", "")
                .attr("referrerpolicy", "strict-origin-when-cross-origin")
                // 플레이어가 이 페이지를 다른 곳으로 옮기거나 폼을 보내지 못하게 (재생·전체 화면·새 창 열기만)
                .attr("sandbox", "allow-scripts allow-same-origin allow-presentation allow-popups allow-popups-to-escape-sandbox");
        return new Element("div").addClass("video-embed").addClass("video-embed-" + embed.provider()).appendChild(frame);
    }

    /** 재생기를 줄 수 없는 영상(치지직 다시보기)은 그 사이트로 가는 카드로 (D-127). 주소는 VideoEmbeds가 만든 것만. */
    private static Element linkCard(VideoEmbeds.Embed embed) {
        Element link = new Element("a")
                .attr("href", embed.src())
                .attr("rel", "nofollow noopener noreferrer")
                .attr("target", "_blank");
        link.appendChild(new Element("span").addClass("video-link-play").attr("aria-hidden", "true").text("▶"));
        Element text = new Element("span").addClass("video-link-text");
        text.appendChild(new Element("strong").text(embed.title()));
        text.appendChild(new Element("span").text(siteName(embed.provider()) + "에서 보기 ↗"));
        link.appendChild(text);
        return new Element("div").addClass("video-link").addClass("video-link-" + embed.provider()).appendChild(link);
    }

    private static String siteName(String provider) {
        return switch (provider) {
            case "chzzk" -> "치지직";
            case "soop" -> "SOOP";
            default -> "YouTube";
        };
    }

    /** 목록·미리보기용 글자만 뽑기 (태그 없이). */
    public String toPlainText(String markdown, int maxLength) {
        Document document = Jsoup.parse(toSafeHtml(markdown));
        document.select(".video-link").remove();
        String text = document.text();
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }
}
