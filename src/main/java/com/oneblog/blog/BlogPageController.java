package com.oneblog.blog;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 블로그 첫 화면 주소 /blog/{주소} (D-70, research R1). 항상 같은 정적 화면을 주고, 내용은 화면이 API로 채운다.
 * HTML에는 블로그 정보가 없으므로 누구에게 보여도 새는 정보가 없다.
 */
@Controller
public class BlogPageController {

    @GetMapping("/blog/{slug}")
    public String blogPage(@PathVariable("slug") String slug) {
        return "forward:/blog.html";
    }

    /** 글 상세 (004). og 태그는 014에서 서버가 채운다 (BRD-07). */
    @GetMapping("/blog/{slug}/posts/{id}")
    public String postPage(@PathVariable("slug") String slug, @PathVariable("id") String id) {
        return "forward:/post.html";
    }

    /** 글쓰기·고치기 화면 (004). */
    @GetMapping({"/blog/{slug}/write", "/blog/{slug}/posts/{id}/edit"})
    public String editorPage(@PathVariable("slug") String slug) {
        return "forward:/post-edit.html";
    }

    /** 블로그 관리 화면 (012). 권한은 화면이 부르는 API마다 서버가 확인한다. */
    @GetMapping("/blog/{slug}/manage")
    public String managePage(@PathVariable("slug") String slug) {
        return "forward:/blog-manage.html";
    }
}
