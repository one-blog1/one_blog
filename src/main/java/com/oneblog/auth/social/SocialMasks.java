package com.oneblog.auth.social;

/**
 * 소셜 가입 화면에 보여 줄 값을 가린다 (027, 4.4). 화면에는 가린 값만 보내고, 가입할 때는 서버가 원래 값을 쓴다.
 */
final class SocialMasks {

    private SocialMasks() {
    }

    /** 홍길동 → 홍*동, 홍길 → 홍*, 남궁민수 → 남**수. 한 글자는 그대로. */
    static String name(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        int[] chars = name.codePoints().toArray();
        if (chars.length == 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder().appendCodePoint(chars[0]);
        if (chars.length == 2) {
            return sb.append('*').toString();
        }
        sb.append("*".repeat(chars.length - 2));
        return sb.appendCodePoint(chars[chars.length - 1]).toString();
    }

    /** 01012345678 → 010-****-5678, 0111234567 → 011-***-4567. 숫자 10~11자리가 아니면 null. */
    static String phone(String digits) {
        if (digits == null || !digits.matches("^[0-9]{10,11}$")) {
            return null;
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-" + "*".repeat(digits.length() - 7) + "-" + tail;
    }

    /** abcdef@naver.com → ab***@naver.com, ab@naver.com → a***@naver.com. */
    static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return null;
        }
        int keep = at > 2 ? 2 : 1;
        return email.substring(0, keep) + "***" + email.substring(at);
    }
}
