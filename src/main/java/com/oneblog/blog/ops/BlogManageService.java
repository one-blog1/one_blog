package com.oneblog.blog.ops;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogCreateService;
import com.oneblog.blog.BlogJoinPolicy;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogPolicy;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.BlogRole;
import com.oneblog.blog.BlogUrls;
import com.oneblog.blog.BlogVisibility;
import com.oneblog.blog.subscription.BlogSubscriptionRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.text.Masking;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.Times;
import com.oneblog.member.UserRepository;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;
import com.oneblog.post.PostRepository;
import com.oneblog.tag.TagPolicy;

/**
 * 블로그 운영 (2장 블로그장 표, BLG-01 수정, BLG-07, D-71).
 * - 정보(이름·소개·대표 이미지·태그): 블로그장, 정보 수정 권한을 받은 부블로그장
 * - 공개 범위·참여 방식·공유 링크 다시 만들기, 부블로그장 지정·해제: 블로그장만
 * - 멤버 목록(이메일·전화번호 가림): 블로그장, 멤버 관리 권한을 받은 부블로그장
 * - 블로그 탈퇴: 블로그장이 아닌 멤버. 떠난 멤버의 글은 "탈퇴한 계정" (D-33)
 * 역할은 요청마다 DB 멤버십으로 확인한다 (SEC-07).
 */
@Service
public class BlogManageService {

    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogCreateService createService;
    private final BlogPolicy policy;
    private final TagPolicy tagPolicy;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final BlogSubscriptionRepository subscriptionRepository;
    private final BlogTransferRequestRepository transferRepository;
    private final NotificationService notifications;
    private final NamedParameterJdbcTemplate jdbc;

    public BlogManageService(BlogAccessService accessService, BlogRepository blogRepository,
            BlogMemberRepository memberRepository, BlogCreateService createService, BlogPolicy policy,
            TagPolicy tagPolicy, UserRepository userRepository, PostRepository postRepository,
            BlogSubscriptionRepository subscriptionRepository, BlogTransferRequestRepository transferRepository,
            NotificationService notifications, NamedParameterJdbcTemplate jdbc) {
        this.accessService = accessService;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.createService = createService;
        this.policy = policy;
        this.tagPolicy = tagPolicy;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transferRepository = transferRepository;
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    public record InfoRequest(String name, String description, Long coverFileId, Boolean removeCover,
            List<String> tags) {
    }

    public record SettingsRequest(BlogVisibility visibility, BlogJoinPolicy joinPolicy) {
    }

    public record SubOwnerRequest(boolean subOwner, boolean canEditInfo, boolean canManageMembers,
            boolean canManagePosts) {
    }

    public record MemberItem(Long userId, String nickname, String name, String email, String phone, BlogRole role,
            boolean canEditInfo, boolean canManageMembers, boolean canManagePosts, OffsetDateTime joinedAt) {
    }

    @Transactional
    public void updateInfo(String slug, AuthenticatedUser principal, InfoRequest request) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        BlogMember me = require(blog, principal, BlogMember::canEditInfo, "블로그 정보를 고칠 권한이 없습니다.");
        String name = policy.normalizeName(request.name());
        String description = policy.normalizeDescription(request.description());
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!policy.isValidName(name)) {
            errors.add(new ErrorResponse.FieldError("name", "블로그 이름은 1~50자로 입력해 주세요."));
        }
        if (!policy.isValidDescription(description)) {
            errors.add(new ErrorResponse.FieldError("description", "소개는 500자까지 쓸 수 있습니다."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        List<String> tags = tagPolicy.normalizeAll(request.tags(), "tags");
        String cover = blog.getCoverImageUrl();
        if (request.coverFileId() != null) {
            cover = createService.resolveCover(me.getUserId(), request.coverFileId());
        } else if (Boolean.TRUE.equals(request.removeCover())) {
            cover = null;
        }
        blog.updateInfo(name, description, cover);
        blogRepository.saveAndFlush(blog);
        replaceBlogTags(blog.getId(), tags);
    }

    /** 공개 범위·참여 방식 (블로그장만). 공개 종류가 바뀌면 개수 제한을 다시 센다 (BLG-10). 비공개가 되면 구독자에게 알린다 (D-37). */
    @Transactional
    public void updateSettings(String slug, AuthenticatedUser principal, SettingsRequest request) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        requireOwner(blog, principal);
        if (request.visibility() != null && request.visibility() != blog.getVisibility()) {
            boolean wasPrivate = blog.getVisibility() == BlogVisibility.PRIVATE;
            boolean toPrivate = request.visibility() == BlogVisibility.PRIVATE;
            if (wasPrivate != toPrivate) {
                userRepository.findForUpdateById(principal.id());
                createService.checkLimit(principal.id(), request.visibility());
            }
            blog.changeVisibility(request.visibility(), BlogCreateService.newShareToken());
            if (toPrivate) {
                List<Long> subscribers = new ArrayList<>(subscriptionRepository.findUserIds(blog.getId()));
                subscribers.removeAll(memberRepository.findMemberIdsExceptOwner(blog.getId()));
                subscribers.remove(principal.id());
                notifications.sendAll(subscribers, NotificationType.BLOG_PRIVATE,
                        "구독한 블로그 「" + blog.getName() + "」이 비공개로 바뀌었어요. 멤버가 아니면 글을 볼 수 없어요.",
                        null, null);
            }
        }
        if (request.joinPolicy() != null) {
            blog.changeJoinPolicy(request.joinPolicy());
        }
        blogRepository.saveAndFlush(blog);
    }

    /** 공유 링크 다시 만들기 (블로그장만, 일부 공개 블로그). 이전 링크는 바로 무효. */
    @Transactional
    public String regenerateShareLink(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        requireOwner(blog, principal);
        if (blog.getVisibility() != BlogVisibility.UNLISTED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_UNLISTED", "일부 공개 블로그만 공유 링크가 있습니다.");
        }
        blog.regenerateShareToken(BlogCreateService.newShareToken());
        blogRepository.saveAndFlush(blog);
        return BlogUrls.shareUrl(blog);
    }

    @Transactional(readOnly = true)
    public List<MemberItem> members(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        require(blog, principal, BlogMember::canManageMembers, "멤버 정보를 볼 권한이 없습니다.");
        return jdbc.query("""
                SELECT u.id, u.nickname, u.name, u.email, u.phone, m.role, m.can_edit_info, m.can_manage_members,
                       m.can_manage_posts, m.joined_at
                FROM blog_members m JOIN users u ON u.id = m.user_id
                WHERE m.blog_id = :blogId AND m.status = 'ACTIVE'
                ORDER BY FIELD(m.role, 'OWNER', 'SUB_OWNER', 'MEMBER'), m.joined_at ASC, m.id ASC
                """, new MapSqlParameterSource("blogId", blog.getId()),
                (rs, i) -> new MemberItem(rs.getLong(1), rs.getString(2), rs.getString(3),
                        Masking.email(rs.getString(4)), Masking.phone(rs.getString(5)),
                        BlogRole.valueOf(rs.getString(6)), rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9),
                        Times.toOffset(rs.getTimestamp(10).toLocalDateTime())));
    }

    /** 부블로그장 지정·권한 변경·해제 (블로그장만, D-71). */
    @Transactional
    public void setSubOwner(String slug, Long userId, AuthenticatedUser principal, SubOwnerRequest request) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        requireOwner(blog, principal);
        BlogMember target = memberRepository.findActive(blog.getId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "멤버를 찾을 수 없습니다."));
        if (target.isOwner()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CHANGE_OWNER", "블로그장의 역할은 위임으로만 바꿀 수 있습니다.");
        }
        if (request.subOwner()) {
            target.makeSubOwner(request.canEditInfo(), request.canManageMembers(), request.canManagePosts(),
                    LocalDateTime.now());
        } else {
            target.makeMember();
        }
        memberRepository.saveAndFlush(target);
    }

    /** 블로그 탈퇴 (BLG-07). 블로그장은 위임하거나 폐쇄해야 한다. 떠난 사람의 글은 "탈퇴한 계정" (D-33). */
    @Transactional
    public void leave(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        userRepository.findForUpdateById(principal.id());
        BlogMember me = memberRepository.findActive(blog.getId(), principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_A_MEMBER", "이 블로그의 멤버가 아닙니다."));
        if (me.isOwner()) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_LEAVE", "블로그장은 위임하거나 폐쇄한 뒤에 나갈 수 있습니다.");
        }
        leaveInternal(blog.getId(), me, true, LocalDateTime.now());
    }

    /** 멤버십을 끝낸다. detach면 글을 "탈퇴한 계정"으로 (블로그 탈퇴 D-33). 회원탈퇴는 "탈퇴한 회원"이 되도록 detach하지 않는다. */
    public void leaveInternal(Long blogId, BlogMember member, boolean detach, LocalDateTime now) {
        member.leave(now);
        memberRepository.saveAndFlush(member);
        blogRepository.addMemberCount(blogId, -1);
        if (detach) {
            postRepository.detachAuthor(blogId, member.getUserId());
        }
        transferRepository.findByBlogIdAndStatus(blogId, TransferStatus.PENDING).stream()
                .filter(r -> r.getToUserId().equals(member.getUserId()))
                .forEach(r -> r.finish(TransferStatus.CANCELED, now));
    }

    private void replaceBlogTags(Long blogId, List<String> tags) {
        jdbc.update("DELETE FROM blog_tags WHERE blog_id = :blogId", new MapSqlParameterSource("blogId", blogId));
        if (!tags.isEmpty()) {
            jdbc.batchUpdate("INSERT IGNORE INTO tags (name) VALUES (:name)", tags.stream()
                    .map(t -> new MapSqlParameterSource("name", t)).toArray(MapSqlParameterSource[]::new));
            jdbc.update("""
                    INSERT INTO blog_tags (blog_id, tag_id) SELECT :blogId, id FROM tags WHERE name IN (:names)
                    """, new MapSqlParameterSource().addValue("blogId", blogId).addValue("names", tags));
        }
    }

    BlogMember requireOwner(Blog blog, AuthenticatedUser principal) {
        return require(blog, principal, BlogMember::isOwner, "블로그장만 할 수 있습니다.");
    }

    private BlogMember require(Blog blog, AuthenticatedUser principal, java.util.function.Predicate<BlogMember> check,
            String message) {
        BlogMember me = accessService.activeMembership(blog.getId(), principal.id());
        if (me == null || !check.test(me)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
        return me;
    }
}
