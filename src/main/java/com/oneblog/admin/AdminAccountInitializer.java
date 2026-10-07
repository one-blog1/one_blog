package com.oneblog.admin;

import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.member.SignupPolicy;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;

/**
 * ADMIN_LOGIN_ID, ADMIN_PASSWORD 환경변수가 있으면 처음 관리자 계정을 만든다 (SEC-09, D-94, D-98).
 * 이미 있으면 아무것도 하지 않는다(비밀번호를 덮어쓰지 않는다).
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    /** 관리자 아이디: 영문 소문자·숫자·_ 4~30자 (users.login_id VARCHAR(30)). */
    static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{4,30}$");

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupPolicy signupPolicy;

    public AdminAccountInitializer(AdminProperties properties, UserRepository userRepository,
            PasswordEncoder passwordEncoder, SignupPolicy signupPolicy) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.signupPolicy = signupPolicy;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String loginId = properties.initialLoginId() == null ? ""
                : properties.initialLoginId().strip().toLowerCase(Locale.ROOT);
        String password = properties.initialPassword();
        if (loginId.isEmpty() || password == null || password.isEmpty()) {
            return;
        }
        if (!LOGIN_ID.matcher(loginId).matches() || !signupPolicy.isValidPassword(password)) {
            log.warn("ADMIN_LOGIN_ID(영문 소문자·숫자·_ 4~30자) 또는 ADMIN_PASSWORD(SEC-02 규칙)가 맞지 않아 관리자 계정을 만들지 않았습니다.");
            return;
        }
        if (userRepository.existsByLoginId(loginId)) {
            return;
        }
        userRepository.save(User.createAdmin(loginId, passwordEncoder.encode(password)));
        log.info("관리자 계정을 만들었습니다: {}", loginId);
    }
}
