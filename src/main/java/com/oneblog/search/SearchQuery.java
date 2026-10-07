package com.oneblog.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.ValidationFailedException;

/**
 * 검색어 정리와 검사 (BRD-08, 6.1).
 * - 앞뒤 공백을 빼고 가운데 공백은 하나로. 2~20자, 글자나 숫자가 하나 이상 (빈칸·특수문자만이면 거부)
 * - FULLTEXT(ngram, 2글자 단위)에는 글자·숫자만 남긴 2글자 이상 낱말을 모두 포함하는 BOOLEAN 검색식을 만든다.
 *   검색식 기호(+ - " * 등)는 모두 지우므로 사용자가 검색식을 조작할 수 없다. 값은 바인딩으로만 넘긴다.
 */
public record SearchQuery(String text, String fulltext) {

    public static final int MIN = 2;
    public static final int MAX = 20;

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern HAS_WORD = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}_]");

    public static SearchQuery parse(String raw) {
        String text = raw == null ? "" : SPACES.matcher(raw.strip()).replaceAll(" ");
        int length = text.codePointCount(0, text.length());
        if (length < MIN || length > MAX || !HAS_WORD.matcher(text).find()) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("q",
                    "검색어는 글자나 숫자를 넣어 2~20자로 입력해 주세요.")));
        }
        List<String> words = new ArrayList<>();
        for (String part : text.split(" ")) {
            String word = NOT_WORD.matcher(part).replaceAll("");
            if (word.codePointCount(0, word.length()) >= MIN) {
                words.add("+\"" + word + "\"");
            }
        }
        return new SearchQuery(text, words.isEmpty() ? null : String.join(" ", words));
    }
}
