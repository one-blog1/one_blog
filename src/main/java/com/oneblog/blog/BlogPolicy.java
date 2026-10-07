package com.oneblog.blog;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 블로그 입력의 정리와 규칙 (BLG-01, 6.5, research R2). 화면도 같은 규칙을 검사하지만 서버가 최종 판단한다.
 */
@Component
public class BlogPolicy {

    public static final int NAME_MAX = 50;
    public static final int DESCRIPTION_MAX = 500;
    public static final int SLUG_MIN = 3;
    public static final int SLUG_MAX = 30;

    /** 영문 소문자·숫자를 -로 이음. -로 시작·끝나거나 --가 들어가면 안 됨. */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final Set<String> reservedSlugs;

    public BlogPolicy(BlogProperties properties) {
        this.reservedSlugs = properties.reservedSlugSet();
    }

    public String normalizeSlug(String slug) {
        return slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isValidSlugFormat(String slug) {
        return slug != null && slug.length() >= SLUG_MIN && slug.length() <= SLUG_MAX
                && SLUG.matcher(slug).matches();
    }

    public boolean isReservedSlug(String slug) {
        return slug != null && reservedSlugs.contains(slug);
    }

    public String normalizeName(String name) {
        return name == null ? null : name.strip();
    }

    public boolean isValidName(String name) {
        return name != null && !name.isEmpty() && name.length() <= NAME_MAX;
    }

    /** 빈 소개는 NULL로 저장한다. */
    public String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String value = description.strip();
        return value.isEmpty() ? null : value;
    }

    public boolean isValidDescription(String description) {
        return description == null || description.length() <= DESCRIPTION_MAX;
    }
}
