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

  // 블로그 머리의 참여 단추. 신청 취소는 메인의 "참여 신청" 탭에서 한다 (D-111)
  function renderJoin(status) {
    const text = $('join-text');
    const button = $('join-button');
    const state = $('join-state');
    show('join-area', true);
    show('join-button', false);
    show('join-state', false);
    show('join-requests-link', false);
    text.textContent = '';
    if (!loggedIn) {
      button.textContent = '로그인하고 참여하기';
      button.onclick = () => { window.location.href = '/login.html'; };
      show('join-button', true);
      return;
    }
    switch (status.status) {
      case 'MEMBER':
        show('join-area', false); // 멤버면 단추가 필요 없다 (역할은 위 줄에 "나: 멤버"로 보인다)
        break;
      case 'PENDING':
        state.textContent = '참여 신청 중';
        show('join-state', true);
        show('join-requests-link', true);
        break;
      case 'REJECTED':
        text.textContent = '참여 신청이 거절됐어요. ' + formatDate(status.retryAt) + '부터 다시 신청할 수 있어요.';
        show('join-area', false);
        break;
      default:
        text.textContent = blog.joinPolicy === 'APPROVAL'
          ? '승인제 블로그예요. 신청하면 블로그장이 확인한 뒤 멤버가 돼요.' : '';
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
    // 블랙리스트에 걸렸으면 해제 문의를 남길 수 있다 (BLG-12)
    if (result.data && result.data.code === 'BLACKLISTED') {
      $('inquiry-button').classList.remove('hidden');
      $('ops-area').classList.remove('hidden');
    }
  }

  function requestItem(r) {
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
    return li;
  }

  // 신청이 많으면 다섯 건만 보이고 나머지는 더보기 창에서 (D-112)
  const INLINE = 5;
  let requests = [];
  let requestModal = null;

  function renderRequestModal() {
    if (!requestModal) {
      return;
    }
    const list = document.createElement('ul');
    list.className = 'request-list';
    requests.forEach(r => list.append(requestItem(r)));
    requestModal.setTitle('참여 신청 ' + requests.length + '건');
    requestModal.body.replaceChildren(list);
    if (requests.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'hint';
      empty.textContent = '대기 중인 신청이 없어요.';
      requestModal.body.append(empty);
    }
  }

  async function loadRequests() {
    const result = await window.api.get(baseUrl() + '/join-requests');
    if (!result.ok || !Array.isArray(result.data)) {
      show('manage-area', false); // 멤버 관리 권한이 없으면 숨긴다
      return;
    }
    requests = result.data;
    show('manage-area', true);
    const list = $('request-list');
    list.replaceChildren();
    $('request-count').textContent = requests.length > 0 ? String(requests.length) + '건' : '';
    show('request-empty', requests.length === 0);
    requests.slice(0, INLINE).forEach(r => list.append(requestItem(r)));
    show('request-more', requests.length > INLINE);
    renderRequestModal();
  }

  async function decide(id, action) {
    const result = await window.api.post(baseUrl() + '/join-requests/' + encodeURIComponent(id) + '/' + action,
      undefined, { userAction: true });
    if (!result.ok) {
      await window.dialog.alert((result.data && result.data.message) || '처리하지 못했어요.');
    }
    loadRequests();
  }

  document.addEventListener('blog:loaded', (event) => {
    blog = event.detail;
    $('request-more').addEventListener('click', () => {
      requestModal = window.modal.open({ title: '참여 신청', onClose: () => { requestModal = null; } });
      renderRequestModal();
    });
    loadStatus();
    if (blog.myRole === 'OWNER' || blog.myRole === 'SUB_OWNER') {
      loadRequests();
    }
  });
})();
