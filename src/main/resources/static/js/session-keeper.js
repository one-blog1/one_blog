// 글쓰기 중 로그인 연장 (SEC-04, D-62).
// - 글자를 입력하는 동안 1분에 한 번 "직접 한 행동" 요청을 보내 30분 무활동 시계를 다시 시작한다.
// - 25분 동안 입력이 없으면 "곧 로그인 시간이 끝나요" 안내와 연장 버튼을 보여준다(만료 5분 전).
(function () {
  'use strict';

  const PING_INTERVAL = 60 * 1000;
  const WARN_AFTER = 25 * 60 * 1000;
  let lastPing = 0;
  let lastActivity = Date.now();

  async function ping() {
    lastPing = Date.now();
    lastActivity = Date.now();
    const warning = document.getElementById('session-warning');
    if (warning) {
      warning.classList.add('hidden');
    }
    await window.api.get('/api/me', { userAction: true });
  }

  function onInput() {
    lastActivity = Date.now();
    if (Date.now() - lastPing >= PING_INTERVAL) {
      ping();
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    lastPing = Date.now();
    document.addEventListener('input', onInput, true);
    document.addEventListener('keydown', onInput, true);
    const extend = document.getElementById('session-extend');
    if (extend) {
      extend.addEventListener('click', ping);
    }
    window.setInterval(() => {
      const warning = document.getElementById('session-warning');
      if (warning && Date.now() - lastActivity >= WARN_AFTER) {
        warning.classList.remove('hidden');
      }
    }, 30 * 1000);
  });

  window.sessionKeeper = { touch: onInput };
})();
