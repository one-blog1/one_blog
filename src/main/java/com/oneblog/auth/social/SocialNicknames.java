package com.oneblog.auth.social;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.oneblog.member.SignupPolicy;
import com.oneblog.member.UserRepository;

/**
 * 소셜 가입 화면에 미리 채울 닉네임을 정한다 (027).
 * 기본은 사이트에서 받은 닉네임 그대로다. 그대로 쓸 수 없을 때만 고친다. 사용자는 가입 화면과 내 정보에서 바꿀 수 있다.
 */
@Component
class SocialNicknames {

    static final int MAX = 12;
    private static final String FALLBACK = "회원";
    private static final int TRIES = 10;

    private final SignupPolicy policy;
    private final UserRepository userRepository;

    SocialNicknames(SignupPolicy policy, UserRepository userRepository) {
        this.policy = policy;
        this.userRepository = userRepository;
    }

    String suggest(String hint) {
        return suggest(hint, userRepository::existsByNickname);
    }

    /**
     * 1) 형식이 맞고 금지어가 아니고 아무도 안 쓰면 그대로
     * 2) 한글·영문·숫자만 남기고 12자로 자른다. 2자 미만이거나 금지어면 "회원" + 숫자 4자리
     * 3) 이미 있으면 뒤에 2, 3, ... 을 붙인다 (12자를 넘으면 앞을 줄인다)
     * 4) 그래도 없으면 "회원" + 무작위 숫자
     */
    String suggest(String hint, Predicate<String> taken) {
        String nickname = policy.normalizeNickname(hint);
        if (usable(nickname) && !taken.test(nickname)) {
            return nickname;
        }
        String base = nickname == null ? "" : nickname.replaceAll("[^가-힣A-Za-z0-9]", "");
        if (base.length() > MAX) {
            base = base.substring(0, MAX);
        }
        if (!usable(base)) {
            base = FALLBACK + randomDigits(4);
        }
        if (!taken.test(base)) {
            return base;
        }
        for (int n = 2; n < 2 + TRIES; n++) {
            String suffix = String.valueOf(n);
            String candidate = base.substring(0, Math.min(base.length(), MAX - suffix.length())) + suffix;
            if (usable(candidate) && !taken.test(candidate)) {
                return candidate;
            }
        }
        for (int i = 0; i < TRIES; i++) {
            String candidate = FALLBACK + randomDigits(6);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        return FALLBACK + randomDigits(8);
    }

    private boolean usable(String nickname) {
        return policy.isValidNicknameFormat(nickname) && !policy.isReservedNickname(nickname);
    }

    private static String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
