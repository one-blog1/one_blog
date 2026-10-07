package com.oneblog.account;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.RateLimiter;
import com.oneblog.common.security.TokenHasher;
import com.oneblog.common.text.Masking;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.SignupPolicy;
import com.oneblog.member.ValidationFailedException;

/**
 * 이메일 찾기 (USR-08, D-26).
 * ① 이름(앞뒤 공백 제외)과 전화번호(숫자만)를 받는다 ② 맞는 계정의 이메일을 가려서 가입일과 함께 보여준다. 탈퇴한 계정은 제외
 * ③ [비밀번호 재설정]은 회원 번호 대신 여기서 만든 10분짜리 임시 토큰으로 요청한다 (PasswordResetService)
 * 같은 IP는 10분에 5번까지 찾을 수 있다.
 */
@Service
public class EmailFindService {

    static final int FINDS_PER_IP = 5;
    static final Duration FIND_WINDOW = Duration.ofMinutes(10);
    static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final NamedParameterJdbcTemplate jdbc;
    private final SignupPolicy policy;
    private final TokenHasher tokenHasher;
    private final RateLimiter rateLimiter;

    public EmailFindService(NamedParameterJdbcTemplate jdbc, SignupPolicy policy, TokenHasher tokenHasher,
            RateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.tokenHasher = tokenHasher;
        this.rateLimiter = rateLimiter;
    }

    public record FindRequest(String name, String phone) {

        @Override
        public String toString() {
            return "FindRequest[***]";
        }
    }

    /** token은 [비밀번호 재설정] 요청에 쓰는 임시 값이다. 화면에 보이지 않는다. */
    public record FoundAccount(String email, String joinedAt, String token) {
    }

    @Transactional
    public List<FoundAccount> find(FindRequest request, String ip) {
        rateLimiter.check("find-email", String.valueOf(ip), FINDS_PER_IP, FIND_WINDOW);
        String name = policy.normalizeName(request.name());
        String phone = policy.normalizePhone(request.phone());
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!policy.isValidName(name)) {
            errors.add(new ErrorResponse.FieldError("name", "이름을 입력해 주세요."));
        }
        if (!policy.isValidPhone(phone)) {
            errors.add(new ErrorResponse.FieldError("phone", "휴대전화 번호를 숫자 10~11자리로 입력해 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        record Row(Long id, String email, LocalDateTime createdAt) {
        }
        List<Row> rows = jdbc.query("""
                SELECT id, email, created_at FROM users
                WHERE name = :name AND phone = :phone AND status = 'ACTIVE' AND role = 'USER'
                  AND deleted_at IS NULL AND email IS NOT NULL
                ORDER BY created_at ASC
                """, new MapSqlParameterSource().addValue("name", name).addValue("phone", phone),
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()));
        LocalDateTime now = LocalDateTime.now();
        List<FoundAccount> result = new ArrayList<>();
        for (Row row : rows) {
            String token = tokenHasher.randomToken();
            jdbc.update("""
                    INSERT INTO account_lookup_tokens (user_id, token_hash, expires_at) VALUES (:userId, :hash, :expires)
                    """, new MapSqlParameterSource().addValue("userId", row.id())
                    .addValue("hash", tokenHasher.sha256(token)).addValue("expires", now.plus(TOKEN_TTL)));
            result.add(new FoundAccount(Masking.email(row.email()), row.createdAt().format(JOINED), token));
        }
        return result;
    }
}
