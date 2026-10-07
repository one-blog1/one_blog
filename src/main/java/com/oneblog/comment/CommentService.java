package com.oneblog.comment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogMember;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.Times;
import com.oneblog.member.UserDisplayService;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.post.Post;
import com.oneblog.post.PostRepository;
import com.oneblog.post.PostService;

/**
 * 댓글·대댓글 (BRD-06, 6.6, D-78, D-87).
 * - 쓰기: 글을 볼 수 있는 로그인 회원 (관리자 제외, D-90)
 * - 대댓글은 1단계: 대댓글에 답하면 부모는 첫 댓글로, 답한 상대는 reply_to_user_id로 남긴다
 * - 고치기: 본인만, "수정됨" 표시 / 지우기: 본인, 글 관리 권한이 있는 블로그장·부블로그장
 * - 답글이 있는 댓글을 지우면 "삭제된 댓글입니다"로 남기고 답글은 그대로 둔다 (6.6)
 */
@Service
public class CommentService {

    public static final int MAX_LENGTH = 500;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final BlogAccessService accessService;
    private final UserDisplayService userDisplay;
    private final List<CommentListener> listeners;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository, PostService postService,
            BlogAccessService accessService, UserDisplayService userDisplay, List<CommentListener> listeners) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.accessService = accessService;
        this.userDisplay = userDisplay;
        this.listeners = listeners;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long postId, String key, AuthenticatedUser principal) {
        Post post = postService.findBlogPost(postId);
        Blog blog = postService.blogOf(post);
        Long viewerId = principal == null ? null : principal.id();
        accessService.check(blog, key, viewerId);
        BlogMember member = accessService.activeMembership(blog.getId(), viewerId);
        boolean manager = member != null && member.canManagePosts();

        List<Comment> all = commentRepository.findByPost(postId).stream().filter(c -> !c.isHidden()).toList();
        Set<Long> userIds = new HashSet<>();
        for (Comment c : all) {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                userIds.add(c.getReplyToUserId());
            }
        }
        Map<Long, String> names = userDisplay.names(userIds);
        Map<Long, List<CommentResponse>> replies = new LinkedHashMap<>();
        for (Comment c : all) {
            if (c.isReply() && !c.isDeleted()) {
                replies.computeIfAbsent(c.getParentId(), k -> new ArrayList<>())
                        .add(toResponse(c, names, viewerId, manager, List.of()));
            }
        }
        List<CommentResponse> result = new ArrayList<>();
        for (Comment c : all) {
            if (c.isReply()) {
                continue;
            }
            List<CommentResponse> children = replies.getOrDefault(c.getId(), List.of());
            if (c.isDeleted() && children.isEmpty()) {
                continue; // 답글 없이 지운 댓글은 아예 보이지 않는다
            }
            result.add(toResponse(c, names, viewerId, manager, children));
        }
        return result;
    }

    @Transactional
    public CommentResponse create(Long postId, String key, AuthenticatedUser principal, Long parentId, String rawContent) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 댓글을 쓸 수 없습니다.");
        }
        Post post = postService.findBlogPost(postId);
        Blog blog = postService.blogOf(post);
        accessService.check(blog, key, principal.id());
        for (CommentListener listener : listeners) {
            listener.beforeWrite(blog, post, principal);
        }
        String content = validate(rawContent);

        Long rootId = null;
        Long replyTo = null;
        Comment target = null;
        if (parentId != null) {
            target = commentRepository.findById(parentId)
                    .filter(c -> c.getPostId().equals(postId) && !c.isDeleted() && !c.isHidden())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "답글을 달 댓글을 찾을 수 없습니다."));
            // 대댓글은 1단계: 대댓글에 답하면 같은 첫 댓글 아래에 달고 @상대를 남긴다 (D-78, D-87)
            rootId = target.isReply() ? target.getParentId() : target.getId();
            replyTo = target.getUserId();
        }
        Comment saved = commentRepository.saveAndFlush(Comment.create(postId, principal.id(), rootId, replyTo, content));
        postRepository.addCommentCount(postId, 1);
        for (CommentListener listener : listeners) {
            listener.afterCreate(blog, post, saved, target, principal);
        }
        Map<Long, String> names = userDisplay.names(replyTo == null ? List.of(principal.id())
                : List.of(principal.id(), replyTo));
        return toResponse(saved, names, principal.id(), false, List.of());
    }

    @Transactional
    public void edit(Long commentId, AuthenticatedUser principal, String rawContent) {
        Comment comment = findLive(commentId);
        if (!comment.getUserId().equals(principal.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "댓글은 작성자만 고칠 수 있습니다.");
        }
        Post post = postService.findBlogPost(comment.getPostId());
        accessService.check(postService.blogOf(post), null, principal.id());
        comment.edit(validate(rawContent));
    }

    @Transactional
    public void delete(Long commentId, AuthenticatedUser principal) {
        Comment comment = findLive(commentId);
        Post post = postService.findBlogPost(comment.getPostId());
        Blog blog = postService.blogOf(post);
        boolean author = comment.getUserId().equals(principal.id());
        if (!author) {
            BlogMember member = accessService.activeMembership(blog.getId(), principal.id());
            if (member == null || !member.canManagePosts()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 댓글을 지울 권한이 없습니다.");
            }
        }
        comment.delete();
        postRepository.addCommentCount(post.getId(), -1);
    }

    private Comment findLive(Long commentId) {
        return commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted() && !c.isHidden())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."));
    }

    private static String validate(String raw) {
        String content = raw == null ? "" : raw.strip();
        int length = content.codePointCount(0, content.length());
        if (length < 1 || length > MAX_LENGTH) {
            throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("content", "댓글은 1~500자로 입력해 주세요.")));
        }
        return content;
    }

    private CommentResponse toResponse(Comment c, Map<Long, String> names, Long viewerId, boolean manager,
            List<CommentResponse> replies) {
        boolean mine = viewerId != null && c.getUserId().equals(viewerId);
        boolean deleted = c.isDeleted();
        return new CommentResponse(c.getId(), c.getParentId(), deleted ? null : c.getUserId(),
                deleted ? null : names.getOrDefault(c.getUserId(), UserDisplayService.WITHDRAWN_MEMBER),
                c.getReplyToUserId() == null ? null
                        : names.getOrDefault(c.getReplyToUserId(), UserDisplayService.WITHDRAWN_MEMBER),
                deleted ? null : c.getContent(), c.isEdited(), deleted, Times.toOffset(c.getCreatedAt()),
                !deleted && mine, !deleted && (mine || manager), replies);
    }
}
