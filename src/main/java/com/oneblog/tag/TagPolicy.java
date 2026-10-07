package com.oneblog.tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.ValidationFailedException;

/**
 * 태그 정리와 규칙 (6.4, D-88, research R5). 블로그 태그와 글 태그(008)가 함께 쓴다.
 * 금칙어(6.4)는 보류라 검사하지 않는다 (constitution I).
 */
@Component
public class TagPolicy {

    public static final int MAX_TAGS = 10;
    public static final int MAX_LENGTH = 20;

    private static final Pattern TAG = Pattern.compile("^[가-힣a-z0-9_]{1," + MAX_LENGTH + "}$");
    private static final Pattern LEADING_HASH = Pattern.compile("^#+");
    private static final Pattern INNER_SPACES = Pattern.compile("\\s+");

    /** 태그 하나 정리: 앞뒤 공백·앞의 # 제거, 가운데 공백 → _, 영문 소문자. */
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.strip();
        value = LEADING_HASH.matcher(value).replaceFirst("").strip();
        value = INNER_SPACES.matcher(value).replaceAll("_");
        return value.toLowerCase(Locale.ROOT);
    }

    public boolean isValid(String normalized) {
        return normalized != null && TAG.matcher(normalized).matches();
    }

    /**
     * 정리하고 검사한 뒤 같은 태그를 하나로 합친다(입력 순서 유지).
     * 틀린 태그가 있거나 합친 결과가 10개를 넘으면 ValidationFailedException (field: tags[i] 또는 tags).
     *
     * @param field 오류에 쓸 필드 이름 (예: "tags")
     */
    public List<String> normalizeAll(List<String> rawTags, String field) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < rawTags.size(); i++) {
            String tag = normalize(rawTags.get(i));
            if (!isValid(tag)) {
                errors.add(new ErrorResponse.FieldError(field + "[" + i + "]",
                        "태그는 1~20자의 한글, 영문, 숫자, _만 쓸 수 있습니다."));
                continue;
            }
            result.add(tag);
        }
        if (errors.isEmpty() && result.size() > MAX_TAGS) {
            errors.add(new ErrorResponse.FieldError(field, "태그는 " + MAX_TAGS + "개까지 달 수 있습니다."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return List.copyOf(result);
    }
}
