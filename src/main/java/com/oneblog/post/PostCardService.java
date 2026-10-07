package com.oneblog.post;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.web.Times;
import com.oneblog.member.UserDisplayService;
import com.oneblog.post.dto.PostCard;

/**
 * 여러 블로그의 글을 목록 한 줄(PostCard)로 바꾼다. 태그 목록(008), 통합 검색·메인 피드(010)가 함께 쓴다.
 * 어떤 글을 보여도 되는지는 부르는 쪽이 이미 골랐어야 한다. 여기서는 순서를 지켜 모양만 만든다.
 */
@Service
public class PostCardService {

    private final PostRepository postRepository;
    private final BlogRepository blogRepository;
    private final UserDisplayService userDisplay;
    private final List<PostExtension> extensions;

    public PostCardService(PostRepository postRepository, BlogRepository blogRepository,
            UserDisplayService userDisplay, List<PostExtension> extensions) {
        this.postRepository = postRepository;
        this.blogRepository = blogRepository;
        this.userDisplay = userDisplay;
        this.extensions = extensions;
    }

    /** 글 ID 순서대로 카드를 만든다. 그사이 지워진 글은 빠진다. */
    @Transactional(readOnly = true)
    public List<PostCard> cards(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId = new HashMap<>();
        postRepository.findByIdIn(postIds).forEach(p -> byId.put(p.getId(), p));
        List<Post> posts = postIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        return toCards(posts);
    }

    @Transactional(readOnly = true)
    public List<PostCard> toCards(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> blogIds = new HashSet<>();
        Set<Long> authorIds = new HashSet<>();
        posts.forEach(p -> {
            if (p.getBlogId() != null) {
                blogIds.add(p.getBlogId());
            }
            authorIds.add(p.getUserId());
        });
        Map<Long, Blog> blogs = new HashMap<>();
        blogRepository.findAllById(blogIds).forEach(b -> blogs.put(b.getId(), b));
        Map<Long, String> names = userDisplay.names(authorIds);
        PostListContext context = new PostListContext(posts);
        for (PostExtension extension : extensions) {
            extension.describeList(null, context);
        }
        return posts.stream().map(p -> {
            Blog blog = p.getBlogId() == null ? null : blogs.get(p.getBlogId());
            return new PostCard(p.getId(), blog == null ? null : blog.getSlug(), blog == null ? null : blog.getName(),
                    p.getTitle(), UserDisplayService.authorName(names, p.getUserId(), p.isAuthorDetached()),
                    context.categoryNames.get(p.getId()), context.tags.getOrDefault(p.getId(), List.of()),
                    context.thumbnails.get(p.getId()), p.getViewCount(), p.getLikeCount(), p.getCommentCount(),
                    Times.toOffset(p.getCreatedAt()));
        }).toList();
    }
}
