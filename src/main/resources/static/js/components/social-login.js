// 소셜 로그인 단추 (022, D-126). 키가 들어온 사이트만 서버가 알려 주고, 없으면 칸을 숨긴다.
// 쓰는 법: <div data-social-login data-verb="로그인"></div> 를 두고 이 파일을 불러온다.
(function () {
  'use strict';

  const ERRORS = {
    canceled: '소셜 로그인을 취소했습니다.',
    failed: '소셜 로그인을 하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    inactive: '이 소셜 계정에 연결된 회원은 로그인할 수 없습니다.'
  };

  /** 주소의 social_error 값에 맞는 문구. 모르는 값이면 기본 실패 문구. */
  function errorMessage(code) {
    if (!code) {
      return '';
    }
    return ERRORS[code] || ERRORS.failed;
  }

  async function render(box) {
    const result = await window.api.get('/api/auth/social/providers');
    const providers = result.ok && Array.isArray(result.data) ? result.data : [];
    if (providers.length === 0) {
      box.classList.add('hidden');
      return;
    }
    const verb = box.dataset.verb || '로그인';
    box.replaceChildren();
    box.classList.add('social-login');
    box.appendChild(window.ui.el('p', 'social-login-divider', '또는'));
    const list = window.ui.el('div', 'social-login-buttons');
    providers.forEach((p) => {
      const link = window.ui.el('a', 'social-button social-button-' + String(p.provider).toLowerCase(),
        p.label + '로 ' + verb);
      // 서버가 준 주소만 쓴다 (/oauth2/authorization/{사이트})
      if (typeof p.loginUrl === 'string' && p.loginUrl.startsWith('/oauth2/authorization/')) {
        link.href = p.loginUrl;
      }
      list.appendChild(link);
    });
    box.appendChild(list);
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-social-login]').forEach(render);
  });

  window.socialLogin = { errorMessage: errorMessage };
})();
