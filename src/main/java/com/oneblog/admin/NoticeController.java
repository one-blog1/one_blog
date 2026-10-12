package com.oneblog.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.web.PageParams;

/** 메인 공지 목록·내용 읽기 (BRD-10, D-122). 누구나. */
@RestController
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** category(GENERAL·UPDATE·FIX·MAINTENANCE)를 주면 그 종류만 (D-122). */
    @GetMapping("/api/notices")
    public Map<String, Object> list(@RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "category", required = false) String category) {
        return noticeService.list(PageParams.of(page, size), category);
    }

    @GetMapping("/api/notices/{id}")
    public NoticeService.NoticeDetail detail(@PathVariable("id") Long id) {
        return noticeService.detail(id);
    }
}
