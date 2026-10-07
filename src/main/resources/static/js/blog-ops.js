// 블로그 첫 화면의 운영 버튼: 관리 화면 링크, 블로그 나가기(BLG-07), 폐쇄 예정 안내(BLG-09, D-67).
(function () {
  'use strict';

  function $(id) {
    return document.getElementById(id);
  }

  document.addEventListener('blog:loaded', (event) => {
    const blog = event.detail;
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
        if (!window.confirm('이 블로그에서 나갈까요? 내가 쓴 글은 "탈퇴한 계정"으로 남아요.')) {
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
    $('ops-area').classList.remove('hidden');
  });
})();
