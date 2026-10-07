// 프로필 /users/{닉네임} (SOC-01~03): 소개, 팔로워·팔로잉, 운영·참여 블로그, 팔로우 버튼.
// 사용자 입력(닉네임·소개)은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const state = { nickname: '', profile: null, listKind: null, page: 1 };

  function $(id) {
    return document.getElementById(id);
  }

  function avatarSrc(url) {
    return url && url.startsWith('/files/') ? url : '/images/avatar-default.svg';
  }

  function profileUrl(nickname) {
    return '/users/' + encodeURIComponent(nickname);
  }

  function renderBlogs(listId, emptyId, items) {
    const list = $(listId);
    list.replaceChildren();
    items.forEach(b => list.append(window.blogCard.create(b, { showRole: true, showVisibility: state.profile.me })));
    $(emptyId).classList.toggle('hidden', items.length > 0);
  }

  function renderFollow() {
    const p = state.profile;
    $('follower-count').textContent = String(p.followerCount);
    $('following-count').textContent = String(p.followingCount);
    const button = $('follow-button');
    button.textContent = p.following ? '팔로우 취소' : '팔로우';
    button.className = p.following ? 'secondary inline-button' : 'primary inline-button';
  }

  async function toggleFollow() {
    $('follow-message').textContent = '';
    const button = $('follow-button');
    button.disabled = true;
    try {
      const result = await window.api.post('/api/users/' + encodeURIComponent(state.nickname) + '/follow', undefined,
        { userAction: true });
      if (result.status === 401) {
        window.location.href = '/login.html';
        return;
      }
      if (!result.ok || !result.data) {
        $('follow-message').textContent = (result.data && result.data.message) || '처리하지 못했어요.';
        return;
      }
      state.profile.following = result.data.following;
      state.profile.followerCount = result.data.followerCount;
      renderFollow();
      if (state.listKind === 'followers') {
        loadList();
      }
    } finally {
      button.disabled = false;
    }
  }

  async function loadList() {
    const q = new URLSearchParams({ page: String(state.page), size: '20' });
    const result = await window.api.get('/api/users/' + encodeURIComponent(state.nickname) + '/' + state.listKind
      + '?' + q.toString());
    if (!result.ok || !result.data) {
      return;
    }
    const data = result.data;
    state.page = data.page;
    const list = $('follow-list');
    list.replaceChildren();
    data.items.forEach(u => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = profileUrl(u.nickname);
      a.className = 'user-link';
      const img = document.createElement('img');
      img.className = 'avatar';
      img.src = avatarSrc(u.profileImageUrl);
      img.alt = '';
      const name = document.createElement('span');
      name.textContent = u.nickname;
      a.append(img, name);
      li.append(a);
      list.append(li);
    });
    $('follow-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager.render($('follow-pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      loadList();
    });
  }

  function showList(kind) {
    state.listKind = kind;
    state.page = 1;
    $('follow-panel-title').textContent = kind === 'followers' ? '팔로워' : '팔로잉';
    $('follow-panel').classList.remove('hidden');
    loadList();
  }

  function renderBlock() {
    $('block-button').textContent = state.profile.blocked ? '차단 해제' : '차단';
  }

  async function toggleBlock() {
    if (!state.profile.blocked
      && !window.confirm(state.nickname + '님을 차단할까요? 서로의 팔로우가 끊기고 이 회원의 글과 댓글이 가려져요.')) {
      return;
    }
    const result = await window.api.post('/api/users/' + encodeURIComponent(state.nickname) + '/block', undefined,
      { userAction: true });
    if (!result.ok || !result.data) {
      $('follow-message').textContent = (result.data && result.data.message) || '처리하지 못했어요.';
      return;
    }
    state.profile.blocked = result.data.blocked;
    if (result.data.blocked) {
      state.profile.following = false;
      renderFollow();
    }
    renderBlock();
  }

  // 차단·신고는 로그인한 일반 회원에게만 (관리자 제외, D-90)
  document.addEventListener('header:user', (event) => {
    const enable = () => {
      if (!state.profile || state.profile.me || event.detail.role === 'ADMIN') {
        return;
      }
      renderBlock();
      $('block-button').classList.remove('hidden');
      $('report-user').classList.remove('hidden');
    };
    if (state.profile) {
      enable();
    } else {
      document.addEventListener('profile:loaded', enable, { once: true });
    }
  });

  document.addEventListener('DOMContentLoaded', async () => {
    const parts = window.location.pathname.split('/');
    state.nickname = decodeURIComponent(parts[2] || '');
    const result = await window.api.get('/api/users/' + encodeURIComponent(state.nickname), { userAction: true });
    if (!result.ok || !result.data) {
      $('profile-missing').classList.remove('hidden');
      return;
    }
    const p = result.data;
    state.profile = p;
    document.title = p.nickname + ' - One Blog';
    $('profile-nickname').textContent = p.nickname;
    $('profile-bio').textContent = p.bio || '';
    $('profile-image').src = avatarSrc(p.profileImageUrl);
    renderFollow();
    if (p.me) {
      $('edit-link').classList.remove('hidden');
    } else {
      $('follow-button').classList.remove('hidden');
      $('follow-button').addEventListener('click', toggleFollow);
    }
    $('show-followers').addEventListener('click', () => showList('followers'));
    $('show-following').addEventListener('click', () => showList('following'));
    renderBlogs('owned-list', 'owned-empty', p.ownedBlogs);
    renderBlogs('joined-list', 'joined-empty', p.joinedBlogs);
    $('block-button').addEventListener('click', toggleBlock);
    $('report-user').addEventListener('click', () => window.report.open({ targetType: 'PROFILE', nickname: p.nickname }));
    $('profile').classList.remove('hidden');
    document.dispatchEvent(new CustomEvent('profile:loaded'));
  });
})();
