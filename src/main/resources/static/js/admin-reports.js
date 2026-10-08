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

  // 신고 대상이 있는 화면 주소. 신고할 때 남긴 기록(snapshot)의 블로그 주소·글 번호로 만든다
  function targetHref(r) {
    let s = {};
    try {
      s = JSON.parse(r.snapshot) || {};
    } catch (e) {
      s = {};
    }
    const blog = s.blogSlug ? '/blog/' + encodeURIComponent(s.blogSlug) : null;
    switch (r.targetType) {
      case 'POST':
        return blog && blog + '/posts/' + encodeURIComponent(r.targetId);
      case 'COMMENT':
        return blog && s.postId && blog + '/posts/' + encodeURIComponent(s.postId) + '#comment-' + encodeURIComponent(r.targetId);
      case 'BLOG':
        return blog;
      default:
        return r.targetNickname ? '/users/' + encodeURIComponent(r.targetNickname) : null;
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
      const type = TYPE[r.targetType] || r.targetType;
      const href = targetHref(r);
      let target = type;
      if (href) {
        target = h.el('a', 'goto-link', type + ' 바로가기');
        target.href = href;
        target.target = '_blank';
        target.rel = 'noopener';
      }
      return [h.date(r.createdAt), target, r.targetNickname || '', snapshotText(r.snapshot),
        r.reasonLabel + (r.detail ? ' — ' + r.detail : ''), r.reporterNickname, actions];
    }));
  }

  window.adminReports = { render: render };
})();
