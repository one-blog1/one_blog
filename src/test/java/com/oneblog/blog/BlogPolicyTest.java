package com.oneblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 블로그 주소·이름 규칙 (6.5, research R2). */
class BlogPolicyTest {

    private final BlogPolicy policy = new BlogPolicy(
            new BlogProperties(new BlogProperties.Limit(3, 5), List.of("main", " Admin ", "api", "login")));

    @Test
    void 주소는_소문자로_정리한다() {
        assertThat(policy.normalizeSlug("  My-Blog ")).isEqualTo("my-blog");
    }

    @Test
    void 주소_형식() {
        assertThat(policy.isValidSlugFormat("abc")).isTrue();
        assertThat(policy.isValidSlugFormat("my-blog-2")).isTrue();
        assertThat(policy.isValidSlugFormat("a".repeat(30))).isTrue();
        assertThat(policy.isValidSlugFormat("ab")).isFalse();
        assertThat(policy.isValidSlugFormat("a".repeat(31))).isFalse();
        assertThat(policy.isValidSlugFormat("-abc")).isFalse();
        assertThat(policy.isValidSlugFormat("abc-")).isFalse();
        assertThat(policy.isValidSlugFormat("a--b")).isFalse();
        assertThat(policy.isValidSlugFormat("a_b")).isFalse();
        assertThat(policy.isValidSlugFormat("ABC")).isFalse();
        assertThat(policy.isValidSlugFormat("a b")).isFalse();
        assertThat(policy.isValidSlugFormat("../x")).isFalse();
        assertThat(policy.isValidSlugFormat(null)).isFalse();
    }

    @Test
    void 예약어는_설정값을_정리해서_비교한다() {
        assertThat(policy.isReservedSlug("admin")).isTrue();
        assertThat(policy.isReservedSlug("main")).isTrue();
        assertThat(policy.isReservedSlug("mains")).isFalse();
    }

    @Test
    void 이름과_소개() {
        assertThat(policy.normalizeName("  제주  ")).isEqualTo("제주");
        assertThat(policy.isValidName("")).isFalse();
        assertThat(policy.isValidName("가".repeat(50))).isTrue();
        assertThat(policy.isValidName("가".repeat(51))).isFalse();
        assertThat(policy.normalizeDescription("   ")).isNull();
        assertThat(policy.isValidDescription(null)).isTrue();
        assertThat(policy.isValidDescription("가".repeat(501))).isFalse();
    }
}
