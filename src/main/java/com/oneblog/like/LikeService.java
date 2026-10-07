package com.oneblog.like;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.post.Post;
import com.oneblog.post.PostRepository;
import com.oneblog.post.PostService;

/**
 * 좋아요 (BRD-06, 6.5): 한 번만, 다시 누르면 취소. 좋아요 수는 posts.like_count에 바로 더한다 (D-89 인기순 기준).
 * 같은 회원의 누르기가 동시에 여러 번 들어와도 회원 행을 잠가 차례로 처리한다.
 */
@Service
public class LikeService {

    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final BlogAccessService accessService;
    private final UserRepository userRepository;
    private final List<LikeListener> listeners;

    /** 좋아요·팔로우·구독은 한 회원이 1분에 이만큼까지 누른다 (6.6). */
    public static final int TOGGLES_PER_MINUTE = 60;

    private final com.oneblog.common.security.RateLimiter rateLimiter;

    public LikeService(PostLikeRepository likeRepository, PostRepository postRepository, PostService postService,
            BlogAccessService accessService, UserRepository userRepository, List<LikeListener> listeners,
            com.oneblog.common.security.RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.listeners = listeners;
    }

    public record LikeResult(boolean liked, int likeCount) {
    }

    /** 누르면 좋아요, 이미 눌렀으면 취소. */
    @Transactional
    public LikeResult toggle(Long postId, String key, AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 좋아요를 누를 수 없습니다.");
        }
        // 좋아요를 빠르게 반복하지 못하게 한다 (6.6 "트래픽 제한")
        rateLimiter.check("toggle", String.valueOf(principal.id()), TOGGLES_PER_MINUTE, java.time.Duration.ofMinutes(1));
        Post post = postService.findBlogPost(postId);
        Blog blog = postService.blogOf(post);
        accessService.check(blog, key, principal.id());
        userRepository.findForUpdateById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."));

        boolean liked;
        var existing = likeRepository.findByPostIdAndUserId(postId, principal.id());
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            likeRepository.flush();
            postRepository.addLikeCount(postId, -1);
            liked = false;
        } else {
            likeRepository.saveAndFlush(PostLike.of(postId, principal.id()));
            postRepository.addLikeCount(postId, 1);
            liked = true;
            for (LikeListener listener : listeners) {
                listener.afterLike(blog, post, principal);
            }
        }
        int count = postRepository.findById(postId).map(Post::getLikeCount).orElse(0);
        return new LikeResult(liked, count);
    }
}
