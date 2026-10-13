package com.oneblog.post;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.common.text.MarkdownRenderer;
import com.oneblog.member.UserDisplayService;

/**
 * 글 목록의 본문 미리보기와 작성자 사진 (BRD-02, D-128).
 * - 미리보기: 거른 본문의 글자만 앞 160자(영상 재생기·카드 글자는 빠짐). 화면이 2줄로 자른다
 * - 작성자 사진: 프로필 사진이 있는 활동 중인 회원만. 블로그를 떠난 작성자("탈퇴한 계정")는 비운다
 */
@Component
public class PostSummaryExtension implements PostExtension {

    static final int EXCERPT_LENGTH = 160;

    private final MarkdownRenderer markdown;
    private final UserDisplayService userDisplay;

    public PostSummaryExtension(MarkdownRenderer markdown, UserDisplayService userDisplay) {
        this.markdown = markdown;
        this.userDisplay = userDisplay;
    }

    @Override
    public void describeList(Blog blog, PostListContext context) {
        if (context.posts.isEmpty()) {
            return;
        }
        Set<Long> authors = new HashSet<>();
        for (Post post : context.posts) {
            context.excerpts.put(post.getId(), markdown.toPlainText(post.getContent(), EXCERPT_LENGTH));
            if (!post.isAuthorDetached()) {
                authors.add(post.getUserId());
            }
        }
        Map<Long, String> images = userDisplay.profileImages(authors);
        for (Post post : context.posts) {
            if (!post.isAuthorDetached() && images.containsKey(post.getUserId())) {
                context.authorImages.put(post.getId(), images.get(post.getUserId()));
            }
        }
    }
}
