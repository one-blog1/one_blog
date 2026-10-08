// 설정 (D-101): 알림 끄기·켜기와 보관 기간 (011). 상단 프로필 메뉴의 "설정"에서 온다.
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

  document.addEventListener('DOMContentLoaded', () => {
    $('notification-settings').addEventListener('submit', save);
    load();
  });
})();
