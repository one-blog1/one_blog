// 소셜 가입 화면 (022, D-126). 서버가 준 가입 대기 정보로 칸을 채우고, 가입 또는 기존 계정 연결을 한다.
// 이메일은 사이트가 인증했으면 그대로 쓰고(바꿀 수 없음), 아니면 인증번호로 확인한다.
// 서버 문구는 모두 textContent로만 표시한다 (SEC-06).
(function () {
  'use strict';

  const NICKNAME_RULE = /^[가-힣A-Za-z0-9]{2,12}$/;
  const NICKNAME_REASONS = {
    TAKEN: '이미 쓰이는 닉네임입니다.',
    RESERVED: '사용할 수 없는 닉네임입니다.',
    INVALID_FORMAT: '2~12자의 한글, 영문, 숫자만 쓸 수 있습니다.'
  };

  const $ = window.ui.$;
  let emailVerified = false;
  let resendTimer = null;

  function setMessage(node, text, ok) {
    node.textContent = text || '';
    node.classList.toggle('ok', Boolean(ok));
    node.classList.toggle('error', !ok);
  }

  function fieldErrorText(data, fallback) {
    if (data && Array.isArray(data.fieldErrors) && data.fieldErrors.length > 0) {
      return data.fieldErrors.map(f => f.message).join(' ');
    }
    return (data && data.message) || fallback;
  }

  function showExpired() {
    $('content').classList.add('hidden');
    $('expired').classList.remove('hidden');
  }

  async function load() {
    const result = await window.api.get('/api/auth/social/pending');
    if (!result.ok || !result.data) {
      showExpired();
      return;
    }
    const p = result.data;
    $('title').textContent = p.providerLabel + ' 계정으로 가입';
    $('intro').textContent = p.providerLabel + ' 계정을 처음 쓰시네요. 아래 정보를 확인하면 가입이 끝납니다.';
    if (p.email) {
      $('email').value = p.email;
      $('link-email').value = p.email;
    }
    emailVerified = Boolean(p.emailVerified);
    if (emailVerified) {
      // 사이트가 인증한 이메일은 그대로 쓴다
      $('email').readOnly = true;
      $('send-code').classList.add('hidden');
      $('email-hint').textContent = p.providerLabel + '에서 확인된 이메일입니다.';
    }
    if (p.name) {
      $('name').value = p.name;
    }
    if (p.nickname) {
      $('nickname').value = p.nickname;
    }
    if (p.emailInUse) {
      // 이메일을 인증한 본인이므로 이미 가입했다고 알려 주고 연결을 권한다
      $('signup-form').classList.add('hidden');
      $('email-form').classList.add('hidden');
      $('intro').textContent = '이 이메일로 가입한 계정이 이미 있습니다. 비밀번호로 로그인하면 '
        + p.providerLabel + ' 계정이 연결됩니다.';
    }
    $('content').classList.remove('hidden');
  }

  function startResendCountdown(seconds) {
    const button = $('send-code');
    let remaining = seconds;
    clearInterval(resendTimer);
    button.disabled = true;
    button.textContent = '다시 보내기 (' + remaining + '초)';
    resendTimer = setInterval(() => {
      remaining -= 1;
      if (remaining <= 0) {
        clearInterval(resendTimer);
        button.disabled = false;
        button.textContent = '다시 보내기';
      } else {
        button.textContent = '다시 보내기 (' + remaining + '초)';
      }
    }, 1000);
  }

  async function sendCode() {
    const message = $('send-message');
    const email = $('email').value.trim();
    if (!email) {
      setMessage(message, '이메일을 입력해 주세요.', false);
      return;
    }
    const result = await window.api.post('/api/auth/signup/email-code', { email: email }, { userAction: true });
    if (result.ok) {
      setMessage(message, result.data.message, true);
      $('code-area').classList.remove('hidden');
      $('code').focus();
      startResendCountdown(result.data.resendAvailableInSeconds || 60);
    } else if (result.data && result.data.code === 'RESEND_TOO_SOON') {
      setMessage(message, result.data.message, false);
      startResendCountdown(result.data.retryAfterSeconds || 60);
    } else {
      setMessage(message, fieldErrorText(result.data, '인증번호를 보내지 못했습니다.'), false);
    }
  }

  async function verifyCode(event) {
    event.preventDefault();
    if (emailVerified) {
      return;
    }
    const message = $('code-message');
    const code = $('code').value.trim();
    if (!/^[0-9]{6}$/.test(code)) {
      setMessage(message, '인증번호는 숫자 6자리입니다.', false);
      return;
    }
    const result = await window.api.post('/api/auth/signup/email-code/verify',
      { email: $('email').value.trim(), code: code }, { userAction: true });
    if (result.ok) {
      emailVerified = true;
      $('email').readOnly = true;
      $('code-area').classList.add('hidden');
      $('send-code').classList.add('hidden');
      clearInterval(resendTimer);
      setMessage($('send-message'), '이메일을 확인했습니다.', true);
      $('name').focus();
      return;
    }
    let text = fieldErrorText(result.data, '인증하지 못했습니다.');
    if (result.data && result.data.code === 'CODE_MISMATCH') {
      text += ' (남은 횟수 ' + result.data.remainingAttempts + '번)';
    }
    setMessage(message, text, false);
  }

  async function checkNickname() {
    const message = $('nickname-message');
    const nickname = $('nickname').value.trim();
    if (!NICKNAME_RULE.test(nickname)) {
      setMessage(message, NICKNAME_REASONS.INVALID_FORMAT, false);
      return;
    }
    const result = await window.api.get('/api/auth/signup/nickname-availability?nickname='
      + encodeURIComponent(nickname), { userAction: true });
    if (result.ok && result.data.available) {
      setMessage(message, '사용할 수 있는 닉네임입니다.', true);
    } else {
      setMessage(message, NICKNAME_REASONS[result.data && result.data.reason] || '확인하지 못했습니다.', false);
    }
  }

  function allAgreed() {
    return $('agree-terms').checked && $('agree-privacy').checked;
  }

  function updateAgree() {
    $('agree-all').checked = allAgreed();
    $('signup-submit').disabled = !allAgreed();
  }

  async function submitSignup(event) {
    event.preventDefault();
    const message = $('signup-message');
    if (!emailVerified) {
      setMessage(message, '이메일 인증을 먼저 해 주세요.', false);
      return;
    }
    if (!allAgreed()) {
      setMessage(message, '필수 항목에 모두 동의해 주세요.', false);
      return;
    }
    const form = $('signup-form');
    const button = $('signup-submit');
    button.disabled = true;
    try {
      const result = await window.api.post('/api/auth/signup/social', {
        name: form.name.value.trim(),
        nickname: form.nickname.value.trim(),
        phone: form.phone.value.trim(),
        agreeTerms: form.agreeTerms.checked,
        agreePrivacy: form.agreePrivacy.checked
      }, { userAction: true });
      if (result.ok) {
        window.location.href = '/';
        return;
      }
      const code = result.data && result.data.code;
      if (code === 'SOCIAL_PENDING_EXPIRED') {
        showExpired();
        return;
      }
      if (code === 'EMAIL_IN_USE') {
        $('link-email').value = $('email').value.trim();
        $('link-password').focus();
      }
      setMessage(message, fieldErrorText(result.data, '가입하지 못했습니다.'), false);
    } finally {
      button.disabled = !allAgreed();
    }
  }

  async function submitLink(event) {
    event.preventDefault();
    const message = $('link-message');
    const form = $('link-form');
    const email = form.email.value.trim();
    const password = form.password.value;
    if (!email || !password) {
      setMessage(message, '이메일과 비밀번호를 입력해 주세요.', false);
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const result = await window.api.post('/api/auth/social/link', {
        email: email,
        password: password,
        captchaToken: window.captcha ? window.captcha.token() : null
      }, { userAction: true });
      if (result.ok) {
        window.location.href = '/';
        return;
      }
      const data = result.data || {};
      if (data.code === 'SOCIAL_PENDING_EXPIRED') {
        showExpired();
        return;
      }
      if (data.code === 'LOGIN_LOCKED' && data.retryAfterSeconds) {
        setMessage(message, data.message + ' (' + Math.ceil(data.retryAfterSeconds / 60) + '분 뒤)', false);
      } else {
        setMessage(message, data.message || '로그인하지 못했습니다.', false);
      }
      if ((data.code === 'CAPTCHA_REQUIRED' || data.code === 'LOGIN_FAILED_CAPTCHA') && window.captcha) {
        window.captcha.show($('captcha'));
      }
    } finally {
      button.disabled = false;
    }
  }

  async function cancel() {
    await window.api.post('/api/auth/social/cancel', undefined, { userAction: true });
    window.location.href = '/login.html';
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('agree-all').addEventListener('change', (event) => {
      $('agree-terms').checked = event.target.checked;
      $('agree-privacy').checked = event.target.checked;
      updateAgree();
    });
    $('agree-terms').addEventListener('change', updateAgree);
    $('agree-privacy').addEventListener('change', updateAgree);
    $('send-code').addEventListener('click', sendCode);
    $('email-form').addEventListener('submit', verifyCode);
    $('check-nickname').addEventListener('click', checkNickname);
    $('nickname').addEventListener('input', () => setMessage($('nickname-message'), '', false));
    $('signup-form').addEventListener('submit', submitSignup);
    $('link-form').addEventListener('submit', submitLink);
    $('cancel').addEventListener('click', cancel);
    updateAgree();
    load();
  });
})();
