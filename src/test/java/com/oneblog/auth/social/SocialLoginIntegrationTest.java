package com.oneblog.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MvcResult;

import com.oneblog.IntegrationTestSupport;
import com.oneblog.common.security.CookieNames;

import jakarta.servlet.http.Cookie;

/**
 * 소셜 로그인 (022, D-126). 실제 카카오·구글에는 접속하지 않는다: 사이트로 보내는 주소와 state 쿠키는 그대로 확인하고,
 * 돌아온 뒤의 처리는 사이트가 준 사용자 정보로 SocialLoginHandlers를 직접 불러 확인한다.
 * 테스트 설정(application-test.yml)은 카카오·구글만 켜고 네이버는 꺼 둔다.
 */
class SocialLoginIntegrationTest extends IntegrationTestSupport {

    private static final String GOOGLE_EMAIL = "gildong@gmail.com";

    @Autowired
    private SocialLoginHandlers handlers;

    @Autowired
    private SocialTickets tickets;

    private static OAuth2AuthenticationToken google(String sub, String email, boolean verified) {
        Map<String, Object> attributes = Map.of("sub", sub, "email", email, "email_verified", verified,
                "name", "홍길동", "given_name", "길동이");
        var authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
        return new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities, attributes, "sub"), authorities,
                "google");
    }

    private static OAuth2AuthenticationToken kakao(long id, String nickname) {
        Map<String, Object> attributes = Map.of("id", id,
                "kakao_account", Map.of("profile", Map.of("nickname", nickname)));
        var authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
        return new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities, attributes, "id"), authorities,
                "kakao");
    }

    /** 사이트에서 돌아온 것처럼 처리기를 부른다. */
    private MockHttpServletResponse callback(OAuth2AuthenticationToken token, Cookie... cookies) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/login/oauth2/code/" + token.getAuthorizedClientRegistrationId());
        if (cookies.length > 0) {
            request.setCookies(cookies);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        handlers.onAuthenticationSuccess(request, response, token);
        return response;
    }

    private static String socialSignupBody(String nickname) {
        return """
                {"name":"홍길동","nickname":"%s","phone":"010-1234-5678","agreeTerms":true,"agreePrivacy":true}
                """.formatted(nickname);
    }

    private Integer links(String email) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM social_accounts s JOIN users u ON u.id = s.user_id WHERE u.email = ?""",
                Integer.class, email);
    }

    @Test
    void 키가_있는_사이트만_로그인_단추로_알려_준다() throws Exception {
        mvc.perform(get("/api/auth/social/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$[0].loginUrl").value("/oauth2/authorization/kakao"))
                .andExpect(jsonPath("$[1].provider").value("GOOGLE"));
    }

    @Test
    void 사이트로_보낼_때_state를_서명한_쿠키에_두고_세션은_만들지_않는다() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().string("Location", containsString("client_id=test-kakao-id")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", containsString("login/oauth2/code/kakao")))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(SocialAuthorizationRequestCookies.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/login/oauth2");
        // 로그인 요청·저장한 요청을 세션에 두지 않는다. 테스트 도구의 csrf()가 CSRF 저장소를 세션용으로 바꿔 두므로
        // 세션 유무가 아니라 OAuth2·요청 저장 값이 없는지 본다 (운영은 쿠키 CSRF 저장소)
        var session = result.getRequest().getSession(false);
        if (session != null) {
            assertThat(java.util.Collections.list(session.getAttributeNames()))
                    .noneMatch(name -> name.contains("OAuth2") || name.contains("SAVED_REQUEST"));
        }

        // 꺼진 사이트(네이버)로는 보내지 않는다
        mvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(result2 -> assertThat(result2.getResponse().getStatus()).isNotEqualTo(302));
    }

    @Test
    void 요청_쿠키_없이_돌아오면_로그인_화면으로_실패를_알린다() throws Exception {
        mvc.perform(get("/login/oauth2/code/kakao").param("code", "abc").param("state", "forged"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login.html?social_error=failed"));
    }

    @Test
    void 사용자가_취소하면_취소_문구로_돌아간다() throws Exception {
        MvcResult start = mvc.perform(get("/oauth2/authorization/kakao")).andReturn();
        Cookie cookie = start.getResponse().getCookie(SocialAuthorizationRequestCookies.COOKIE);
        String location = start.getResponse().getHeader("Location");
        String state = java.net.URLDecoder.decode(location.replaceAll(".*[?&]state=([^&]+).*", "$1"),
                java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(get("/login/oauth2/code/kakao").cookie(cookie)
                        .param("error", "access_denied").param("state", state))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login.html?social_error=canceled"));
    }

    @Test
    void 구글_인증_이메일이면_정보만_넣고_가입되며_다음부터_바로_로그인한다() throws Exception {
        MockHttpServletResponse first = callback(google("g-100", GOOGLE_EMAIL, true));
        assertThat(first.getRedirectedUrl()).isEqualTo("/social-signup.html");
        Cookie pending = first.getCookie(SocialCookies.PENDING);
        assertThat(pending).isNotNull();
        assertThat(pending.isHttpOnly()).isTrue();
        assertThat(first.getCookie(CookieNames.ACCESS_TOKEN)).isNull();

        mvc.perform(get("/api/auth/social/pending").cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.email").value("gi***@gmail.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.emailInUse").value(false))
                .andExpect(jsonPath("$.name").value("홍*동"))
                .andExpect(jsonPath("$.needName").value(false))
                .andExpect(jsonPath("$.needPhone").value(true))
                .andExpect(jsonPath("$.nickname").value("길동이"));

        MvcResult signup = mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON).content(socialSignupBody("길동이")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("길동이"))
                .andReturn();
        Cookie access = signup.getResponse().getCookie(CookieNames.ACCESS_TOKEN);
        assertThat(access).isNotNull();
        mvc.perform(get("/api/me").cookie(access)).andExpect(jsonPath("$.nickname").value("길동이"));

        Map<String, Object> user = jdbc.queryForMap("SELECT password_hash, terms_agreed_at FROM users WHERE email = ?",
                GOOGLE_EMAIL);
        assertThat(user.get("password_hash")).isEqualTo("!SOCIAL");
        assertThat(user.get("terms_agreed_at")).isNotNull();
        assertThat(links(GOOGLE_EMAIL)).isEqualTo(1);

        // 같은 구글 계정으로 다시 오면 바로 로그인
        MockHttpServletResponse again = callback(google("g-100", GOOGLE_EMAIL, true));
        assertThat(again.getRedirectedUrl()).isEqualTo("/");
        assertThat(again.getCookie(CookieNames.ACCESS_TOKEN)).isNotNull();

        // 비밀번호가 없으니 비밀번호 로그인은 안 된다
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + GOOGLE_EMAIL + "\",\"password\":\"!SOCIAL\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 카카오는_이메일을_인증번호로_확인해야_가입된다() throws Exception {
        Cookie pending = callback(kakao(4242L, "카카오친구")).getCookie(SocialCookies.PENDING);
        mvc.perform(get("/api/auth/social/pending").cookie(pending))
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.needName").value(true))
                .andExpect(jsonPath("$.needPhone").value(true))
                .andExpect(jsonPath("$.nickname").value("카카오친구"));

        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON).content(socialSignupBody("카카오친구")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String email = "kakao-user@example.com";
        Cookie ticket = verifyCode(email, requestSignupCode(email));
        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending, ticket)
                        .contentType(MediaType.APPLICATION_JSON).content(socialSignupBody("카카오친구")))
                .andExpect(status().isCreated());
        assertThat(links(email)).isEqualTo(1);
        // 인증번호는 한 번만 쓴다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_codes WHERE email = ? AND used_at IS NULL",
                Integer.class, email)).isZero();
    }

    @Test
    void 같은_이메일의_기존_회원에는_저절로_붙지_않고_비밀번호로_로그인해야_연결된다() throws Exception {
        signUp(GOOGLE_EMAIL, "기존회원");
        Cookie pending = callback(google("g-200", GOOGLE_EMAIL, true)).getCookie(SocialCookies.PENDING);

        mvc.perform(get("/api/auth/social/pending").cookie(pending))
                .andExpect(jsonPath("$.emailInUse").value(true))
                .andExpect(jsonPath("$.email").value("gi***@gmail.com"));
        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON).content(socialSignupBody("새닉네임")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        assertThat(links(GOOGLE_EMAIL)).isZero();

        mvc.perform(post("/api/auth/social/link").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + GOOGLE_EMAIL + "\",\"password\":\"Wrong123!\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(links(GOOGLE_EMAIL)).isZero();

        // "이미 있음" 화면은 비밀번호만 보낸다. 이메일은 서버가 받은 값을 쓴다 (027)
        MvcResult linked = mvc.perform(post("/api/auth/social/link").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("기존회원"))
                .andReturn();
        assertThat(linked.getResponse().getCookie(CookieNames.ACCESS_TOKEN)).isNotNull();
        assertThat(links(GOOGLE_EMAIL)).isEqualTo(1);
        assertThat(callback(google("g-200", GOOGLE_EMAIL, true)).getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void 가입_대기_쿠키가_없거나_고쳐지면_가입할_수_없다() throws Exception {
        mvc.perform(get("/api/auth/social/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_PENDING_EXPIRED"));
        Cookie forged = new Cookie(SocialCookies.PENDING, "eyJhbGciOiJub25lIn0.eyJ0eXAiOiJTT0NJQUwifQ.");
        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(forged)
                        .contentType(MediaType.APPLICATION_JSON).content(socialSignupBody("아무개")))
                .andExpect(status().isUnauthorized());
        // 가입 대기 값은 로그인 쿠키로 쓸 수 없다
        String pending = tickets.issuePending(new SocialTickets.Pending(SocialProvider.KAKAO, "1", null, false,
                null, null, null));
        mvc.perform(get("/api/me").cookie(new Cookie(CookieNames.ACCESS_TOKEN, pending)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내_정보에서_연결하고_끊는다() throws Exception {
        Cookie access = signUpAndLogin("member@example.com", "회원하나");
        mvc.perform(get("/api/me/social").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.providers", hasSize(2)))
                .andExpect(jsonPath("$.providers[0].linked").value(false));

        MvcResult start = mvc.perform(post("/api/me/social/kakao/link").with(csrf()).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/oauth2/authorization/kakao"))
                .andReturn();
        Cookie link = start.getResponse().getCookie(SocialCookies.LINK);
        assertThat(link.getPath()).isEqualTo("/login/oauth2");

        MockHttpServletResponse back = callback(kakao(777L, "아무개"), link);
        assertThat(back.getRedirectedUrl()).isEqualTo("/account.html?social=linked");
        assertThat(back.getCookie(SocialCookies.LINK).getMaxAge()).isZero();
        mvc.perform(get("/api/me/social").cookie(access))
                .andExpect(jsonPath("$.providers[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.providers[0].linked").value(true));

        // 이미 다른 회원에 연결된 카카오 계정은 붙일 수 없다
        Cookie other = signUpAndLogin("other@example.com", "회원둘");
        Cookie otherLink = mvc.perform(post("/api/me/social/kakao/link").with(csrf()).cookie(other))
                .andReturn().getResponse().getCookie(SocialCookies.LINK);
        assertThat(callback(kakao(777L, "아무개"), otherLink).getRedirectedUrl())
                .isEqualTo("/account.html?social_error=taken");

        // CSRF 없이는 연결 표시를 받을 수 없다
        mvc.perform(post("/api/me/social/google/link").cookie(access)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/me/social/kakao").with(csrf()).cookie(access)).andExpect(status().isNoContent());
        assertThat(links("member@example.com")).isZero();
    }

    @Test
    void 비밀번호_없는_회원은_마지막_연결을_끊거나_재확인할_수_없다() throws Exception {
        Cookie pending = callback(google("g-300", GOOGLE_EMAIL, true)).getCookie(SocialCookies.PENDING);
        Cookie access = mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON).content(socialSignupBody("소셜회원")))
                .andReturn().getResponse().getCookie(CookieNames.ACCESS_TOKEN);

        mvc.perform(get("/api/me/social").cookie(access)).andExpect(jsonPath("$.hasPassword").value(false));
        mvc.perform(delete("/api/me/social/google").with(csrf()).cookie(access))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD"));
        mvc.perform(post("/api/me/reauth").with(csrf()).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"!SOCIAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"!SOCIAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
    }

    @Test
    void 탈퇴하면_연결이_지워져_같은_소셜_계정으로_다시_가입할_수_있다() throws Exception {
        Cookie access = signUpAndLogin("leaver@example.com", "떠날회원");
        Cookie link = mvc.perform(post("/api/me/social/google/link").with(csrf()).cookie(access))
                .andReturn().getResponse().getCookie(SocialCookies.LINK);
        callback(google("g-400", "leaver@gmail.com", true), link);
        assertThat(links("leaver@example.com")).isEqualTo(1);

        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_accounts", Integer.class)).isZero();
        assertThat(callback(google("g-400", "leaver@gmail.com", true)).getRedirectedUrl())
                .isEqualTo("/social-signup.html");
    }

    /** 네이버처럼 이름·휴대전화까지 준 사이트. 테스트 설정은 네이버를 꺼 두므로 구글 가입 대기 값에 휴대전화를 넣어 흉내 낸다. */
    private Cookie pendingWithPhone(String pid, String email) {
        return new Cookie(SocialCookies.PENDING, tickets.issuePending(new SocialTickets.Pending(
                SocialProvider.GOOGLE, pid, email, true, "홍길동", "길동", "01012345678")));
    }

    @Test
    void 사이트가_준_이름과_휴대전화는_가려서_보여_주고_그_값으로_가입한다() throws Exception {
        String email = "naverlike@example.com";
        Cookie pending = pendingWithPhone("g-500", email);
        mvc.perform(get("/api/auth/social/pending").cookie(pending))
                .andExpect(jsonPath("$.email").value("na***@example.com"))
                .andExpect(jsonPath("$.name").value("홍*동"))
                .andExpect(jsonPath("$.needName").value(false))
                .andExpect(jsonPath("$.phone").value("010-****-5678"))
                .andExpect(jsonPath("$.needPhone").value(false))
                .andExpect(jsonPath("$.nickname").value("길동"));

        // 화면에서 다른 값을 보내도 사이트가 준 이름·휴대전화로 저장한다. 닉네임은 바꾼 값으로
        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"딴사람","nickname":"바꾼닉네임","phone":"01099998888","agreeTerms":true,"agreePrivacy":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("바꾼닉네임"));
        Map<String, Object> user = jdbc.queryForMap("SELECT name, phone, nickname FROM users WHERE email = ?", email);
        assertThat(user.get("name")).isEqualTo("홍길동");
        assertThat(user.get("phone")).isEqualTo("01012345678");
        assertThat(user.get("nickname")).isEqualTo("바꾼닉네임");
    }

    @Test
    void 받은_값이_있으면_이름과_휴대전화를_비워_보내도_가입된다() throws Exception {
        Cookie pending = pendingWithPhone("g-501", "quick@example.com");
        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","nickname":"길동","phone":"","agreeTerms":true,"agreePrivacy":true}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void 빈칸이나_약관_동의가_없으면_가입되지_않는다() throws Exception {
        // 구글은 휴대전화를 주지 않는다
        Cookie pending = callback(google("g-600", "nophone@gmail.com", true)).getCookie(SocialCookies.PENDING);
        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","nickname":"폰없음","phone":"","agreeTerms":true,"agreePrivacy":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));

        mvc.perform(post("/api/auth/signup/social").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"폰없음","phone":"01012345678","agreeTerms":true,"agreePrivacy":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("agreePrivacy"));
        assertThat(links("nophone@gmail.com")).isZero();
    }

    @Test
    void 받은_닉네임이_이미_있으면_숫자를_붙여_채운다() throws Exception {
        signUp("first@example.com", "길동이");
        Cookie pending = callback(google("g-700", "second@gmail.com", true)).getCookie(SocialCookies.PENDING);
        mvc.perform(get("/api/auth/social/pending").cookie(pending))
                .andExpect(jsonPath("$.nickname").value("길동이2"));
    }

    @Test
    void 믿을_수_없는_이메일이면_비밀번호만으로는_연결할_수_없다() throws Exception {
        Cookie pending = callback(google("g-800", "unverified@gmail.com", false)).getCookie(SocialCookies.PENDING);
        mvc.perform(get("/api/auth/social/pending").cookie(pending))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(post("/api/auth/social/link").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void 가입을_그만두면_가입_대기_정보가_지워진다() throws Exception {
        // 022: 가입 화면에서 그만두기 (029 US6)
        Cookie pending = callback(google("g-900", "quit@gmail.com", true)).getCookie(SocialCookies.PENDING);
        assertThat(pending).isNotNull();
        mvc.perform(get("/api/auth/social/pending").cookie(pending)).andExpect(status().isOk());

        MvcResult canceled = mvc.perform(post("/api/auth/social/cancel").with(csrf()).cookie(pending))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cleared = canceled.getResponse().getCookie(SocialCookies.PENDING);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'quit@gmail.com'", Integer.class))
                .isZero();
    }
}
