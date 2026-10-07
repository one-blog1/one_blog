// 로그인 화면 (USR-03, SEC-04). 서버 문구는 textContent로만 표시한다 (SEC-06).
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', () => {
    if (new URLSearchParams(window.location.search).get('signup') === 'done') {
      document.getElementById('signup-done').classList.remove('hidden');
    }
    if (new URLSearchParams(window.location.search).get('reset') === 'done') {
      document.getElementById('reset-done').classList.remove('hidden');
    }

    const form = document.getElementById('login-form');
    const message = document.getElementById('login-message');

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      message.textContent = '';

      const email = form.email.value.trim();
      const password = form.password.value;
      if (!email || !password) {
        message.textContent = '이메일과 비밀번호를 입력해 주세요.';
        return;
      }

      const button = form.querySelector('button[type="submit"]');
      button.disabled = true;
      try {
        const result = await window.api.post('/api/auth/login', {
          email: email,
          password: password,
          rememberMe: form.rememberMe.checked,
          captchaToken: window.captcha ? window.captcha.token() : null
        }, { userAction: true });

        if (result.ok) {
          window.location.href = '/';
          return;
        }
        const data = result.data || {};
        if (data.code === 'LOGIN_LOCKED' && data.retryAfterSeconds) {
          message.textContent = data.message + ' (' + Math.ceil(data.retryAfterSeconds / 60) + '분 뒤)';
        } else {
          message.textContent = data.message || '로그인하지 못했습니다. 다시 시도해 주세요.';
        }
        // 3번 틀린 뒤에는 사람 확인 (SEC-13)
        if ((data.code === 'CAPTCHA_REQUIRED' || data.code === 'LOGIN_FAILED_CAPTCHA') && window.captcha) {
          window.captcha.show(document.getElementById('captcha'));
        }
      } finally {
        button.disabled = false;
      }
    });
  });
})();
