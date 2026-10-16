package com.oneblog.common.text;

/**
 * 문자열 끝 정리 도우미. 정규식(replaceAll("x+$", ""))은 긴 입력에서 되짚기가 많아질 수 있어 글자를 직접 본다
 * (SonarQube S5852, 030).
 */
public final class Trim {

    private Trim() {
    }

    /** 끝에 붙은 chars의 글자를 모두 지운다. 예: trailing("a/b//", "/") → "a/b" */
    public static String trailing(String value, String chars) {
        int end = value.length();
        while (end > 0 && chars.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }
}
