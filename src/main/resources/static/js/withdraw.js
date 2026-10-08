// 회원 탈퇴 (USR-05, D-103): 내 정보 맨 아래 링크로만 들어온다. 안내 확인 → 비밀번호 확인 → 탈퇴.
// 블로그장인 블로그가 남아 있으면 그 목록을 보여주고 버튼을 막는다(서버도 거절한다).
(function () {
  'use strict';

  const state = { blocked: false };

  const $ = window.ui.$;

  function setError(field, text) {
    const node = document.querySelector('[data-error-for="' + field + '"]');
    if (node) {
      node.textContent = text || '';
    }
  }

  function refreshButton() {
    $('withdraw-submit').disabled = state.blocked || !$('withdraw-agree').checked;
  }

  async function loadBlocking() {
    const result = await window.api.get('/api/me/withdrawal', { userAction: true, redirectOnLogout: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (!result.ok || !Array.isArray(result.data)) {
      $('withdraw-message').textContent = (result.data && result.data.message) || '탈퇴할 수 있는지 확인하지 못했어요.';
      state.blocked = true;
      refreshButton();
      return;
    }
    const list = $('withdraw-blogs');
    list.replaceChildren();
    result.data.forEach(b => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = '/blog/' + encodeURIComponent(b.slug) + '/manage';
      a.textContent = b.name + (b.closing ? ' (폐쇄 예정)' : '');
      li.append(a);
      list.append(li);
    });
    state.blocked = result.data.length > 0;
    $('withdraw-blocked').classList.toggle('hidden', !state.blocked);
    refreshButton();
  }

  async function withdraw(event) {
    event.preventDefault();
    setError('password', '');
    $('withdraw-message').textContent = '';
    if (!$('withdraw-agree').checked) {
      return;
    }
    if (!$('withdraw-password').value) {
      setError('password', '비밀번호를 입력해 주세요.');
      $('withdraw-password').focus();
      return;
    }
    const result = await window.api.post('/api/me/withdrawal', { password: $('withdraw-password').value },
      { userAction: true });
    $('withdraw-password').value = '';
    if (!result.ok) {
      const data = result.data || {};
      (data.fieldErrors || []).forEach(f => setError(f.field, f.message));
      $('withdraw-message').textContent = data.message || '탈퇴하지 못했어요.';
      return;
    }
    window.location.href = '/';
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('withdraw-agree').addEventListener('change', refreshButton);
    $('withdraw-form').addEventListener('submit', withdraw);
    loadBlocking();
  });
})();
