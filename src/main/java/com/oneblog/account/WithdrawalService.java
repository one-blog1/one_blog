package com.oneblog.account;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.RefreshTokenRepository;
import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRole;
import com.oneblog.blog.BlogVisibility;
import com.oneblog.blog.ops.BlogManageService;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;

/**
 * 회원탈퇴 (USR-05, 4.5).
 * - 비밀번호를 다시 확인한다
 * - 블로그장인 블로그가 하나라도 있으면(폐쇄 예정 7일 포함) 탈퇴할 수 없다. 위임하거나 폐쇄가 끝나야 한다
 * - 탈퇴하면 로그인용 이메일은 바로 비우고(같은 이메일로 다시 가입 가능), 모든 로그인을 끝내고, 참여한 블로그에서 빠진다
 * - 쓴 글은 "탈퇴한 회원"으로 남는다(작성자 연결은 끊지 않는다). 나머지 개인정보는 30일 뒤 04:00 배치가 지운다
 */
@Service
public class WithdrawalService {

    private final UserRepository userRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogManageService manageService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;

    public WithdrawalService(UserRepository userRepository, BlogMemberRepository memberRepository,
            BlogManageService manageService, RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder, NamedParameterJdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.manageService = manageService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    public record OwnedBlog(String slug, String name, BlogVisibility visibility, boolean closing) {
    }

    /** 탈퇴 전에 정리해야 할 블로그 (블로그장인 블로그, 폐쇄 예정 포함). */
    @Transactional(readOnly = true)
    public List<OwnedBlog> blockingBlogs(Long userId) {
        return memberRepository.findMyBlogs(userId).stream()
                .filter(row -> row[1] == BlogRole.OWNER)
                .map(row -> (Blog) row[0])
                .map(b -> new OwnedBlog(b.getSlug(), b.getName(), b.getVisibility(), b.isClosing()))
                .toList();
    }

    @Transactional
    public void withdraw(AuthenticatedUser principal, String password) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 탈퇴할 수 없습니다.");
        }
        User user = userRepository.findForUpdateById(principal.id()).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("password", "비밀번호가 맞지 않습니다.")));
        }
        if (!blockingBlogs(user.getId()).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNED_BLOGS_REMAIN",
                    "블로그장인 블로그가 있어 탈퇴할 수 없습니다. 블로그마다 위임하거나 폐쇄가 끝난 뒤에 탈퇴해 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        for (Object[] row : memberRepository.findMyBlogs(user.getId())) {
            Blog blog = (Blog) row[0];
            memberRepository.findActive(blog.getId(), user.getId())
                    .ifPresent(member -> manageService.leaveInternal(blog.getId(), member, false, now));
        }
        MapSqlParameterSource me = new MapSqlParameterSource("userId", user.getId());
        jdbc.update("DELETE FROM blog_subscriptions WHERE user_id = :userId", me);
        jdbc.update("UPDATE blog_join_requests SET status = 'CANCELED' WHERE user_id = :userId AND status = 'PENDING'", me);
        jdbc.update("DELETE FROM recent_searches WHERE user_id = :userId", me);
        refreshTokenRepository.revokeAll(user.getId(), now);

        User fresh = userRepository.findById(user.getId()).orElseThrow();
        fresh.withdraw(now);
        userRepository.saveAndFlush(fresh);
    }
}
