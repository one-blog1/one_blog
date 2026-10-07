package com.oneblog.report;

import java.util.Map;

/** 신고 당시 대상 내용(target_snapshot)을 JSON 문자열로 (D-85). 값은 모두 문자열·숫자만 쓴다. */
final class SnapshotJson {

    private SnapshotJson() {
    }

    static String of(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            json.append(value instanceof Number ? value.toString() : quote(value.toString()));
        }
        return json.append('}').toString();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** 글·댓글 내용은 앞부분만 남긴다. */
    static String shorten(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.codePointCount(0, value.length()) <= max ? value
                : value.substring(0, value.offsetByCodePoints(0, max)) + "…";
    }
}
