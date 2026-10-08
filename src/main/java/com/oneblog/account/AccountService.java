package com.oneblog.account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.RefreshTokenRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.text.Masking;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.Times;
import com.oneblog.file.FilePurpose;
import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.member.SignupPolicy;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;

/**
 * 회원정보 수정 (USR-07): 닉네임·프로필 사진·전화번호·소개·비밀번호. 본인만, 이메일·이름은 못 바꾼다.
 * 비밀번호를 바꾸면 지금 기기를 뺀 다른 기기의 로그인을 모두 끝낸다 (SEC-04).
 * 수정 전 비밀번호 재확인(D-102)은 {@link ReauthService}가 하고 컨트롤러가 검사한다.
 * 관리자 계정은 프로필이 없다 (D-90).
 */
@Service
public class AccountService {

    public static final int BIO_MAX = 300;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StoredFileRepository fileRepository;
    private final SignupPolicy policy;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            StoredFileRepository fileRepository, SignupPolicy policy, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.fileRepository = fileRepository;
        this.policy = policy;
        this.passwordEncoder = passwordEncoder;
    }

    public record AccountResponse(String email, String name, String nickname, String phone, String profileImageUrl,
            String bio, java.time.OffsetDateTime createdAt) {
    }

    public record ProfileUpdateRequest(String nickname, String phone, String bio, Long profileFileId,
            Boolean removeProfileImage) {
    }

    /** 현재 비밀번호는 재확인(D-102)으로 대신하므로 받지 않는다. */
    public record PasswordChangeRequest(String newPassword, String newPasswordConfirm) {

        @Override
        public String toString() {
            return "PasswordChangeRequest[***]";
        }
    }

    /** 내 정보. 본인 화면이라 가리지 않은 값을 준다 (전화번호는 보기 좋게 하이픈). */
    @Transactional(readOnly = true)
    public AccountResponse account(AuthenticatedUser principal) {
        User user = member(principal);
        return new AccountResponse(user.getEmail(), user.getName(), user.getNickname(),
                Masking.formatPhone(user.getPhone()), user.getProfileImageUrl(), user.getBio(),
                Times.toOffset(user.getCreatedAt()));
    }

    @Transactional
    public AccountResponse updateProfile(AuthenticatedUser principal, ProfileUpdateRequest request) {
        User user = member(principal);
        String nickname = policy.normalizeNickname(request.nickname());
        String phone = policy.normalizePhone(request.phone());
        String bio = request.bio() == null ? null : request.bio().strip();
        if (bio != null && bio.isEmpty()) {
            bio = null;
        }

        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!policy.isValidNicknameFormat(nickname)) {
            errors.add(new ErrorResponse.FieldError("nickname", "닉네임은 2~12자의 한글, 영문, 숫자만 쓸 수 있습니다."));
        }
        if (!policy.isValidPhone(phone)) {
            errors.add(new ErrorResponse.FieldError("phone", "휴대전화 번호를 숫자 10~11자리로 입력해 주세요."));
        }
        if (bio != null && bio.codePointCount(0, bio.length()) > BIO_MAX) {
            errors.add(new ErrorResponse.FieldError("bio", "소개는 300자까지 쓸 수 있습니다."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        boolean nicknameChanged = !nickname.equals(user.getNickname());
        if (nicknameChanged && (policy.isReservedNickname(nickname) || userRepository.existsByNickname(nickname))) {
            throw nicknameUnavailable();
        }

        if (request.profileFileId() != null) {
            StoredFile file = fileRepository.findById(request.profileFileId())
                    .filter(f -> !f.isDeleted() && f.getPurpose() == FilePurpose.PROFILE
                            && user.getId().equals(f.getUserId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_FILE",
                            "프로필 사진을 다시 올려 주세요."));
            if (!Objects.equals(file.url(), user.getProfileImageUrl())) {
                retireProfileImage(user);
                user.changeProfileImage(file.url());
            }
        } else if (Boolean.TRUE.equals(request.removeProfileImage())) {
            retireProfileImage(user);
            user.changeProfileImage(null);
        }

        user.changeProfile(nickname, phone, bio);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 같은 닉네임으로 바꾼 경우 (uk_users_nickname)
            throw nicknameUnavailable();
        }
        return account(principal);
    }

    @Transactional
    public void changePassword(AuthenticatedUser principal, PasswordChangeRequest request) {
        User user = member(principal);
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!policy.isValidPassword(request.newPassword())) {
            errors.add(new ErrorResponse.FieldError("newPassword",
                    "비밀번호는 8~15자이며 영문, 숫자, 특수문자를 모두 포함해야 합니다."));
        } else if (!request.newPassword().equals(request.newPasswordConfirm())) {
            errors.add(new ErrorResponse.FieldError("newPasswordConfirm", "비밀번호가 서로 다릅니다."));
        } else if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            errors.add(new ErrorResponse.FieldError("newPassword", "지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeOthers(user.getId(), principal.sessionId(), LocalDateTime.now());
    }

    private void retireProfileImage(User user) {
        String url = user.getProfileImageUrl();
        if (url == null || !url.startsWith("/files/")) {
            return;
        }
        fileRepository.findByStoredNameIn(List.of(url.substring("/files/".length())))
                .forEach(StoredFile::markDeleted);
    }

    User member(AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 회원정보 화면을 쓰지 않습니다.");
        }
        return userRepository.findById(principal.id()).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
    }

    private static ApiException nicknameUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "NICKNAME_UNAVAILABLE", "사용할 수 없는 닉네임입니다.");
    }
}
