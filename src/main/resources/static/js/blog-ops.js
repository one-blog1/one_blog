// 블로그 첫 화면의 운영 버튼: 관리 화면 링크, 블로그 나가기·신고(같은 줄 맨 오른쪽 아이콘, D-111), 폐쇄 예정 안내(BLG-09, D-67).
(function () {
  'use strict';

  const $ = window.ui.$;

  let user = null;
  let loadedBlog = null;

  // 블로그 신고(SOC-06)는 로그인한 일반 회원 중 블로그장이 아닌 사람에게
  function showReport() {
    if (!user || !loadedBlog || user.role === 'ADMIN' || loadedBlog.myRole === 'OWNER') {
      return;
    }
    const button = $('report-blog');
    button.classList.remove('hidden');
    button.onclick = () => window.report.open({ targetType: 'BLOG', blogSlug: loadedBlog.slug });
  }

  document.addEventListener('header:user', (event) => {
    user = event.detail;
    showReport();
  });

  document.addEventListener('DOMContentLoaded', () => {
    $('inquiry-button').addEventListener('click', async () => {
      const message = await window.dialog.prompt('블랙리스트 해제를 요청하는 이유를 적어 주세요. (예: 전화번호 주인이 바뀌었어요)');
      if (!message || !loadedBlog) {
        return;
      }
      const result = await window.api.post('/api/blogs/' + encodeURIComponent(loadedBlog.slug) + '/blacklist-inquiries',
        { message: message }, { userAction: true });
      $('join-message').textContent = result.ok ? '문의를 남겼어요. 처리 결과는 알림으로 알려 드려요.'
        : ((result.data && result.data.message) || '문의를 남기지 못했어요.');
    });
  });

  document.addEventListener('blog:loaded', (event) => {
    const blog = event.detail;
    loadedBlog = blog;
    showReport();
    if (blog.status === 'CLOSING' && blog.closeScheduledAt) {
      const when = new Date(blog.closeScheduledAt).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric' });
      $('closing-banner').textContent = '이 블로그는 ' + when + '에 폐쇄될 예정이에요. 필요한 글은 미리 챙겨 주세요.';
      $('closing-banner').classList.remove('hidden');
    }
    if (!blog.myRole) {
      return;
    }
    const p = blog.permissions || {};
    if (blog.myRole === 'OWNER' || p.canEditInfo || p.canManageMembers) {
      const link = $('manage-link');
      link.href = '/blog/' + encodeURIComponent(blog.slug) + '/manage';
      link.classList.remove('hidden');
    }
    if (blog.myRole !== 'OWNER') {
      const leave = $('leave-button');
      leave.classList.remove('hidden');
      leave.addEventListener('click', async () => {
        if (!await window.dialog.confirm('이 블로그에서 나갈까요? 내가 쓴 글은 "탈퇴한 계정"으로 남아요.',
          { title: '블로그 나가기', okLabel: '나가기', danger: true })) {
          return;
        }
        const result = await window.api.delete('/api/blogs/' + encodeURIComponent(blog.slug) + '/membership',
          { userAction: true });
        if (!result.ok) {
          $('ops-message').textContent = (result.data && result.data.message) || '나가지 못했어요.';
          return;
        }
        window.location.reload();
      });
    }
  });
})();
