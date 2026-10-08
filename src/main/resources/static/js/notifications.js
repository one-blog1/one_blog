// 알림 (SOC-04, 3.6): 상단 종 아이콘, 안 읽은 수, 오른쪽 사이드바의 탭별 목록. 누르면 자세히 보기(사유·바로가기, D-107).
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

  const el = window.ui.el;

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

  // 목록에는 짧은 문장만, 사유와 바로가기는 알림을 눌러 연 자세히 보기에서 (D-107)
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
      const body = el('button', 'notification-body');
      body.type = 'button';
      body.append(el('span', 'notification-message', n.message),
        el('span', 'notification-date', new Date(n.createdAt).toLocaleString('ko-KR')));
      body.addEventListener('click', () => openDetail(n.id));
      li.append(body);
      list.append(li);
    });
    panel.querySelector('.notification-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager && window.pager.render(panel.querySelector('.pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      load();
    });
  }

  function showView(detail) {
    panel.querySelector('.notification-browse').classList.toggle('hidden', detail);
    panel.querySelector('.notification-detail').classList.toggle('hidden', !detail);
  }

  async function openDetail(id) {
    const box = panel.querySelector('.notification-detail');
    box.replaceChildren();
    const result = await window.api.get('/api/notifications/' + encodeURIComponent(id), { userAction: true });
    const back = window.icons.button('back', '목록으로', 'tip-start');
    back.addEventListener('click', () => {
      showView(false);
      load();
    });
    box.append(back);
    if (!result.ok || !result.data) {
      box.append(el('p', 'message error', (result.data && result.data.message) || '알림을 열지 못했어요.'));
      showView(true);
      return;
    }
    const n = result.data;
    box.append(el('p', 'notification-type', n.typeLabel),
      el('p', 'notification-detail-message', n.message),
      el('p', 'notification-date', new Date(n.createdAt).toLocaleString('ko-KR')));
    if (n.detail) {
      box.append(el('p', 'notification-reason', n.detail));
    }
    const link = safeLink(n.linkUrl);
    if (link) {
      const go = el('a', 'button-link notification-go', n.linkLabel || '바로가기');
      go.href = link;
      box.append(go);
    } else if (n.linkUnavailable) {
      box.append(el('p', 'hint', n.linkUnavailable));
    }
    showView(true);
    poll();
    back.focus();
  }

  function build() {
    const area = document.getElementById('header-user');
    bell = el('button', 'bell link-button');
    bell.type = 'button';
    bell.setAttribute('aria-haspopup', 'dialog');
    bell.dataset.tip = '알림';
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
    // 아이콘 단추: 마우스를 올리면 이름이 뜬다 (D-108)
    const readAll = window.icons.button('checks', '모두 읽음', 'tip-end');
    readAll.addEventListener('click', async () => {
      await window.api.post('/api/notifications/read-all', undefined, { userAction: true });
      load();
    });
    const clear = window.icons.button('trash', '전체 삭제', 'tip-end danger');
    clear.addEventListener('click', async () => {
      if (await window.dialog.confirm('받은 알림을 모두 지울까요?')) {
        await window.api.delete('/api/notifications', { userAction: true });
        load();
      }
    });
    const settings = window.icons.button('settings', '알림 설정', 'tip-end', 'a');
    settings.href = '/settings.html';
    const close = window.icons.button('close', '닫기', 'tip-end');
    close.addEventListener('click', () => setOpen(false));
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
    const browse = el('div', 'notification-browse');
    browse.append(tabs, el('ul', 'notification-list'), el('p', 'hint notification-empty hidden', '알림이 없어요.'),
      el('nav', 'pagination'));
    panel.append(head, browse, el('div', 'notification-detail hidden'));
    document.body.append(panel);
    bell.addEventListener('click', () => setOpen(!state.open));

    // 알림 창 밖(왼쪽 화면 아무 데나)을 누르거나 Esc를 누르면 닫는다 (D-108)
    document.addEventListener('click', (event) => {
      if (!state.open) {
        return;
      }
      const path = event.composedPath();
      // 확인 창(전체 삭제 확인 등)을 누른 것은 바깥 클릭이 아니다
      const inDialog = path.some(n => n.classList && n.classList.contains('modal-overlay'));
      if (!inDialog && !path.includes(panel) && !path.includes(bell)) {
        setOpen(false);
      }
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && state.open) {
        setOpen(false);
        bell.focus();
      }
    });
  }

  function setOpen(open) {
    state.open = open;
    panel.classList.toggle('hidden', !open);
    bell.setAttribute('aria-expanded', String(open));
    if (open) {
      state.page = 1;
      showView(false);
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
