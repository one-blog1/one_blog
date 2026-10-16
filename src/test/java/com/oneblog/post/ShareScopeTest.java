package com.oneblog.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.oneblog.blog.BlogVisibility;

/** 공유 창에 보일 항목을 정하는 공유 범위 (031, BRD-07, 6.2). 숨긴 블로그·글은 멤버끼리만. */
class ShareScopeTest {

    @ParameterizedTest(name = "{0}, 블로그 숨김 {1}, 글 숨김 {2} → {3}")
    @CsvSource({
            "PUBLIC, false, false, PUBLIC",
            "UNLISTED, false, false, LINK",
            "PRIVATE, false, false, MEMBERS",
            "PUBLIC, true, false, MEMBERS",
            "UNLISTED, true, false, MEMBERS",
            "PRIVATE, true, false, MEMBERS",
            "PUBLIC, false, true, MEMBERS",
            "UNLISTED, false, true, MEMBERS",
            "PRIVATE, false, true, MEMBERS",
            "PUBLIC, true, true, MEMBERS"
    })
    void 공개_범위와_숨김으로_공유_범위를_정한다(BlogVisibility visibility, boolean blogHidden, boolean postHidden,
            ShareScope expected) {
        assertThat(ShareScope.of(visibility, blogHidden, postHidden)).isEqualTo(expected);
    }
}
