// 블로그 첫 화면의 글 목록 (BRD-02). 공지는 위에, 일반 글은 번호 페이지 (D-06, D-76).
// 고른 카테고리(전체 포함) 안에서 최신순·인기순(좋아요 많은 순, D-89)으로 본다 (D-129).
// 카테고리·정렬·페이지는 주소(?category=&sort=&page=)에 남겨 글을 읽고 뒤로 와도 그대로 보인다. 공유 링크 key는 그대로 둔다.
(function () {
  'use strict';

  const state = { page: 1, size: 10, category: null, sort: 'latest' };
  let blog = null;

  const $ = window.ui.$;

  function keyParam() {
    return new URLSearchParams(window.location.search).get('key');
  }

  function postHref(id) {
    return window.ui.postHref(blog.slug, id, window.ui.keyQuery());
  }

  function readUrl() {
    const q = new URLSearchParams(window.location.search);
    const category = parseInt(q.get('category'), 10);
    state.category = Number.isInteger(category) && category > 0 ? category : null;
    state.sort = q.get('sort') === 'popular' ? 'popular' : 'latest';
    state.page = Math.max(1, parseInt(q.get('page'), 10) || 1);
  }

  function writeUrl() {
    const q = new URLSearchParams(window.location.search);
    const set = (name, value) => (value ? q.set(name, value) : q.delete(name));
    set('category', state.category ? String(state.category) : '');
    set('sort', state.sort === 'popular' ? 'popular' : '');
    set('page', state.page > 1 ? String(state.page) : '');
    const query = q.toString();
    window.history.replaceState(null, '', window.location.pathname + (query ? '?' + query : '') + window.location.hash);
  }

  function markSort() {
    document.querySelectorAll('[data-post-sort]').forEach(b =>
      b.setAttribute('aria-pressed', String(b.dataset.postSort === state.sort)));
  }

  async function load() {
    const q = new URLSearchParams({ page: String(state.page), size: String(state.size), sort: state.sort });
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
    writeUrl();
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
    document.querySelectorAll('[data-post-sort]').forEach(b => b.addEventListener('click', () => {
      if (state.sort === b.dataset.postSort) {
        return;
      }
      state.sort = b.dataset.postSort;
      state.page = 1;
      markSort();
      load();
    }));
    readUrl();
    markSort();
    load();
  });
})();
