package com.oneblog.admin;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.text.MarkdownRenderer;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;
import com.oneblog.post.DeletedBy;
import com.oneblog.post.Post;
import com.oneblog.post.PostPolicy;
import com.oneblog.post.PostType;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 메인 공지 (BRD-10). posts에 post_type = MAIN_NOTICE, blog_id = NULL로 둔다 (ERD ck_posts_main_notice).
 * 관리자만 쓰고 지우며, 누구나 읽는다. 전체 회원 알림(NOTICE, 끌 수 있음)은 011에서 붙인다.
 */
@Service
public class NoticeService {

    public static final String AUTHOR_NAME = "One Blog 운영팀";

    private final NoticeRepository noticeRepository;
    private final PostPolicy policy;
    private final MarkdownRenderer markdown;
    private final AdminActionLogger actionLogger;
    private final List<NoticeListener> listeners;

    public NoticeService(NoticeRepository noticeRepository, PostPolicy policy, MarkdownRenderer markdown,
            AdminActionLogger actionLogger, List<NoticeListener> listeners) {
        this.noticeRepository = noticeRepository;
        this.policy = policy;
        this.markdown = markdown;
        this.actionLogger = actionLogger;
        this.listeners = listeners;
    }

    public record NoticeItem(Long id, String title, String authorName, java.time.OffsetDateTime createdAt) {
    }

    public record NoticeDetail(Long id, String title, String contentHtml, String authorName,
            java.time.OffsetDateTime createdAt) {
    }

    @Transactional
    public Post create(String rawTitle, String content, AuthenticatedUser admin, HttpServletRequest request) {
        String title = policy.normalizeTitle(rawTitle);
        policy.validate(title, content);
        Post notice = noticeRepository.saveAndFlush(Post.create(null, admin.id(), PostType.MAIN_NOTICE, title, content));
        actionLogger.log(admin, "NOTICE_CREATE", "POST", notice.getId(), title, request);
        for (NoticeListener listener : listeners) {
            listener.noticePublished(notice);
        }
        return notice;
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser admin, HttpServletRequest request) {
        Post notice = find(id);
        notice.delete(DeletedBy.ADMIN);
        actionLogger.log(admin, "NOTICE_DELETE", "POST", id, notice.getTitle(), request);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(PageParams params) {
        Page<Post> page = noticeRepository.findMainNotices(PageRequest.of(params.zeroBasedPage(), params.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<NoticeItem> items = page.getContent().stream()
                .map(n -> new NoticeItem(n.getId(), n.getTitle(), AUTHOR_NAME, Times.toOffset(n.getCreatedAt())))
                .toList();
        return Map.of("items", items, "page", params.page(), "size", params.size(),
                "totalItems", page.getTotalElements(), "totalPages", Math.max(1, page.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public NoticeDetail detail(Long id) {
        Post notice = find(id);
        return new NoticeDetail(notice.getId(), notice.getTitle(), markdown.toSafeHtml(notice.getContent()), AUTHOR_NAME,
                Times.toOffset(notice.getCreatedAt()));
    }

    private Post find(Long id) {
        return noticeRepository.findById(id)
                .filter(p -> p.getPostType() == PostType.MAIN_NOTICE && p.isVisible())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다."));
    }
}
