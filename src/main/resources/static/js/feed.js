// 메인 피드 (BRD-09, D-65): 최신·인기 글은 누구나, 팔로우·구독 탭은 로그인한 회원만.
(function () {
  'use strict';

  const EMPTY = {
    latest: '아직 글이 없어요.',
    popular: '아직 글이 없어요.',
    following: '팔로우한 회원의 새 글이 없어요.',
    subscriptions: '구독한 블로그의 새 글이 없어요.'
  };
  const state = { tab: 'latest', page: 1 };

  function $(id) {
    return document.getElementById(id);
  }

  function tabs() {
    return document.querySelectorAll('[data-tab]');
  }

  async function load() {
    const q = new URLSearchParams({ tab: state.tab, page: String(state.page), size: '10' });
    const result = await window.api.get('/api/feed?' + q.toString());
    if (!result.ok || !result.data) {
      return;
    }
    const data = result.data;
    state.page = data.page;
    const list = $('feed-list');
    list.replaceChildren();
    data.items.forEach(c => list.append(window.postItem.create(c,
      '/blog/' + encodeURIComponent(c.blogSlug) + '/posts/' + encodeURIComponent(c.id), { blogName: c.blogName })));
    $('feed-empty').textContent = EMPTY[state.tab];
    $('feed-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager.render($('feed-pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      load();
    });
  }

  document.addEventListener('header:user', (event) => {
    if (event.detail.role !== 'ADMIN') {
      tabs().forEach(t => t.classList.remove('hidden'));
    }
  });

  document.addEventListener('DOMContentLoaded', () => {
    tabs().forEach(t => t.addEventListener('click', () => {
      state.tab = t.dataset.tab;
      state.page = 1;
      tabs().forEach(other => other.setAttribute('aria-selected', String(other === t)));
      load();
    }));
    load();
  });
})();
