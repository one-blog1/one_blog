// 알림 (SOC-04, 3.6): 상단 종 아이콘, 안 읽은 수, 오른쪽 사이드바의 탭별 목록.
// 안 읽은 수는 30초마다 묻는다(D-72). 이 자동 요청은 사용자 활동이 아니므로 userAction을 붙이지 않는다 (D-62).
// 메시지는 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const POLL_MS = 30 * 1000;
  const TABS = [['ALL', '전체'], ['COMMENT', '댓글'], ['LIKE', '좋아요'], ['FOLLOW', '팔로우·구독'], ['BLOG', '블로그'],
    ['OPERATION', '운영']];
  const state = { tab: 'ALL', page: 1, open: false };
  let bell;
  let badge;
  let panel;

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = text;
    }
    return node;
  }

  function setBadge(count) {
    badge.textContent = count > 99 ? '99+' : String(count);
    badge.classList.toggle('hidden', count === 0);
    bell.setAttribute('aria-label', '알림' + (count > 0 ? ', 안 읽은 알림 ' + count + '개' : ''));
  }

  async function poll() {
    const result = await window.api.get('/api/notifications/unread-count');
    if (result.ok && result.data) {
      setBadge(result.data.count);
    }
  }

  function safeLink(url) {
    return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//') ? url : null;
  }

  async function load() {
    const q = new URLSearchParams({ tab: state.tab, page: String(state.page), size: '20' });
    const result = await window.api.get('/api/notifications?' + q.toString(), { userAction: true });
    if (!result.ok || !result.data) {
      return;
    }
    const data = result.data;
    state.page = data.page;
    setBadge(data.unreadCount);
    const list = panel.querySelector('.notification-list');
    list.replaceChildren();
    data.items.forEach(n => {
      const li = el('li', n.read ? 'notification' : 'notification unread');
      const link = safeLink(n.linkUrl);
      const body = el(link ? 'a' : 'span', 'notification-body');
      if (link) {
        body.href = link;
      }
      body.append(el('span', 'notification-message', n.message),
        el('span', 'notification-date', new Date(n.createdAt).toLocaleString('ko-KR')));
      body.addEventListener('click', async (e) => {
        if (!n.read) {
          if (link) {
            e.preventDefault();
          }
          await window.api.post('/api/notifications/' + n.id + '/read', undefined, { userAction: true });
          if (link) {
            window.location.href = link;
          } else {
            load();
          }
        }
      });
      li.append(body);
      list.append(li);
    });
    panel.querySelector('.notification-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager && window.pager.render(panel.querySelector('.pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      load();
    });
  }

  function build() {
    const area = document.getElementById('header-user');
    bell = el('button', 'bell link-button');
    bell.type = 'button';
    bell.setAttribute('aria-haspopup', 'dialog');
    // 종 모양 아이콘 (글자 이모지는 기기마다 색이 달라 선으로 그린다, D-104)
    const ns = 'http://www.w3.org/2000/svg';
    const icon = document.createElementNS(ns, 'svg');
    icon.setAttribute('viewBox', '0 0 24 24');
    icon.setAttribute('aria-hidden', 'true');
    icon.setAttribute('class', 'bell-icon');
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16zM10 20.5a2 2 0 0 0 4 0');
    icon.append(path);
    bell.append(icon);
    badge = el('span', 'bell-badge hidden', '0');
    bell.append(badge);
    area.prepend(bell);

    panel = el('aside', 'notification-panel hidden');
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', '알림');
    const head = el('div', 'notification-head');
    head.append(el('strong', null, '알림'));
    const readAll = el('button', 'link-button', '모두 읽음');
    readAll.type = 'button';
    readAll.addEventListener('click', async () => {
      await window.api.post('/api/notifications/read-all', undefined, { userAction: true });
      load();
    });
    const clear = el('button', 'link-button danger', '전체 삭제');
    clear.type = 'button';
    clear.addEventListener('click', async () => {
      if (window.confirm('받은 알림을 모두 지울까요?')) {
        await window.api.delete('/api/notifications', { userAction: true });
        load();
      }
    });
    const settings = el('a', 'link-button', '설정');
    settings.href = '/settings.html';
    const close = el('button', 'link-button', '닫기');
    close.type = 'button';
    close.addEventListener('click', toggle);
    head.append(readAll, clear, settings, close);

    const tabs = el('div', 'tabs');
    tabs.setAttribute('role', 'tablist');
    TABS.forEach(([key, label]) => {
      const tab = el('button', null, label);
      tab.type = 'button';
      tab.setAttribute('role', 'tab');
      tab.setAttribute('aria-selected', String(key === state.tab));
      tab.addEventListener('click', () => {
        state.tab = key;
        state.page = 1;
        tabs.querySelectorAll('button').forEach(b => b.setAttribute('aria-selected', String(b === tab)));
        load();
      });
      tabs.append(tab);
    });
    panel.append(head, tabs, el('ul', 'notification-list'), el('p', 'hint notification-empty hidden', '알림이 없어요.'),
      el('nav', 'pagination'));
    document.body.append(panel);
    bell.addEventListener('click', toggle);
  }

  function toggle() {
    state.open = !state.open;
    panel.classList.toggle('hidden', !state.open);
    bell.setAttribute('aria-expanded', String(state.open));
    if (state.open) {
      state.page = 1;
      load();
    }
  }

  build();
  poll();
  window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      poll();
    }
  }, POLL_MS);
})();
