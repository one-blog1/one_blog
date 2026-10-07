package com.oneblog.common.text;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 글 본문 마크다운을 HTML로 바꾸고 위험한 태그·속성·주소를 지운다 (SEC-06, D-64, 6.6 에디터).
 * 1) 마크다운 안의 HTML은 글자로 바꾸고(escapeHtml) javascript: 같은 주소를 지운다(sanitizeUrls).
 * 2) 그 결과를 jsoup 허용 목록으로 한 번 더 거른다. 화면은 이 결과에만 innerHTML을 쓴다 (constitution III).
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
        return document.body().html();
    }

    /** 목록·미리보기용 글자만 뽑기 (태그 없이). */
    public String toPlainText(String markdown, int maxLength) {
        String text = Jsoup.parse(toSafeHtml(markdown)).text();
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }
}
