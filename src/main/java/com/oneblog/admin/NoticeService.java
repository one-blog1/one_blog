package com.oneblog.admin;

import java.time.OffsetDateTime;
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
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.Times;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.post.DeletedBy;
import com.oneblog.post.Post;
import com.oneblog.post.PostPolicy;
import com.oneblog.post.PostType;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 메인 공지 (BRD-10). posts에 post_type = MAIN_NOTICE, blog_id = NULL로 둔다 (ERD ck_posts_main_notice).
 * 관리자만 쓰고 고치고 지우며, 누구나 읽는다. 전체 회원 알림(NOTICE, 끌 수 있음)은 011에서 붙인다.
 * 공지마다 종류(일반·업데이트·수정·점검)가 있다 (D-122). 고쳐도 알림은 다시 보내지 않는다 (018 R4).
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

    public record NoticeItem(Long id, String title, String category, String categoryLabel, String authorName,
            OffsetDateTime createdAt) {
    }

    public record NoticeDetail(Long id, String title, String category, String categoryLabel, String contentHtml,
            String authorName, OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean edited) {
    }

    /** 관리자 수정 칸에 채울 원문 (마크다운 그대로). */
    public record NoticeSource(Long id, String title, String category, String content) {
    }

    /** 수정됨 표시: 만든 뒤 1분 넘게 지나 고친 경우만 (만들 때 바로 붙는 시각 차이는 무시). */
    private static final long EDITED_AFTER_SECONDS = 60;

    @Transactional
    public Post create(String rawTitle, String content, String rawCategory, AuthenticatedUser admin,
            HttpServletRequest request) {
        NoticeCategory category = category(rawCategory);
        String title = policy.normalizeTitle(rawTitle);
        policy.validate(title, content);
        Post draft = Post.create(null, admin.id(), PostType.MAIN_NOTICE, title, content);
        draft.setNoticeCategory(category.name());
        Post notice = noticeRepository.saveAndFlush(draft);
        actionLogger.log(admin, "NOTICE_CREATE", "POST", notice.getId(), title, request);
        for (NoticeListener listener : listeners) {
            listener.noticePublished(notice);
        }
        return notice;
    }

    /** 제목·내용·종류를 고친다. 활동 기록을 남기고 알림은 보내지 않는다. */
    @Transactional
    public void update(Long id, String rawTitle, String content, String rawCategory, AuthenticatedUser admin,
            HttpServletRequest request) {
        Post notice = find(id);
        NoticeCategory category = category(rawCategory);
        String title = policy.normalizeTitle(rawTitle);
        policy.validate(title, content);
        notice.edit(title, content);
        notice.setNoticeCategory(category.name());
        actionLogger.log(admin, "NOTICE_UPDATE", "POST", id, title, request);
    }

    @Transactional(readOnly = true)
    public NoticeSource source(Long id) {
        Post notice = find(id);
        return new NoticeSource(notice.getId(), notice.getTitle(), categoryOf(notice).name(), notice.getContent());
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser admin, HttpServletRequest request) {
        Post notice = find(id);
        notice.delete(DeletedBy.ADMIN);
        actionLogger.log(admin, "NOTICE_DELETE", "POST", id, notice.getTitle(), request);
    }

    /** category가 비면 전체. 정해진 값이 아니면 400. */
    @Transactional(readOnly = true)
    public Map<String, Object> list(PageParams params, String rawCategory) {
        PageRequest pageable = PageRequest.of(params.zeroBasedPage(), params.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Post> page = (rawCategory == null || rawCategory.isBlank())
                ? noticeRepository.findMainNotices(pageable)
                : noticeRepository.findMainNoticesByCategory(category(rawCategory).name(), pageable);
        List<NoticeItem> items = page.getContent().stream()
                .map(n -> {
                    NoticeCategory c = categoryOf(n);
                    return new NoticeItem(n.getId(), n.getTitle(), c.name(), c.label(), AUTHOR_NAME,
                            Times.toOffset(n.getCreatedAt()));
                })
                .toList();
        return Map.of("items", items, "page", params.page(), "size", params.size(),
                "totalItems", page.getTotalElements(), "totalPages", Math.max(1, page.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public NoticeDetail detail(Long id) {
        Post notice = find(id);
        NoticeCategory c = categoryOf(notice);
        boolean edited = notice.getUpdatedAt() != null && notice.getCreatedAt() != null
                && notice.getUpdatedAt().isAfter(notice.getCreatedAt().plusSeconds(EDITED_AFTER_SECONDS));
        return new NoticeDetail(notice.getId(), notice.getTitle(), c.name(), c.label(),
                markdown.toSafeHtml(notice.getContent()), AUTHOR_NAME, Times.toOffset(notice.getCreatedAt()),
                Times.toOffset(notice.getUpdatedAt()), edited);
    }

    private static NoticeCategory category(String raw) {
        NoticeCategory category = NoticeCategory.parseOrDefault(raw);
        if (category == null) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("category", "공지 종류를 골라 주세요.")));
        }
        return category;
    }

    /** 저장된 종류. V18 전에 올린 공지는 마이그레이션이 GENERAL로 채웠지만, 혹시 비어 있으면 일반으로 본다. */
    private static NoticeCategory categoryOf(Post notice) {
        NoticeCategory c = NoticeCategory.parseOrDefault(notice.getNoticeCategory());
        return c == null ? NoticeCategory.GENERAL : c;
    }

    private Post find(Long id) {
        return noticeRepository.findById(id)
                .filter(p -> p.getPostType() == PostType.MAIN_NOTICE && p.isVisible())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다."));
    }
}
