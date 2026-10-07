// 관리자 신고 처리 (ADM-04, D-95): 블로그 자체, 블로그장, 블로그장이 쓴 글·댓글, 메인 프로필 회원 신고.
// admin.js의 신고 탭이 window.adminReports.render를 부른다. 모든 값은 textContent로 넣는다 (SEC-06).
(function () {
  'use strict';

  const TYPE = { POST: '글', COMMENT: '댓글', USER: '블로그장', PROFILE: '회원(프로필)', BLOG: '블로그' };
  const RESULTS = {
    POST: [['NO_ISSUE', '문제 없음'], ['CONTENT_DELETED', '글 삭제'], ['OWNER_WARNING', '블로그장 경고'],
      ['OWNER_REVOKED', '블로그장 강퇴'], ['BLOG_CLOSED', '블로그 폐쇄']],
    COMMENT: [['NO_ISSUE', '문제 없음'], ['CONTENT_DELETED', '댓글 삭제'], ['OWNER_WARNING', '블로그장 경고'],
      ['OWNER_REVOKED', '블로그장 강퇴'], ['BLOG_CLOSED', '블로그 폐쇄']],
    USER: [['NO_ISSUE', '문제 없음'], ['OWNER_WARNING', '블로그장 경고'], ['OWNER_REVOKED', '블로그장 강퇴'],
      ['BLOG_CLOSED', '블로그 폐쇄']],
    BLOG: [['NO_ISSUE', '문제 없음'], ['OWNER_WARNING', '블로그장 경고'], ['OWNER_REVOKED', '블로그장 강퇴'],
      ['BLOG_CLOSED', '블로그 폐쇄']],
    PROFILE: [['NO_ISSUE', '문제 없음']]
  };

  function snapshotText(raw) {
    try {
      const s = JSON.parse(raw);
      return [s.blogName, s.title || s.postTitle, s.content || s.description || s.bio].filter(Boolean).join(' / ');
    } catch (e) {
      return '';
    }
  }

  function render(items, h) {
    return h.table(['신고일', '대상', '작성자', '내용', '사유', '신고한 사람', ''], items.map(r => {
      const actions = h.el('span');
      (RESULTS[r.targetType] || RESULTS.PROFILE).forEach(([result, label]) => {
        actions.append(h.button(label, async () => {
          const reason = result === 'NO_ISSUE' ? '' : window.prompt('사유를 적어 주세요 (활동 기록과 알림에 남습니다).');
          if (reason === null) {
            return;
          }
          if (result !== 'NO_ISSUE' && !reason.trim()) {
            return;
          }
          const res = await window.api.post('/api/admin/reports/' + r.id + '/resolve', { result: result, reason: reason },
            { userAction: true });
          if (!res.ok) {
            window.alert((res.data && res.data.message) || '처리하지 못했습니다.');
          }
          h.reload();
        }, result === 'NO_ISSUE' ? 'link-button' : 'link-button danger'));
      });
      return [h.date(r.createdAt), TYPE[r.targetType] || r.targetType, r.targetNickname || '', snapshotText(r.snapshot),
        r.reasonLabel + (r.detail ? ' — ' + r.detail : ''), r.reporterNickname, actions];
    }));
  }

  window.adminReports = { render: render };
})();
