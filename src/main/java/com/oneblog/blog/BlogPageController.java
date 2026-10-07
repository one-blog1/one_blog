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
}
