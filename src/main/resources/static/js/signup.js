// 회원가입 화면 (USR-01, USR-02, SEC-02, 4.6)
// 단계: ① 이메일 인증 → ② 비밀번호 → ③ 이름·닉네임·전화번호·동의
// 서버 문구는 모두 textContent로만 표시한다 (SEC-06). 버튼·폼 요청만 userAction: true (D-62).
(function () {
  'use strict';

  const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s])\S{8,15}$/;
  const NICKNAME_RULE = /^[가-힣A-Za-z0-9]{2,12}$/;
  const NICKNAME_REASONS = {
    TAKEN: '이미 쓰이는 닉네임입니다.',
    RESERVED: '사용할 수 없는 닉네임입니다.',
    INVALID_FORMAT: '2~12자의 한글, 영문, 숫자만 쓸 수 있습니다.'
  };

  let resendTimer = null;
  let password = '';

  const $ = window.ui.$;

  function setMessage(el, text, ok) {
    el.textContent = text || '';
    el.classList.toggle('ok', Boolean(ok));
    el.classList.toggle('error', !ok);
  }

  function showStep(step) {
    [1, 2, 3].forEach(n => $('step-' + n).classList.toggle('hidden', n !== step));
    document.querySelectorAll('.steps li').forEach(li => {
      li.classList.toggle('active', Number(li.dataset.step) === step);
    });
  }

  function fieldErrorText(data, fallback) {
    if (data && Array.isArray(data.fieldErrors) && data.fieldErrors.length > 0) {
      return data.fieldErrors.map(f => f.message).join(' ');
    }
    return (data && data.message) || fallback;
  }

  // 다시 보내기 남은 시간 표시. 화면 안에서만 세고 서버를 호출하지 않는다.
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
    const message = $('code-message');
    const code = $('code').value.trim();
    if (!/^[0-9]{6}$/.test(code)) {
      setMessage(message, '인증번호는 숫자 6자리입니다.', false);
      return;
    }
    const result = await window.api.post('/api/auth/signup/email-code/verify',
      { email: $('email').value.trim(), code: code }, { userAction: true });
    if (result.ok) {
      $('email').readOnly = true;
      showStep(2);
      $('password').focus();
      return;
    }
    let text = fieldErrorText(result.data, '인증하지 못했습니다.');
    if (result.data && result.data.code === 'CODE_MISMATCH') {
      text += ' (남은 횟수 ' + result.data.remainingAttempts + '번)';
    }
    setMessage(message, text, false);
  }

  function submitPassword(event) {
    event.preventDefault();
    const message = $('password-message');
    const value = $('password').value;
    if (!PASSWORD_RULE.test(value)) {
      setMessage(message, '비밀번호는 8~15자이며 영문, 숫자, 특수문자를 모두 포함해야 합니다.', false);
      return;
    }
    if (value !== $('password-confirm').value) {
      setMessage(message, '비밀번호가 서로 다릅니다.', false);
      return;
    }
    setMessage(message, '', false);
    password = value;
    showStep(3);
    $('name').focus();
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

  async function submitSignup(event) {
    event.preventDefault();
    const message = $('signup-message');
    const form = $('step-3');
    if (!form.agreeTerms.checked || !form.agreePrivacy.checked) {
      setMessage(message, '필수 항목에 모두 동의해 주세요.', false);
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const result = await window.api.post('/api/auth/signup', {
        password: password,
        passwordConfirm: password,
        name: form.name.value.trim(),
        nickname: form.nickname.value.trim(),
        phone: form.phone.value.trim(),
        agreeTerms: form.agreeTerms.checked,
        agreePrivacy: form.agreePrivacy.checked
      }, { userAction: true });

      if (result.ok) {
        window.location.href = '/login.html?signup=done';
        return;
      }
      const code = result.data && result.data.code;
      if (code === 'SIGNUP_TICKET_INVALID' || code === 'SIGNUP_FAILED') {
        // 처음부터 다시 해야 하는 경우
        setMessage(message, result.data.message, false);
        return;
      }
      setMessage(message, fieldErrorText(result.data, '가입하지 못했습니다.'), false);
    } finally {
      button.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('send-code').addEventListener('click', sendCode);
    $('step-1').addEventListener('submit', verifyCode);
    $('step-2').addEventListener('submit', submitPassword);
    $('check-nickname').addEventListener('click', checkNickname);
    $('nickname').addEventListener('input', () => setMessage($('nickname-message'), '', false));
    $('step-3').addEventListener('submit', submitSignup);
  });
})();
