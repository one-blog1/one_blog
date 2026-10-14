package com.oneblog.auth.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.oneblog.member.SignupPolicy;

/** 소셜 가입 화면에 미리 채울 닉네임 (027). 기본은 사이트 닉네임 그대로, 쓸 수 없을 때만 고친다. */
class SocialNicknamesTest {

    private final SocialNicknames nicknames = new SocialNicknames(new SignupPolicy(), null);

    private String suggest(String hint, String... taken) {
        Set<String> used = Set.of(taken);
        return nicknames.suggest(hint, used::contains);
    }

    @Test
    void 쓸_수_있으면_사이트_닉네임을_그대로_쓴다() {
        assertThat(suggest("길동")).isEqualTo("길동");
        assertThat(suggest(" 네이버친구 ")).isEqualTo("네이버친구");
    }

    @Test
    void 쓸_수_없는_글자는_빼고_12자로_자른다() {
        assertThat(suggest("길 동!")).isEqualTo("길동");
        assertThat(suggest("가나다라마바사아자차카타파하")).isEqualTo("가나다라마바사아자차카타");
    }

    @Test
    void 너무_짧거나_금지어이거나_없으면_회원과_숫자로_정한다() {
        assertThat(suggest("길")).matches("^회원[0-9]{4}$");
        assertThat(suggest("관리자")).matches("^회원[0-9]{4}$");
        assertThat(suggest("!!!")).matches("^회원[0-9]{4}$");
        assertThat(suggest(null)).matches("^회원[0-9]{4}$");
    }

    @Test
    void 이미_있으면_뒤에_숫자를_붙인다() {
        assertThat(suggest("길동", "길동")).isEqualTo("길동2");
        assertThat(suggest("길동", "길동", "길동2")).isEqualTo("길동3");
    }

    @Test
    void 숫자를_붙여_12자를_넘으면_앞을_줄인다() {
        assertThat(suggest("가나다라마바사아자차카타", "가나다라마바사아자차카타"))
                .isEqualTo("가나다라마바사아자차카2");
    }
}
