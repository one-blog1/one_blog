package com.oneblog.banner;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 광고 링크 검사 (6.8, SEC-06). https:// · http:// 주소(호스트가 있어야 함) 또는 /로 시작하는 우리 사이트 주소만.
 * //로 시작하는 주소(다른 사이트로 감), 역슬래시, 빈칸, javascript: 같은 주소는 받지 않는다.
 */
final class BannerLinks {

    static final int MAX_LENGTH = 500;
    private static final Pattern INTERNAL = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");
    private static final Pattern NO_SPACE = Pattern.compile("^[^\\s\\\\]+$");

    private BannerLinks() {
    }

    static boolean isValid(String url) {
        if (url == null || url.isEmpty() || url.length() > MAX_LENGTH) {
            return false;
        }
        if (url.startsWith("/")) {
            return INTERNAL.matcher(url).matches();
        }
        if (!NO_SPACE.matcher(url).matches()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isExternal(String url) {
        return url != null && !url.startsWith("/");
    }
}
