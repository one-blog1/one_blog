package com.oneblog.auth.social;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.AccessTokenService;
import com.oneblog.auth.AccessTokenService.SignupTicket;
import com.oneblog.auth.AuthService;
import com.oneblog.auth.AuthService.LoginResult;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.SignupPolicy;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.verification.VerificationCode;
import com.oneblog.verification.VerificationCodeRepository;
import com.oneblog.verification.VerificationPurpose;

/**
 * 소셜 로그인·가입·연결 (USR-09, D-126).
 * - 연결된 소셜 계정이면 바로 로그인한다
 * - 처음이면 가입 화면으로 보낸다. 이메일은 사이트가 인증한 것만 믿고, 아니면 우리 인증번호로 확인한다
 * - 같은 이메일의 기존 회원에 저절로 붙이지 않는다. 비밀번호로 로그인해야 연결된다
 */
@Service
public class SocialLoginService {

    private final SocialAccountRepository socialRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AccessTokenService accessTokenService;
    private final VerificationCodeRepository codeRepository;
    private final SignupPolicy signupPolicy;
    private final SocialClientRegistrations registrations;

    public SocialLoginService(SocialAccountRepository socialRepository, UserRepository userRepository,
            AuthService authService, AccessTokenService accessTokenService,
            VerificationCodeRepository codeRepository, SignupPolicy signupPolicy,
            SocialClientRegistrations registrations) {
        this.socialRepository = socialRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.accessTokenService = accessTokenService;
        this.codeRepository = codeRepository;
        this.signupPolicy = signupPolicy;
        this.registrations = registrations;
    }

    /** 사이트에서 돌아온 결과. login이 있으면 로그인, 없으면 가입 대기(pending)로 가입 화면에 간다. */
    public record CallbackResult(LoginResult login, SocialTickets.Pending pending, String error) {

        static CallbackResult loggedIn(LoginResult login) {
            return new CallbackResult(login, null, null);
        }

        static CallbackResult needsSignup(SocialTickets.Pending pending) {
            return new CallbackResult(null, pending, null);
        }

        static CallbackResult failed(String error) {
            return new CallbackResult(null, null, error);
        }
    }

    /** 사이트에서 돌아왔을 때: 연결된 회원이면 로그인, 아니면 가입 대기. */
    @Transactional
    public CallbackResult onLogin(SocialTickets.Pending profile) {
        Optional<SocialAccount> linked = socialRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (linked.isEmpty()) {
            return CallbackResult.needsSignup(profile);
        }
        Optional<User> user = userRepository.findById(linked.get().getUserId())
                .filter(User::isActive)
                .filter(u -> u.getRole() == UserRole.USER);
        if (user.isEmpty()) {
            return CallbackResult.failed("inactive");
        }
        return CallbackResult.loggedIn(authService.issueLogin(user.get(), false));
    }

    /**
     * 로그인한 회원이 내 정보에서 연결하고 돌아왔을 때. 오류 이름(taken, already, inactive)이나 null(성공)을 돌려준다.
     */
    @Transactional
    public String onLink(Long userId, SocialTickets.Pending profile) {
        User user = userRepository.findById(userId).filter(User::isActive)
                .filter(u -> u.getRole() == UserRole.USER).orElse(null);
        if (user == null) {
            return "inactive";
        }
        Optional<SocialAccount> existing = socialRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (existing.isPresent()) {
            return existing.get().getUserId().equals(userId) ? null : "taken";
        }
        if (socialRepository.existsByUserIdAndProvider(userId, profile.provider())) {
            return "already";
        }
        return save(userId, profile) ? null : "taken";
    }

    /** 가입 화면에 보여 줄 값. 이메일이 이미 가입됐는지는 사이트가 이메일을 인증해 준 경우에만 알려 준다. */
    public record PendingView(String provider, String providerLabel, String email, boolean emailVerified,
            boolean emailInUse, String name, String nickname) {
    }

    @Transactional(readOnly = true)
    public PendingView view(SocialTickets.Pending pending) {
        String email = pending.email() == null ? null : signupPolicy.normalizeEmail(pending.email());
        boolean inUse = pending.emailVerified() && email != null && userRepository.existsByEmail(email);
        String name = pending.name() == null ? null : signupPolicy.normalizeName(pending.name());
        return new PendingView(pending.provider().name(), pending.provider().label(), email,
                pending.emailVerified(), inUse, name != null && signupPolicy.isValidName(name) ? name : null,
                suggestNickname(pending.nickname()));
    }

    /** 사이트의 닉네임을 쓸 수 있으면 그대로 제안한다. 형식이 맞지 않거나 이미 있으면 비워 둔다. */
    private String suggestNickname(String hint) {
        if (hint == null) {
            return null;
        }
        String nickname = signupPolicy.normalizeNickname(hint);
        if (!signupPolicy.isValidNicknameFormat(nickname) || signupPolicy.isReservedNickname(nickname)
                || userRepository.existsByNickname(nickname)) {
            return null;
        }
        return nickname;
    }

    public record SocialSignupRequest(String name, String nickname, String phone, Boolean agreeTerms,
            Boolean agreePrivacy) {
    }

    /**
     * 소셜 계정으로 가입하고 바로 로그인한다. 비밀번호는 만들지 않는다 (나중에 비밀번호 찾기로 만들 수 있다).
     * 사이트가 인증한 이메일이 없으면 이메일 인증을 마친 가입 티켓(SIGNUP_TICKET)이 있어야 한다.
     */
    @Transactional
    public LoginResult signup(SocialTickets.Pending pending, String signupTicketToken, SocialSignupRequest request) {
        if (!registrations.isEnabled(pending.provider())) {
            throw pendingExpired();
        }
        String email;
        VerificationCode verification = null;
        if (pending.emailVerified() && pending.email() != null) {
            email = signupPolicy.normalizeEmail(pending.email());
        } else {
            SignupTicket ticket = accessTokenService.readSignupTicket(signupTicketToken)
                    .orElseThrow(SocialLoginService::emailNotVerified);
            verification = codeRepository.findById(ticket.verificationId())
                    .filter(v -> v.getPurpose() == VerificationPurpose.SIGNUP)
                    .filter(v -> v.getEmail().equals(ticket.email()))
                    .filter(VerificationCode::isVerified)
                    .filter(v -> !v.isUsed())
                    .orElseThrow(SocialLoginService::emailNotVerified);
            email = ticket.email();
        }

        String name = signupPolicy.normalizeName(request.name());
        String nickname = signupPolicy.normalizeNickname(request.nickname());
        String phone = signupPolicy.normalizePhone(request.phone());
        validate(request, name, nickname, phone);
        if (signupPolicy.isReservedNickname(nickname) || userRepository.existsByNickname(nickname)) {
            throw nicknameUnavailable();
        }
        if (userRepository.existsByEmail(email)) {
            throw emailInUse();
        }
        if (socialRepository.findByProviderAndProviderUserId(pending.provider(), pending.providerUserId())
                .isPresent()) {
            throw alreadyLinked();
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.createSocialMember(email, name, nickname, phone, now);
        try {
            userRepository.saveAndFlush(user);
            socialRepository.saveAndFlush(SocialAccount.link(user.getId(), pending.provider(),
                    pending.providerUserId()));
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains("uk_users_nickname")) {
                throw nicknameUnavailable();
            }
            if (message.contains("uk_users_email")) {
                throw emailInUse();
            }
            throw alreadyLinked();
        }
        if (verification != null) {
            verification.markUsed(now);
        }
        return authService.issueLogin(user, false);
    }

    /** 이미 가입한 회원이 비밀번호로 로그인하면서 소셜 계정을 연결한다. 로그인 잠금·사람 확인은 로그인과 같다. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult linkWithPassword(SocialTickets.Pending pending, String email, String password, String ip,
            String captchaToken) {
        User user = authService.authenticateMember(email, password, ip, captchaToken);
        Optional<SocialAccount> existing = socialRepository
                .findByProviderAndProviderUserId(pending.provider(), pending.providerUserId());
        if (existing.isPresent() && !existing.get().getUserId().equals(user.getId())) {
            throw alreadyLinked();
        }
        if (existing.isEmpty()) {
            if (socialRepository.existsByUserIdAndProvider(user.getId(), pending.provider())) {
                throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_ALREADY_LINKED",
                        "이 계정에는 이미 다른 " + pending.provider().label()
                                + " 계정이 연결되어 있습니다. 내 정보에서 연결을 끊은 뒤 다시 해 주세요.");
            }
            if (!save(user.getId(), pending)) {
                throw alreadyLinked();
            }
        }
        return authService.issueLogin(user, false);
    }

    public record LinkedProvider(String provider, String label, boolean enabled, boolean linked,
            LocalDateTime linkedAt) {
    }

    public record MySocial(boolean hasPassword, List<LinkedProvider> providers) {
    }

    /** 내 정보: 사이트별 연결 상태. 키가 없어 꺼진 사이트도 이미 연결돼 있으면 보여 준다(끊을 수 있게). */
    @Transactional(readOnly = true)
    public MySocial mine(Long userId) {
        User user = activeMember(userId);
        List<SocialAccount> links = socialRepository.findByUserIdOrderByIdAsc(userId);
        List<LinkedProvider> result = new ArrayList<>();
        for (SocialProvider provider : SocialProvider.values()) {
            Optional<SocialAccount> link = links.stream().filter(l -> l.getProvider() == provider).findFirst();
            boolean enabled = registrations.isEnabled(provider);
            if (enabled || link.isPresent()) {
                result.add(new LinkedProvider(provider.name(), provider.label(), enabled, link.isPresent(),
                        link.map(SocialAccount::getCreatedAt).orElse(null)));
            }
        }
        return new MySocial(user.hasPassword(), result);
    }

    /** 연결을 끊는다. 비밀번호가 없고 마지막 연결이면 로그인할 방법이 없어지므로 막는다. */
    @Transactional
    public void unlink(Long userId, SocialProvider provider) {
        User user = userRepository.findForUpdateById(userId).filter(User::isActive)
                .orElseThrow(SocialLoginService::unauthenticated);
        List<SocialAccount> links = socialRepository.findByUserIdOrderByIdAsc(userId);
        if (links.stream().noneMatch(l -> l.getProvider() == provider)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_LINKED", "연결되지 않은 계정입니다.");
        }
        if (!user.hasPassword() && links.size() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_LOGIN_METHOD",
                    "마지막 로그인 방법이라 끊을 수 없습니다. 비밀번호 찾기로 비밀번호를 먼저 만들어 주세요.");
        }
        socialRepository.deleteLink(userId, provider);
    }

    /** 내 정보에서 "연결하기"를 누를 수 있는지 확인한다. */
    @Transactional(readOnly = true)
    public void checkCanStartLink(Long userId, SocialProvider provider) {
        activeMember(userId);
        if (!registrations.isEnabled(provider)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_DISABLED", "지금은 쓸 수 없는 로그인 방법입니다.");
        }
        if (socialRepository.existsByUserIdAndProvider(userId, provider)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_ALREADY_LINKED", "이미 연결된 계정입니다.");
        }
    }

    private User activeMember(Long userId) {
        return userRepository.findById(userId).filter(User::isActive)
                .filter(u -> u.getRole() == UserRole.USER)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "MEMBER_ONLY",
                        "회원 계정만 소셜 로그인을 쓸 수 있습니다."));
    }

    /** 같은 소셜 계정이 거의 동시에 다른 회원에 붙으면 DB 유일 조건에 걸린다. 그때 false. */
    private boolean save(Long userId, SocialTickets.Pending profile) {
        try {
            socialRepository.saveAndFlush(SocialAccount.link(userId, profile.provider(), profile.providerUserId()));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private void validate(SocialSignupRequest request, String name, String nickname, String phone) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!signupPolicy.isValidName(name)) {
            errors.add(new ErrorResponse.FieldError("name", "이름은 1~50자로 입력해 주세요."));
        }
        if (!signupPolicy.isValidNicknameFormat(nickname)) {
            errors.add(new ErrorResponse.FieldError("nickname", "닉네임은 2~12자의 한글, 영문, 숫자만 쓸 수 있습니다."));
        }
        if (!signupPolicy.isValidPhone(phone)) {
            errors.add(new ErrorResponse.FieldError("phone", "휴대전화 번호를 숫자 10~11자리로 입력해 주세요."));
        }
        if (!Boolean.TRUE.equals(request.agreeTerms())) {
            errors.add(new ErrorResponse.FieldError("agreeTerms", "이용약관에 동의해 주세요."));
        }
        if (!Boolean.TRUE.equals(request.agreePrivacy())) {
            errors.add(new ErrorResponse.FieldError("agreePrivacy", "개인정보 수집·이용에 동의해 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }

    static ApiException pendingExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SOCIAL_PENDING_EXPIRED",
                "소셜 로그인 시간이 지났습니다. 로그인 화면에서 다시 시도해 주세요.");
    }

    private static ApiException emailNotVerified() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED", "이메일 인증을 먼저 해 주세요.");
    }

    private static ApiException nicknameUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "NICKNAME_UNAVAILABLE", "사용할 수 없는 닉네임입니다.");
    }

    /** 이메일을 인증한 사람에게만 나오는 문구라 가입 여부를 알려 줘도 된다. */
    private static ApiException emailInUse() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE",
                "이미 이 이메일로 가입한 계정이 있습니다. 아래에서 비밀번호로 로그인하면 연결됩니다.");
    }

    private static ApiException alreadyLinked() {
        return new ApiException(HttpStatus.CONFLICT, "SOCIAL_ALREADY_LINKED",
                "이 소셜 계정은 이미 다른 회원에 연결되어 있습니다.");
    }

    private static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
    }
}
