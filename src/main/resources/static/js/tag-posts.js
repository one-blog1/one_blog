// 같은 태그의 글 (BRD-04). 주소 /tags/{태그}. 여러 블로그의 글이라 블로그 이름을 함께 보여준다.
(function () {
  'use strict';

  const state = { tag: '', page: 1, size: 10 };

  function $(id) {
    return document.getElementById(id);
  }

  function postHref(card) {
    return '/blog/' + encodeURIComponent(card.blogSlug) + '/posts/' + encodeURIComponent(card.id);
  }

  async function load() {
    const q = new URLSearchParams({ page: String(state.page), size: String(state.size) });
    const result = await window.api.get('/api/tags/' + encodeURIComponent(state.tag) + '/posts?' + q.toString());
    if (!result.ok || !result.data) {
      return;
    }
    const data = result.data;
    state.page = data.page;
    const list = $('tag-posts');
    list.replaceChildren();
    data.items.forEach(card => list.append(window.postItem.create(card, postHref(card), { blogName: card.blogName })));
    $('tag-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager.render($('tag-pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      load();
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const parts = window.location.pathname.split('/');
    state.tag = decodeURIComponent(parts[2] || '');
    $('tag-title').textContent = '#' + state.tag;
    document.title = '#' + state.tag + ' - One Blog';
    $('tag-size').addEventListener('change', (e) => {
      state.size = parseInt(e.target.value, 10);
      state.page = 1;
      load();
    });
    load();
  });
})();
