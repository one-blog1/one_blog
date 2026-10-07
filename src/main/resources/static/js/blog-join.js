// 블로그 참여 신청·승인 (BLG-04, BLG-05). 블로그 첫 화면(blog.js)이 정보를 불러오면 이어서 그린다.
// 사용자 입력(닉네임)은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  let blog = null;
  let loggedIn = false;

  function $(id) {
    return document.getElementById(id);
  }

  function baseUrl() {
    return '/api/blogs/' + encodeURIComponent(blog.slug);
  }

  function keyQuery() {
    const key = new URLSearchParams(window.location.search).get('key');
    return key ? '?key=' + encodeURIComponent(key) : '';
  }

  function show(id, visible) {
    $(id).classList.toggle('hidden', !visible);
  }

  function formatDate(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('ko-KR');
  }

  function renderJoin(status) {
    const text = $('join-text');
    const button = $('join-button');
    show('join-area', true);
    show('join-cancel', false);
    show('join-button', false);
    if (!loggedIn) {
      text.textContent = '로그인하면 이 블로그에 참여할 수 있어요.';
      button.textContent = '로그인';
      button.onclick = () => { window.location.href = '/login.html'; };
      show('join-button', true);
      return;
    }
    switch (status.status) {
      case 'MEMBER':
        text.textContent = '이 블로그의 멤버예요.';
        break;
      case 'PENDING':
        text.textContent = '참여를 신청했어요. 블로그장의 승인을 기다리는 중이에요.';
        show('join-cancel', true);
        break;
      case 'REJECTED':
        text.textContent = '참여 신청이 거절됐어요. ' + formatDate(status.retryAt) + '부터 다시 신청할 수 있어요.';
        break;
      default:
        text.textContent = blog.joinPolicy === 'APPROVAL'
          ? '승인제 블로그예요. 신청하면 블로그장이 확인 후 승인해요.'
          : '자유 참여 블로그예요. 누르면 바로 멤버가 돼요.';
        button.textContent = blog.joinPolicy === 'APPROVAL' ? '참여 신청' : '참여하기';
        button.onclick = apply;
        show('join-button', true);
    }
  }

  async function loadStatus() {
    const result = await window.api.get(baseUrl() + '/join' + keyQuery());
    if (result.status === 401) {
      loggedIn = false;
      renderJoin({ status: 'NONE' });
      return;
    }
    if (result.ok && result.data) {
      loggedIn = true;
      renderJoin(result.data);
    }
  }

  async function apply() {
    $('join-message').textContent = '';
    const result = await window.api.post(baseUrl() + '/join' + keyQuery(), undefined, { userAction: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (result.ok && result.data) {
      if (result.data.status === 'MEMBER') {
        window.location.reload();
        return;
      }
      renderJoin(result.data);
      return;
    }
    $('join-message').textContent = (result.data && result.data.message) || '신청하지 못했어요. 다시 시도해 주세요.';
  }

  async function cancel() {
    const result = await window.api.request('DELETE', baseUrl() + '/join' + keyQuery(), { userAction: true });
    if (result.ok && result.data) {
      renderJoin(result.data);
    } else {
      $('join-message').textContent = (result.data && result.data.message) || '취소하지 못했어요.';
    }
  }

  async function loadRequests() {
    const result = await window.api.get(baseUrl() + '/join-requests');
    if (!result.ok || !Array.isArray(result.data)) {
      show('manage-area', false); // 멤버 관리 권한이 없으면 숨긴다
      return;
    }
    show('manage-area', true);
    const list = $('request-list');
    list.replaceChildren();
    $('request-count').textContent = result.data.length > 0 ? String(result.data.length) + '건' : '';
    show('request-empty', result.data.length === 0);
    result.data.forEach(r => {
      const li = document.createElement('li');
      const name = document.createElement('span');
      name.className = 'request-name';
      name.textContent = r.nickname + ' · ' + formatDate(r.requestedAt);
      const approve = document.createElement('button');
      approve.type = 'button';
      approve.className = 'secondary';
      approve.textContent = '승인';
      approve.addEventListener('click', () => decide(r.id, 'approve'));
      const reject = document.createElement('button');
      reject.type = 'button';
      reject.className = 'link-button';
      reject.textContent = '거절';
      reject.addEventListener('click', () => decide(r.id, 'reject'));
      li.append(name, approve, reject);
      list.append(li);
    });
  }

  async function decide(id, action) {
    const result = await window.api.post(baseUrl() + '/join-requests/' + encodeURIComponent(id) + '/' + action,
      undefined, { userAction: true });
    if (!result.ok) {
      window.alert((result.data && result.data.message) || '처리하지 못했어요.');
    }
    loadRequests();
  }

  document.addEventListener('blog:loaded', (event) => {
    blog = event.detail;
    $('join-cancel').addEventListener('click', cancel);
    loadStatus();
    if (blog.myRole === 'OWNER' || blog.myRole === 'SUB_OWNER') {
      loadRequests();
    }
  });
})();
