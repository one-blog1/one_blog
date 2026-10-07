package com.oneblog.blog;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * application.yml의 app.blog.* (BLG-10, D-52, D-68, 6.5).
 * 제한값은 환경변수로 바꿀 수 있고, 바꾼 뒤에는 서버를 다시 시작한다.
 */
@ConfigurationProperties(prefix = "app.blog")
public record BlogProperties(Limit limit, List<String> reservedSlugs) {

    public BlogProperties {
        if (limit == null) {
            limit = new Limit(3, 5);
        }
        if (limit.publicLimit() < 1 || limit.publicLimit() > 5) {
            throw new IllegalStateException("app.blog.limit.public(BLOG_LIMIT_PUBLIC)은 1~5여야 합니다 (D-52).");
        }
        if (limit.privateLimit() < 1) {
            throw new IllegalStateException("app.blog.limit.private(BLOG_LIMIT_PRIVATE)는 1 이상이어야 합니다.");
        }
        reservedSlugs = reservedSlugs == null ? List.of() : List.copyOf(reservedSlugs);
    }

    /** 예약어를 정리(앞뒤 공백 제거, 소문자)한 집합. */
    public Set<String> reservedSlugSet() {
        return reservedSlugs.stream()
                .map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** yml의 public/private는 자바 예약어라 @Name으로 받는다. */
    public record Limit(
            @Name("public") @DefaultValue("3") int publicLimit,
            @Name("private") @DefaultValue("5") int privateLimit) {
    }
}
