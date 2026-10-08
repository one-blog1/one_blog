// 설정 화면 (D-101, D-114, D-115): 알림 끄기·켜기와 보관 기간, 프로필 공개 범위, 차단한 회원 관리, 화면(밝기·글자 크기).
// 상단 프로필 메뉴의 "설정"에서 온다. 닉네임은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  function $(id) {
    return document.getElementById(id);
  }

  async function load() {
    const result = await window.api.get('/api/me/notification-settings', { userAction: true, redirectOnLogout: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (!result.ok || !result.data) {
      $('settings-message').className = 'message error';
      $('settings-message').textContent = (result.data && result.data.message) || '설정을 불러오지 못했어요.';
      return;
    }
    const box = $('notification-types');
    box.replaceChildren();
    result.data.items.forEach(item => {
      const label = document.createElement('label');
      label.className = 'checkbox';
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.dataset.type = item.type;
      input.checked = item.enabled;
      label.append(input, document.createTextNode(' ' + item.label));
      box.append(label);
    });
    $('retention').value = String(result.data.retentionDays);
  }

  async function save(event) {
    event.preventDefault();
    const settings = {};
    $('notification-types').querySelectorAll('input[data-type]').forEach(i => { settings[i.dataset.type] = i.checked; });
    const result = await window.api.put('/api/me/notification-settings',
      { retentionDays: parseInt($('retention').value, 10), settings: settings }, { userAction: true });
    const message = $('settings-message');
    message.className = result.ok ? 'message ok' : 'message error';
    message.textContent = result.ok ? '저장했어요.' : ((result.data && result.data.message) || '저장하지 못했어요.');
  }

  function say(id, ok, text) {
    $(id).className = ok ? 'message ok' : 'message error';
    $(id).textContent = text;
  }

  // ---- 프로필 공개 범위 (D-114) ----
  async function loadPrivacy() {
    const result = await window.api.get('/api/me/privacy');
    if (!result.ok || !result.data) {
      return;
    }
    $('privacy-blogs').checked = result.data.showBlogs;
    $('privacy-follows').checked = result.data.showFollows;
    $('privacy-allow-follow').checked = result.data.allowFollow;
  }

  async function savePrivacy(event) {
    event.preventDefault();
    const result = await window.api.put('/api/me/privacy', {
      showBlogs: $('privacy-blogs').checked,
      showFollows: $('privacy-follows').checked,
      allowFollow: $('privacy-allow-follow').checked
    }, { userAction: true });
    say('privacy-message', result.ok, result.ok ? '저장했어요.' : ((result.data && result.data.message) || '저장하지 못했어요.'));
  }

  // ---- 차단한 회원 (SOC-05) ----
  async function loadBlocks() {
    const result = await window.api.get('/api/me/blocks');
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const list = $('block-list');
    list.replaceChildren();
    result.data.forEach(user => {
      const li = document.createElement('li');
      const link = document.createElement('a');
      link.className = 'user-link';
      link.href = '/users/' + encodeURIComponent(user.nickname);
      const img = document.createElement('img');
      img.className = 'avatar';
      img.alt = '';
      img.src = user.profileImageUrl && user.profileImageUrl.startsWith('/files/')
        ? user.profileImageUrl : '/images/avatar-default.svg';
      const name = document.createElement('span');
      name.textContent = user.nickname;
      link.append(img, name);
      const unblock = document.createElement('button');
      unblock.type = 'button';
      unblock.className = 'secondary';
      unblock.textContent = '차단 해제';
      unblock.addEventListener('click', async () => {
        // 차단 단추는 누를 때마다 차단·해제가 바뀐다
        const r = await window.api.post('/api/users/' + encodeURIComponent(user.nickname) + '/block', undefined,
          { userAction: true });
        say('blocks-message', r.ok, r.ok ? user.nickname + '님 차단을 풀었어요.' : ((r.data && r.data.message) || '풀지 못했어요.'));
        loadBlocks();
      });
      li.append(link, unblock);
      list.append(li);
    });
    $('blocks-count').textContent = result.data.length > 0 ? result.data.length + '명' : '';
    $('blocks-empty').classList.toggle('hidden', result.data.length > 0);
  }

  // ---- 화면 (D-115): 이 기기에만 저장 ----
  function renderDisplay() {
    const current = window.theme.get();
    document.querySelectorAll('[data-theme-choice]').forEach(b => {
      b.setAttribute('aria-pressed', String(b.dataset.themeChoice === current.theme));
    });
    document.querySelectorAll('[data-font-choice]').forEach(b => {
      b.setAttribute('aria-pressed', String(b.dataset.fontChoice === current.font));
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('notification-settings').addEventListener('submit', save);
    $('privacy-form').addEventListener('submit', savePrivacy);
    document.querySelectorAll('[data-theme-choice]').forEach(b => b.addEventListener('click', () => {
      window.theme.setTheme(b.dataset.themeChoice);
      renderDisplay();
    }));
    document.querySelectorAll('[data-font-choice]').forEach(b => b.addEventListener('click', () => {
      window.theme.setFont(b.dataset.fontChoice);
      renderDisplay();
    }));
    renderDisplay();
    load();
    loadPrivacy();
    loadBlocks();
  });
})();
