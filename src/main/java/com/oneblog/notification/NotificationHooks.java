package com.oneblog.notification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.oneblog.admin.AdminListener;
import com.oneblog.admin.NoticeListener;
import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.join.JoinListener;
import com.oneblog.blog.subscription.SubscriptionListener;
import com.oneblog.comment.Comment;
import com.oneblog.comment.CommentListener;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.like.LikeListener;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.post.DeletedBy;
import com.oneblog.post.Post;
import com.oneblog.post.PostExtension;
import com.oneblog.social.FollowListener;

/**
 * 기능마다 둔 알림 자리(Listener)를 알림으로 잇는다 (3.6 표).
 * 내가 한 일은 나에게 알리지 않는다 (예: 내 글에 내가 댓글).
 * 메시지에 들어가는 닉네임·제목은 화면이 textContent로만 넣는다 (SEC-06).
 */
@Component
public class NotificationHooks implements CommentListener, LikeListener, FollowListener, SubscriptionListener,
        JoinListener, NoticeListener, AdminListener, PostExtension {

    private final NotificationService notifications;
    private final UserRepository userRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogRepository blogRepository;

    public NotificationHooks(NotificationService notifications, UserRepository userRepository,
            BlogMemberRepository memberRepository, BlogRepository blogRepository) {
        this.notifications = notifications;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.blogRepository = blogRepository;
    }

    // ---- 댓글·대댓글 ----

    @Override
    public void afterCreate(Blog blog, Post post, Comment comment, Comment target, AuthenticatedUser principal) {
        String actor = nickname(principal.id());
        String link = postLink(blog, post) + "#comment-" + comment.getId();
        Long replyTarget = target == null ? null : target.getUserId();
        if (replyTarget != null && !replyTarget.equals(principal.id())) {
            notifications.send(replyTarget, NotificationType.REPLY,
                    actor + "님이 내 댓글에 답글을 남겼어요: " + snippet(comment.getContent()), link,
                    "REPLY:" + comment.getId());
        }
        // 글 작성자가 답글 대상과 같으면 답글 알림 하나만 보낸다
        if (!post.getUserId().equals(principal.id()) && !post.getUserId().equals(replyTarget)
                && !post.isAuthorDetached()) {
            notifications.send(post.getUserId(), NotificationType.COMMENT,
                    actor + "님이 내 글 「" + post.getTitle() + "」에 댓글을 남겼어요.", link,
                    "COMMENT:" + comment.getId());
        }
    }

    // ---- 좋아요 ----

    @Override
    public void afterLike(Blog blog, Post post, AuthenticatedUser principal) {
        if (post.getUserId().equals(principal.id()) || post.isAuthorDetached()) {
            return;
        }
        // 눌렀다 취소했다 다시 눌러도 한 번만 알린다
        notifications.send(post.getUserId(), NotificationType.LIKE,
                nickname(principal.id()) + "님이 내 글 「" + post.getTitle() + "」을 좋아해요.", postLink(blog, post),
                "LIKE:" + post.getId() + ":" + principal.id());
    }

    // ---- 팔로우·구독 ----

    @Override
    public void followed(Long followerId, Long followeeId) {
        String actor = nickname(followerId);
        notifications.send(followeeId, NotificationType.FOLLOW, actor + "님이 나를 팔로우해요.", profileLink(actor),
                "FOLLOW:" + followerId);
    }

    @Override
    public void subscribed(Long blogId, Long userId) {
        Blog blog = blogRepository.findById(blogId).orElse(null);
        Long ownerId = memberRepository.findOwnerId(blogId);
        if (blog == null || ownerId == null || ownerId.equals(userId)) {
            return;
        }
        notifications.send(ownerId, NotificationType.BLOG_SUBSCRIBE,
                nickname(userId) + "님이 블로그 「" + blog.getName() + "」을 구독해요.", blogLink(blog),
                "BLOG_SUBSCRIBE:" + blogId + ":" + userId);
    }

    // ---- 참여 신청 ----

    @Override
    public void requested(Blog blog, Long applicantId) {
        List<Long> managers = new ArrayList<>(memberRepository.findMemberManagerIds(blog.getId()));
        managers.remove(applicantId);
        notifications.sendAll(managers, NotificationType.JOIN_REQUEST,
                nickname(applicantId) + "님이 블로그 「" + blog.getName() + "」에 참여를 신청했어요.", blogLink(blog), null);
    }

    @Override
    public void processed(Blog blog, Long applicantId, boolean approved) {
        notifications.send(applicantId, NotificationType.JOIN_RESULT,
                "블로그 「" + blog.getName() + "」 참여 신청이 " + (approved ? "승인됐어요." : "거절됐어요."),
                blogLink(blog), null);
    }

    // ---- 운영: 공지, 글 삭제 ----

    @Override
    public void noticePublished(Post notice) {
        notifications.sendToAllMembers(NotificationType.NOTICE, "새 공지사항: " + notice.getTitle(),
                "/notice.html?id=" + notice.getId(), "NOTICE:" + notice.getId());
    }

    @Override
    public void postDeletedByAdmin(Post post, String reason) {
        if (post.isAuthorDetached() || post.getBlogId() == null) {
            return;
        }
        notifications.send(post.getUserId(), NotificationType.POST_DELETED,
                "내 글 「" + post.getTitle() + "」이 운영 정책에 따라 관리자에 의해 삭제됐어요.",
                null, "POST_DELETED:" + post.getId(), NotificationService.Extra.of(
                        reason == null || reason.isBlank() ? null : "사유: " + reason.strip(), "POST", post.getId()));
    }

    /** 관리자가 댓글을 지웠을 때 (ADM-03, D-107). 바로가기는 댓글이 있던 글. */
    @Override
    public void commentDeletedByAdmin(com.oneblog.comment.Comment comment, String reason) {
        notifications.send(comment.getUserId(), NotificationType.COMMENT_DELETED,
                "내 댓글이 운영 정책에 따라 관리자에 의해 삭제됐어요.", null, "COMMENT_DELETED:" + comment.getId(),
                NotificationService.Extra.of(reason == null || reason.isBlank() ? null : "사유: " + reason.strip(),
                        "COMMENT", comment.getId()));
    }

    /** 블로그장·부블로그장이 남의 글을 지웠을 때 (3.6 "내 글 삭제됨"). 작성자가 직접 지운 것은 알리지 않는다. */
    @Override
    public void afterDelete(Blog blog, Post post, AuthenticatedUser principal) {
        if (post.getDeletedBy() != DeletedBy.BLOG_OWNER || post.isAuthorDetached()) {
            return;
        }
        notifications.send(post.getUserId(), NotificationType.POST_DELETED,
                "내 글 「" + post.getTitle() + "」이 블로그 「" + blog.getName() + "」의 관리자에 의해 삭제됐어요.",
                blogLink(blog), "POST_DELETED:" + post.getId());
    }

    // ---- 도우미 ----

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("누군가");
    }

    static String blogLink(Blog blog) {
        return blog.getSlug() == null ? null : "/blog/" + blog.getSlug();
    }

    static String postLink(Blog blog, Post post) {
        return "/blog/" + blog.getSlug() + "/posts/" + post.getId();
    }

    static String profileLink(String nickname) {
        return "/users/" + java.net.URLEncoder.encode(nickname, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String snippet(String text) {
        String value = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return value.codePointCount(0, value.length()) <= 40 ? value
                : value.substring(0, value.offsetByCodePoints(0, 39)) + "…";
    }
}
