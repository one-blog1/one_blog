// 관리자 로그인 (SEC-09, D-98). 관리자는 잠금·사람 확인을 하지 않는다 (D-105). 캡차 처리는 서버가 요구할 때만 동작한다.
(function () {
  'use strict';

  document.getElementById('admin-login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const message = document.getElementById('login-message');
    message.textContent = '';
    const result = await window.api.post('/api/auth/admin/login', {
      loginId: document.getElementById('login-id').value.trim(),
      password: document.getElementById('password').value,
      captchaToken: window.captcha.token()
    }, { userAction: true });
    if (result.ok) {
      window.location.href = '/admin.html';
      return;
    }
    const data = result.data || {};
    message.textContent = data.message || '로그인하지 못했습니다.';
    if (data.code === 'CAPTCHA_REQUIRED' || data.code === 'LOGIN_FAILED_CAPTCHA') {
      window.captcha.show(document.getElementById('captcha'));
    }
  });
})();
