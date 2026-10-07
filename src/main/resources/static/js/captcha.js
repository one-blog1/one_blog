// 사람 확인 Cloudflare Turnstile (SEC-13, D-79). 로그인을 3번 틀린 뒤 서버가 CAPTCHA_REQUIRED를 주면 보여준다.
// 서버에 키가 없으면(개발) 꺼져 있다.
(function () {
  'use strict';

  let token = null;
  let rendered = false;

  function loadScript() {
    return new Promise((resolve, reject) => {
      if (window.turnstile) {
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('turnstile'));
      document.head.append(script);
    });
  }

  /** container에 확인 상자를 띄운다. 꺼져 있으면 false. */
  async function show(container) {
    const config = await window.api.get('/api/auth/captcha-config');
    if (!config.ok || !config.data || !config.data.enabled || !config.data.siteKey) {
      return false;
    }
    container.classList.remove('hidden');
    if (rendered) {
      if (window.turnstile) {
        window.turnstile.reset();
      }
      token = null;
      return true;
    }
    await loadScript();
    window.turnstile.render(container, {
      sitekey: config.data.siteKey,
      callback: (value) => { token = value; },
      'expired-callback': () => { token = null; }
    });
    rendered = true;
    return true;
  }

  window.captcha = {
    show: show,
    token: () => token
  };
})();
