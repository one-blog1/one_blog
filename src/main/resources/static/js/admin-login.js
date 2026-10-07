// 관리자 로그인 (SEC-09, D-98). 3번 틀린 뒤에는 사람 확인을 거친다 (SEC-13).
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
