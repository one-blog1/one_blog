package com.oneblog.common.text;

/**
 * 개인정보 가리기 (4.4). 화면이 아니라 서버가 응답을 만들 때 가린다.
 * - 이메일: @ 앞 처음 2글자만 보이고 나머지는 ***. @ 앞이 2글자 이하면 첫 글자만
 * - 전화번호: 가운데 자리를 가림 (010-1234-5678 → 010-****-5678)
 */
public final class Masking {

    private Masking() {
    }

    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /** 숫자만 저장된 휴대전화 번호를 3-가운데-4 형식으로 가린다. */
    public static String phone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return "***";
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        int middle = digits.length() - 7;
        return head + "-" + "*".repeat(middle) + "-" + tail;
    }

    /** 숫자만 저장된 번호를 보기 좋게 (010-1234-5678). */
    public static String formatPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return digits;
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, digits.length() - 4) + "-"
                + digits.substring(digits.length() - 4);
    }
}
