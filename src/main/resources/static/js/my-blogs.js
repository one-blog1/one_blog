// 내 블로그 (BLG-06): 내가 만든 블로그와 참여한 블로그를 나눠 보여준다. 공개 범위와 상관없이 모두 보인다.
(function () {
  'use strict';

  function render(listId, emptyId, items) {
    const list = document.getElementById(listId);
    list.replaceChildren();
    items.forEach(b => list.append(window.blogCard.create(b, { showVisibility: true, showRole: true })));
    document.getElementById(emptyId).classList.toggle('hidden', items.length > 0);
  }

  document.addEventListener('DOMContentLoaded', async () => {
    const message = document.getElementById('my-message');
    message.textContent = '불러오는 중…';
    const result = await window.api.get('/api/me/blogs', { userAction: true, redirectOnLogout: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (!result.ok || !result.data) {
      message.textContent = '목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
      return;
    }
    message.textContent = '';
    render('owned-list', 'owned-empty', result.data.owned);
    render('joined-list', 'joined-empty', result.data.joined);
  });
})();
