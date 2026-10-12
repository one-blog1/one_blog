// 내 정보 화면의 소셜 로그인 연결 (022, D-126). 사이트마다 연결하기·연결 끊기.
// 연결하기는 서버가 연결 표시 쿠키를 준 뒤 그 사이트 로그인으로 이동하고, 돌아오면 ?social=linked 로 온다.
(function () {
  'use strict';

  const $ = window.ui.$;
  const el = window.ui.el;

  const ERRORS = {
    taken: '그 소셜 계정은 이미 다른 회원에 연결되어 있어요.',
    already: '이미 같은 사이트의 다른 계정이 연결되어 있어요. 먼저 연결을 끊어 주세요.',
    inactive: '연결하지 못했어요. 다시 로그인해 주세요.',
    canceled: '연결을 취소했어요.',
    failed: '연결하지 못했어요. 잠시 후 다시 시도해 주세요.'
  };

  function setMessage(text, ok) {
    const node = $('social-message');
    node.className = 'message ' + (ok ? 'ok' : 'error');
    node.textContent = text || '';
  }

  async function startLink(provider, button) {
    button.disabled = true;
    const result = await window.api.post('/api/me/social/' + encodeURIComponent(provider) + '/link', undefined,
      { userAction: true });
    const url = result.data && result.data.url;
    if (result.ok && typeof url === 'string' && url.startsWith('/oauth2/authorization/')) {
      window.location.href = url;
      return;
    }
    button.disabled = false;
    setMessage((result.data && result.data.message) || ERRORS.failed, false);
  }

  async function unlink(item, button) {
    const ok = await window.dialog.confirm(item.label + ' 연결을 끊을까요? 끊으면 ' + item.label
      + '로 로그인할 수 없어요.', { okLabel: '연결 끊기', danger: true });
    if (!ok) {
      return;
    }
    button.disabled = true;
    const result = await window.api.delete('/api/me/social/' + encodeURIComponent(item.provider),
      { userAction: true });
    if (result.ok) {
      setMessage(item.label + ' 연결을 끊었어요.', true);
      load();
      return;
    }
    button.disabled = false;
    setMessage((result.data && result.data.message) || '연결을 끊지 못했어요.', false);
  }

  async function load() {
    const result = await window.api.get('/api/me/social', { userAction: true });
    const providers = result.ok && result.data && Array.isArray(result.data.providers) ? result.data.providers : [];
    if (providers.length === 0) {
      $('social-accounts').classList.add('hidden');
      return;
    }
    $('social-no-password').classList.toggle('hidden', Boolean(result.data.hasPassword));
    const list = $('social-list');
    list.replaceChildren();
    providers.forEach((item) => {
      const li = el('li');
      const name = el('span', null, item.label);
      const state = el('span', 'social-account-state',
        item.linked ? '연결됨 · ' + window.ui.formatDate(item.linkedAt) : '연결 안 됨');
      const left = el('div');
      left.append(name, document.createTextNode(' '), state);
      li.append(left);
      if (item.linked) {
        const button = el('button', 'link-button danger', '연결 끊기');
        button.type = 'button';
        button.addEventListener('click', () => unlink(item, button));
        li.append(button);
      } else if (item.enabled) {
        const button = el('button', 'secondary', '연결하기');
        button.type = 'button';
        button.addEventListener('click', () => startLink(item.provider, button));
        li.append(button);
      }
      list.append(li);
    });
    $('social-accounts').classList.remove('hidden');
  }

  document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('social') === 'linked') {
      setMessage('소셜 계정을 연결했어요. 다음부터 그 계정으로 로그인할 수 있어요.', true);
    } else if (params.get('social_error')) {
      setMessage(ERRORS[params.get('social_error')] || ERRORS.failed, false);
    }
    if (params.has('social') || params.has('social_error')) {
      // 새로 고쳐도 문구가 다시 뜨지 않게 주소에서 지운다
      window.history.replaceState(null, '', window.location.pathname);
    }
    load();
  });
})();
