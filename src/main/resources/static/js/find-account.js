// 비밀번호 찾기 (USR-06)와 이메일 찾기 (USR-08). 가입 여부와 관계없이 같은 문구를 보여준다.
// 이메일 찾기 결과의 [비밀번호 재설정]은 회원 번호 대신 서버가 준 임시 토큰(10분)으로 요청한다 (D-26).
(function () {
  'use strict';

  const state = { email: null, token: null };

  function $(id) {
    return document.getElementById(id);
  }

  function showTab(name) {
    const password = name === 'password';
    $('tab-password').setAttribute('aria-selected', String(password));
    $('tab-email').setAttribute('aria-selected', String(!password));
    $('password-panel').classList.toggle('hidden', !password);
    $('email-panel').classList.toggle('hidden', password);
  }

  function openReset(email, token) {
    state.email = email;
    state.token = token;
    $('reset-form').classList.remove('hidden');
    $('reset-code').focus();
  }

  async function requestCode(event) {
    event.preventDefault();
    const email = $('reset-email').value.trim();
    if (!email) {
      $('code-message').textContent = '이메일을 입력해 주세요.';
      return;
    }
    const result = await window.api.post('/api/auth/password-reset/code', { email: email }, { userAction: true });
    $('code-message').className = result.ok ? 'message ok' : 'message error';
    $('code-message').textContent = result.ok ? '입력한 이메일로 안내를 보냈습니다.'
      : ((result.data && result.data.message) || '보내지 못했어요.');
    if (result.ok) {
      openReset(email, null);
    }
  }

  async function find(event) {
    event.preventDefault();
    $('find-message').textContent = '';
    const result = await window.api.post('/api/auth/find-email', {
      name: $('find-name').value, phone: $('find-phone').value
    }, { userAction: true });
    const list = $('found-list');
    list.replaceChildren();
    if (!result.ok || !Array.isArray(result.data)) {
      const data = result.data || {};
      const field = Array.isArray(data.fieldErrors) && data.fieldErrors[0] ? data.fieldErrors[0].message : null;
      $('find-message').textContent = field || data.message || '찾지 못했어요.';
      return;
    }
    if (result.data.length === 0) {
      $('find-message').textContent = '일치하는 회원 정보가 없습니다';
      return;
    }
    result.data.forEach(a => {
      const li = document.createElement('li');
      const text = document.createElement('span');
      text.textContent = a.email + ' (' + a.joinedAt + ' 가입)';
      const reset = document.createElement('button');
      reset.type = 'button';
      reset.className = 'link-button';
      reset.textContent = '비밀번호 재설정';
      reset.addEventListener('click', async () => {
        const r = await window.api.post('/api/auth/find-email/reset-code', { token: a.token }, { userAction: true });
        $('find-message').className = r.ok ? 'message ok' : 'message error';
        $('find-message').textContent = r.ok ? a.email + '로 인증번호를 보냈어요.'
          : ((r.data && r.data.message) || '보내지 못했어요.');
        if (r.ok) {
          openReset(null, a.token);
        }
      });
      li.append(text, reset);
      list.append(li);
    });
  }

  async function reset(event) {
    event.preventDefault();
    document.querySelectorAll('#reset-form [data-error-for]').forEach(n => { n.textContent = ''; });
    $('reset-message').textContent = '';
    const result = await window.api.post('/api/auth/password-reset', {
      email: state.email, lookupToken: state.token, code: $('reset-code').value.trim(),
      newPassword: $('new-password').value, newPasswordConfirm: $('new-password-confirm').value
    }, { userAction: true });
    if (result.ok) {
      window.location.href = '/login.html?reset=done';
      return;
    }
    const data = result.data || {};
    (data.fieldErrors || []).forEach(f => {
      const node = document.querySelector('#reset-form [data-error-for="' + f.field + '"]');
      if (node) {
        node.textContent = f.message;
      }
    });
    let text = data.message || '바꾸지 못했어요.';
    if (data.remainingAttempts !== undefined && data.remainingAttempts !== null) {
      text += ' (남은 횟수 ' + data.remainingAttempts + '번)';
    }
    $('reset-message').textContent = text;
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('tab-password').addEventListener('click', () => showTab('password'));
    $('tab-email').addEventListener('click', () => showTab('email'));
    $('code-form').addEventListener('submit', requestCode);
    $('find-form').addEventListener('submit', find);
    $('reset-form').addEventListener('submit', reset);
    if (window.location.hash === '#email') {
      showTab('email');
    }
  });
})();
