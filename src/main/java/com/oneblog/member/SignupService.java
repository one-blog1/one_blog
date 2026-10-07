package com.oneblog.member;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.AccessTokenService;
import com.oneblog.auth.AccessTokenService.SignupTicket;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.dto.SignupRequest;
import com.oneblog.verification.VerificationCode;
import com.oneblog.verification.VerificationCodeRepository;
import com.oneblog.verification.VerificationPurpose;

/**
 * 가입 완료 (USR-01 ②·③ 단계, FR-001~006).
 * 화면 단계를 건너뛰는 요청을 막기 위해 가입 티켓과 DB의 인증 기록을 다시 확인한다 (USR-02 구현 순서 5).
 */
@Service
public class SignupService {

    private static final String TICKET_INVALID_MESSAGE = "이메일 인증이 만료되었습니다. 처음부터 다시 시도해 주세요.";

    private final AccessTokenService accessTokenService;
    private final VerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final SignupPolicy signupPolicy;
    private final PasswordEncoder passwordEncoder;

    public SignupService(AccessTokenService accessTokenService, VerificationCodeRepository codeRepository,
            UserRepository userRepository, SignupPolicy signupPolicy, PasswordEncoder passwordEncoder) {
        this.accessTokenService = accessTokenService;
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.signupPolicy = signupPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void signup(String ticketToken, SignupRequest request) {
        SignupTicket ticket = accessTokenService.readSignupTicket(ticketToken)
                .orElseThrow(SignupService::ticketInvalid);
        VerificationCode verification = codeRepository.findById(ticket.verificationId())
                .filter(v -> v.getPurpose() == VerificationPurpose.SIGNUP)
                .filter(v -> v.getEmail().equals(ticket.email()))
                .filter(VerificationCode::isVerified)
                .filter(v -> !v.isUsed())
                .orElseThrow(SignupService::ticketInvalid);

        String name = signupPolicy.normalizeName(request.name());
        String nickname = signupPolicy.normalizeNickname(request.nickname());
        String phone = signupPolicy.normalizePhone(request.phone());
        validate(request, name, nickname, phone);

        if (signupPolicy.isReservedNickname(nickname) || userRepository.existsByNickname(nickname)) {
            throw nicknameUnavailable();
        }
        if (userRepository.existsByEmail(ticket.email())) {
            throw signupFailed();
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.createMember(ticket.email(), passwordEncoder.encode(request.password()),
                name, nickname, phone, now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 같은 닉네임·이메일로 가입한 경우
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains("uk_users_nickname")) {
                throw nicknameUnavailable();
            }
            throw signupFailed();
        }
        verification.markUsed(now);
    }

    private void validate(SignupRequest request, String name, String nickname, String phone) {
        List<com.oneblog.common.web.ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!signupPolicy.isValidPassword(request.password())) {
            errors.add(field("password", "비밀번호는 8~15자이며 영문, 숫자, 특수문자를 모두 포함해야 합니다."));
        } else if (!request.password().equals(request.passwordConfirm())) {
            errors.add(field("passwordConfirm", "비밀번호가 서로 다릅니다."));
        }
        if (!signupPolicy.isValidName(name)) {
            errors.add(field("name", "이름은 1~50자로 입력해 주세요."));
        }
        if (!signupPolicy.isValidNicknameFormat(nickname)) {
            errors.add(field("nickname", "닉네임은 2~12자의 한글, 영문, 숫자만 쓸 수 있습니다."));
        }
        if (!signupPolicy.isValidPhone(phone)) {
            errors.add(field("phone", "휴대전화 번호를 숫자 10~11자리로 입력해 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }

    private static com.oneblog.common.web.ErrorResponse.FieldError field(String name, String message) {
        return new com.oneblog.common.web.ErrorResponse.FieldError(name, message);
    }

    private static ApiException ticketInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SIGNUP_TICKET_INVALID", TICKET_INVALID_MESSAGE);
    }

    private static ApiException nicknameUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "NICKNAME_UNAVAILABLE", "사용할 수 없는 닉네임입니다.");
    }

    /** 이메일이 이미 가입된 경우. 가입 여부를 드러내지 않는 문구를 쓴다 (D-28). */
    private static ApiException signupFailed() {
        return new ApiException(HttpStatus.CONFLICT, "SIGNUP_FAILED",
                "가입을 완료할 수 없습니다. 처음부터 다시 시도해 주세요.");
    }
}
