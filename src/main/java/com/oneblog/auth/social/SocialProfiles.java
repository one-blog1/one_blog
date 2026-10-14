package com.oneblog.auth.social;

import java.util.Map;
import java.util.Optional;

/**
 * 사이트마다 다른 사용자 정보 모양을 하나로 맞춘다 (D-126, 027).
 * 카카오·구글 이메일은 사이트가 "인증됨"이라고 알려 준 경우만 믿는다. 아니면 가입 화면에서 우리 인증번호로 확인한다.
 * 네이버는 인증 여부를 알려 주지 않지만 네이버 계정 이메일을 그대로 믿는다 (D-139).
 * 이름·휴대전화는 우리 형식(이름 1~50자, 휴대전화 숫자 10~11자리)에 맞을 때만 쓰고, 아니면 가입 화면에서 받는다.
 */
final class SocialProfiles {

    private SocialProfiles() {
    }

    static Optional<SocialTickets.Pending> from(SocialProvider provider, Map<String, Object> attributes) {
        if (attributes == null) {
            return Optional.empty();
        }
        return switch (provider) {
            case KAKAO -> kakao(attributes);
            case NAVER -> naver(attributes);
            case GOOGLE -> google(attributes);
        };
    }

    /** {id, properties:{nickname}, kakao_account:{profile:{nickname}, email, is_email_valid, is_email_verified}} */
    private static Optional<SocialTickets.Pending> kakao(Map<String, Object> a) {
        String id = text(a.get("id"));
        if (id == null) {
            return Optional.empty();
        }
        Map<String, Object> account = map(a.get("kakao_account"));
        Map<String, Object> profile = map(account.get("profile"));
        String nickname = firstText(profile.get("nickname"), map(a.get("properties")).get("nickname"));
        String email = text(account.get("email"));
        boolean verified = email != null && Boolean.TRUE.equals(account.get("is_email_valid"))
                && Boolean.TRUE.equals(account.get("is_email_verified"));
        return Optional.of(new SocialTickets.Pending(SocialProvider.KAKAO, id, email, verified, null, nickname,
                null));
    }

    /** {resultcode, response:{id, email, name, nickname, mobile}}. 이메일은 믿는다 (D-139). */
    private static Optional<SocialTickets.Pending> naver(Map<String, Object> a) {
        Map<String, Object> r = map(a.get("response"));
        String id = text(r.get("id"));
        if (id == null) {
            return Optional.empty();
        }
        String email = text(r.get("email"));
        return Optional.of(new SocialTickets.Pending(SocialProvider.NAVER, id, email, email != null,
                name(r.get("name")), text(r.get("nickname")), phone(r.get("mobile"))));
    }

    /** {sub, email, email_verified, name} */
    private static Optional<SocialTickets.Pending> google(Map<String, Object> a) {
        String id = text(a.get("sub"));
        if (id == null) {
            return Optional.empty();
        }
        String email = text(a.get("email"));
        Object ev = a.get("email_verified");
        boolean verified = email != null && (Boolean.TRUE.equals(ev) || "true".equals(String.valueOf(ev)));
        return Optional.of(new SocialTickets.Pending(SocialProvider.GOOGLE, id, email, verified,
                name(a.get("name")), text(a.get("given_name")), null));
    }

    /** 이름은 1~50자만 쓴다. */
    static String name(Object value) {
        String s = text(value);
        return s != null && s.length() <= 50 ? s : null;
    }

    /** 휴대전화는 숫자만 남긴다. +82로 시작하면 0으로 바꾼다. 우리 형식(01로 시작하는 10~11자리)이 아니면 버린다. */
    static String phone(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        if (s.startsWith("+82")) {
            s = "0" + s.substring(3).replaceAll("[^0-9]", "").replaceFirst("^0", "");
        }
        String digits = s.replaceAll("[^0-9]", "");
        return digits.matches("^01[0-9]{8,9}$") ? digits : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String firstText(Object first, Object second) {
        String value = text(first);
        return value != null ? value : text(second);
    }

    /** 글자로 바꾸고 너무 긴 값은 버린다 (DB provider_user_id 100자). */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).strip();
        return s.isEmpty() || s.length() > 100 ? null : s;
    }
}
