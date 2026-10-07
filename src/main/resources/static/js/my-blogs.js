// 내 블로그 (BLG-06): 내가 만든 블로그와 참여한 블로그를 나눠 보여준다. 공개 범위와 상관없이 모두 보인다.
// 구독한 블로그(SOC-01)도 함께 보여준다.
(function () {
  'use strict';

  function render(listId, emptyId, items) {
    const list = document.getElementById(listId);
    list.replaceChildren();
    items.forEach(b => list.append(window.blogCard.create(b, { showVisibility: true, showRole: true })));
    document.getElementById(emptyId).classList.toggle('hidden', items.length > 0);
  }

  async function loadTransfers() {
    const result = await window.api.get('/api/me/transfer-requests');
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const list = document.getElementById('transfer-list');
    list.replaceChildren();
    result.data.forEach(t => {
      const li = document.createElement('li');
      const text = document.createElement('span');
      text.textContent = t.fromNickname + '님이 블로그 「' + t.blogName + '」의 블로그장을 맡아 달라고 요청했어요.';
      const accept = document.createElement('button');
      accept.type = 'button';
      accept.className = 'primary inline-button';
      accept.textContent = '수락';
      const reject = document.createElement('button');
      reject.type = 'button';
      reject.className = 'secondary';
      reject.textContent = '거절';
      const respond = async (action) => {
        const r = await window.api.post('/api/transfer-requests/' + t.id + '/' + action, undefined, { userAction: true });
        const message = document.getElementById('transfer-message');
        message.className = r.ok ? 'message ok' : 'message error';
        message.textContent = r.ok ? (action === 'accept' ? '이제 블로그장이에요.' : '거절했어요.')
          : ((r.data && r.data.message) || '처리하지 못했어요.');
        window.location.reload();
      };
      accept.addEventListener('click', () => respond('accept'));
      reject.addEventListener('click', () => respond('reject'));
      li.append(text, accept, reject);
      list.append(li);
    });
    document.getElementById('transfer-area').classList.toggle('hidden', result.data.length === 0);
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
    loadTransfers();
    const subscribed = await window.api.get('/api/me/subscriptions');
    render('subscribed-list', 'subscribed-empty', subscribed.ok && Array.isArray(subscribed.data) ? subscribed.data : []);
  });
})();
