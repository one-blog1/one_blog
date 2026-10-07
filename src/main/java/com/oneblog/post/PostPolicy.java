package com.oneblog.post;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.ValidationFailedException;

/** 제목 1~30자, 본문 1~5,000자(공백·마크다운 기호 포함) (6.6, D-92). 글자 수는 서버에서 센다. */
@Component
public class PostPolicy {

    public static final int TITLE_MAX = 30;
    public static final int CONTENT_MAX = 5000;

    public String normalizeTitle(String title) {
        return title == null ? null : title.strip();
    }

    /** 이모지 하나를 한 글자로 센다. */
    public static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    public void validate(String title, String content) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (title == null || title.isEmpty() || length(title) > TITLE_MAX) {
            errors.add(new ErrorResponse.FieldError("title", "제목은 1~30자로 입력해 주세요."));
        }
        if (content == null || content.isBlank()) {
            errors.add(new ErrorResponse.FieldError("content", "본문을 입력해 주세요."));
        } else if (length(content) > CONTENT_MAX) {
            errors.add(new ErrorResponse.FieldError("content", "본문은 5,000자까지 쓸 수 있습니다."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }
}
