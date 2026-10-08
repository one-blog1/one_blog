package com.oneblog.comment;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 댓글 한 건. 지운 댓글은 content가 null이고 deleted=true ("삭제된 댓글입니다", 6.6).
 * replyToNickname은 대댓글에서 답한 상대 (@닉네임, D-78, D-87). 화면은 모두 textContent로 넣는다.
 */
public record CommentResponse(
        Long id,
        Long parentId,
        Long authorId,
        String authorName,
        String replyToNickname,
        String content,
        boolean edited,
        boolean deleted,
        OffsetDateTime createdAt,
        boolean canEdit,
        boolean canDelete,
        List<CommentResponse> replies,
        /** 관리자가 숨긴 댓글. 관리자에게만 보이고 그때만 true (D-106). */
        boolean hidden) {
}
