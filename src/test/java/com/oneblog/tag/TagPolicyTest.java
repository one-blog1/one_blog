package com.oneblog.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.oneblog.member.ValidationFailedException;

/** 태그 정리 규칙 (6.4). */
class TagPolicyTest {

    private final TagPolicy policy = new TagPolicy();

    @Test
    void 앞의_샵과_공백을_지우고_가운데_공백은_밑줄로_영문은_소문자로() {
        assertThat(policy.normalize("#맛집")).isEqualTo("맛집");
        assertThat(policy.normalize("  ##서울   여행 ")).isEqualTo("서울_여행");
        assertThat(policy.normalize("Java")).isEqualTo("java");
        assertThat(policy.normalize("# spring boot")).isEqualTo("spring_boot");
    }

    @Test
    void 허용_문자와_길이() {
        assertThat(policy.isValid("한글_abc_123")).isTrue();
        assertThat(policy.isValid("a".repeat(20))).isTrue();
        assertThat(policy.isValid("a".repeat(21))).isFalse();
        assertThat(policy.isValid("")).isFalse();
        assertThat(policy.isValid("맛집!")).isFalse();
        assertThat(policy.isValid("c++")).isFalse();
        assertThat(policy.isValid("<script>")).isFalse();
    }

    @Test
    void 같은_태그는_합치고_입력_순서를_지킨다() {
        assertThat(policy.normalizeAll(List.of("Java", "#맛집", "java", " 맛집 "), "tags"))
                .containsExactly("java", "맛집");
        assertThat(policy.normalizeAll(null, "tags")).isEmpty();
    }

    @Test
    void 합친_뒤_10개를_넘으면_거부() {
        List<String> eleven = List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "a11");
        assertThatThrownBy(() -> policy.normalizeAll(eleven, "tags"))
                .isInstanceOf(ValidationFailedException.class);
        List<String> elevenWithDup = List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "A1");
        assertThat(policy.normalizeAll(elevenWithDup, "tags")).hasSize(10);
    }

    @Test
    void 틀린_태그는_몇_번째인지_알려준다() {
        assertThatThrownBy(() -> policy.normalizeAll(List.of("좋아요", "", "맛집!"), "tags"))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(e.getFieldErrors())
                        .extracting(f -> f.field()).containsExactly("tags[1]", "tags[2]"));
    }
}
