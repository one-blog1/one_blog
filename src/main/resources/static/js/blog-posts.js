// 블로그 첫 화면의 글 목록 (BRD-02). 공지는 위에, 일반 글은 최신순 번호 페이지 (D-06, D-76).
(function () {
  'use strict';

  const state = { page: 1, size: 10, category: null };
  let blog = null;

  const $ = window.ui.$;

  function keyParam() {
    return new URLSearchParams(window.location.search).get('key');
  }

  function postHref(id) {
    const key = keyParam();
    return '/blog/' + encodeURIComponent(blog.slug) + '/posts/' + encodeURIComponent(id)
      + (key ? '?key=' + encodeURIComponent(key) : '');
  }

  async function load() {
    const q = new URLSearchParams({ page: String(state.page), size: String(state.size) });
    const key = keyParam();
    if (key) {
      q.set('key', key);
    }
    if (state.category) {
      q.set('category', String(state.category));
    }
    const result = await window.api.get('/api/blogs/' + encodeURIComponent(blog.slug) + '/posts?' + q.toString());
    if (!result.ok || !result.data) {
      return;
    }
    const data = result.data;
    state.page = data.page;
    state.size = data.size;
    $('post-size').value = String(state.size);

    const notices = $('notice-list');
    notices.replaceChildren();
    data.notices.forEach(p => notices.append(window.postItem.create(p, postHref(p.id))));
    const list = $('post-list');
    list.replaceChildren();
    data.items.forEach(p => list.append(window.postItem.create(p, postHref(p.id))));
    $('post-empty').classList.toggle('hidden', data.items.length > 0 || data.notices.length > 0);
    window.pager.render($('post-pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      load();
    });
  }

  document.addEventListener('blog:loaded', (event) => {
    blog = event.detail;
    if (blog.myRole) {
      const write = $('write-link');
      const key = keyParam();
      write.href = '/blog/' + encodeURIComponent(blog.slug) + '/write' + (key ? '?key=' + encodeURIComponent(key) : '');
      write.classList.remove('hidden');
    }
    $('post-size').addEventListener('change', (e) => {
      state.size = parseInt(e.target.value, 10);
      state.page = 1;
      load();
    });
    document.addEventListener('category:selected', (e) => {
      state.category = e.detail;
      state.page = 1;
      load();
    });
    load();
  });
})();
