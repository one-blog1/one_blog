// 메인 첫 화면 (BLG-06, BRD-09, D-100)
// - 로그인한 회원: 공지 아래 [내 블로그][메인 피드]. 기본은 "내 블로그"(참여 중인 블로그를 아이콘과 이름으로)
// - 비회원·관리자: 메인 피드만
// 주소 /?view=feed 이면 메인 피드로 연다. 로고(/)를 누르면 늘 "내 블로그"로 돌아온다.
// 블로그 이름은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const state = { member: false, loaded: false };

  function $(id) {
    return document.getElementById(id);
  }

  function requestedView() {
    const q = new URLSearchParams(window.location.search);
    if (q.get('view') === 'requests') {
      return 'requests';
    }
    return q.get('view') === 'feed' || q.has('sort') || q.has('page') ? 'feed' : 'home';
  }

  function show(view) {
    const target = state.member ? view : 'feed';
    $('home-view').classList.toggle('hidden', target !== 'home');
    $('feed-view').classList.toggle('hidden', target !== 'feed');
    $('requests-view').classList.toggle('hidden', target !== 'requests');
    document.querySelectorAll('#view-switch [data-view]').forEach(b => {
      b.setAttribute('aria-pressed', String(b.dataset.view === target));
    });
    if (target === 'home' && !state.loaded) {
      loadMyBlogs();
    }
    if (target === 'requests') {
      loadRequests();
    }
  }

  function choose(view) {
    window.history.replaceState(null, '', view === 'home' ? '/' : '/?view=' + view);
    show(view);
  }

  function icon(blog) {
    const li = document.createElement('li');
    const a = document.createElement('a');
    a.className = 'blog-icon';
    a.href = window.blogCard.blogUrl(blog.slug);
    a.append(window.blogCard.cover(blog, 'blog-icon-cover'));
    const name = document.createElement('span');
    name.className = 'blog-icon-name';
    name.textContent = blog.name;
    a.append(name);
    if (blog.role === 'OWNER') {
      const role = document.createElement('span');
      role.className = 'blog-icon-role';
      role.textContent = '블로그장';
      a.append(role);
    }
    li.append(a);
    return li;
  }

  function createIcon() {
    const li = document.createElement('li');
    const a = document.createElement('a');
    a.className = 'blog-icon create';
    a.href = '/blog-new.html';
    const plus = document.createElement('span');
    plus.className = 'blog-icon-cover blog-icon-plus';
    plus.setAttribute('aria-hidden', 'true');
    plus.textContent = '+';
    const name = document.createElement('span');
    name.className = 'blog-icon-name';
    name.textContent = '블로그 만들기';
    a.append(plus, name);
    li.append(a);
    return li;
  }

  async function loadMyBlogs() {
    state.loaded = true;
    const message = $('home-message');
    message.className = 'message';
    message.textContent = '불러오는 중…';
    const result = await window.api.get('/api/me/blogs');
    if (!result.ok || !result.data) {
      message.className = 'message error';
      message.textContent = '내 블로그를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
      state.loaded = false;
      return;
    }
    message.textContent = '';
    // 같은 블로그가 두 번 나오지 않게 (만든 블로그가 먼저)
    const seen = new Set();
    const blogs = [].concat(result.data.owned || [], result.data.joined || [])
      .filter(b => !seen.has(b.slug) && seen.add(b.slug));
    const list = $('my-blog-icons');
    list.replaceChildren();
    blogs.forEach(b => list.append(icon(b)));
    if (blogs.length > 0) {
      list.append(createIcon());
    }
    list.classList.toggle('hidden', blogs.length === 0);
    $('home-empty').classList.toggle('hidden', blogs.length > 0);
    $('home-posts').classList.toggle('hidden', blogs.length === 0);
    if (blogs.length > 0) {
      loadHomePosts(1);
    }
  }

  // 참여 중인 블로그에 올라온 새 글 (D-113)
  async function loadHomePosts(page) {
    const q = new URLSearchParams({ tab: 'myblogs', page: String(page), size: '10' });
    const result = await window.api.get('/api/feed?' + q.toString());
    if (!result.ok || !result.data) {
      return;
    }
    const data = result.data;
    const list = $('home-post-list');
    list.replaceChildren();
    data.items.forEach(c => list.append(window.postItem.create(c,
      '/blog/' + encodeURIComponent(c.blogSlug) + '/posts/' + encodeURIComponent(c.id), { blogName: c.blogName })));
    $('home-post-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager.render($('home-post-pagination'), data.page, data.totalPages, loadHomePosts);
  }

  // 내가 신청하고 기다리는 블로그. 여기서 취소한다 (D-111)
  async function loadRequests() {
    const message = $('requests-message');
    message.className = 'message';
    message.textContent = '불러오는 중…';
    const result = await window.api.get('/api/me/join-requests');
    if (!result.ok || !Array.isArray(result.data)) {
      message.className = 'message error';
      message.textContent = '참여 신청을 불러오지 못했어요.';
      return;
    }
    message.textContent = '';
    const list = $('my-request-list');
    list.replaceChildren();
    result.data.forEach(r => {
      const li = document.createElement('li');
      li.className = 'my-request';
      const link = document.createElement('a');
      link.className = 'my-request-blog';
      link.href = window.blogCard.blogUrl(r.blogSlug);
      link.append(window.blogCard.cover({ slug: r.blogSlug, name: r.blogName, coverImageUrl: r.coverImageUrl },
        'my-request-cover'));
      const text = document.createElement('span');
      const name = document.createElement('strong');
      name.textContent = r.blogName;
      const when = document.createElement('span');
      when.className = 'hint';
      when.textContent = new Date(r.requestedAt).toLocaleDateString('ko-KR') + ' 신청';
      text.append(name, when);
      link.append(text);
      const cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'secondary';
      cancel.textContent = '신청 취소';
      cancel.addEventListener('click', async () => {
        if (!await window.dialog.confirm('「' + r.blogName + '」 참여 신청을 취소할까요?')) {
          return;
        }
        const res = await window.api.delete('/api/me/join-requests/' + encodeURIComponent(r.id), { userAction: true });
        if (!res.ok) {
          message.className = 'message error';
          message.textContent = (res.data && res.data.message) || '취소하지 못했어요.';
        }
        loadRequests();
      });
      li.append(link, cancel);
      list.append(li);
    });
    $('requests-empty').classList.toggle('hidden', result.data.length > 0);
  }

  document.addEventListener('header:user', (event) => {
    state.member = event.detail.role !== 'ADMIN';
    $('view-switch').classList.toggle('hidden', !state.member);
    show(requestedView());
  });

  document.addEventListener('header:guest', () => {
    state.member = false;
    show('feed');
  });

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('#view-switch [data-view]').forEach(b => {
      b.addEventListener('click', () => choose(b.dataset.view));
    });
    $('home-to-feed').addEventListener('click', () => choose('feed'));
  });
})();
