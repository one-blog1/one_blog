// 통합 검색 /search?q= (BRD-08, BLG-03, 6.1). 글과 블로그를 탭으로 나누고, 글은 분류바로 범위를 고른다.
// 사용자 입력은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const state = { q: '', type: 'post', target: 'all', sort: 'relevance', page: 1, size: 10, loggedIn: false };

  function $(id) {
    return document.getElementById(id);
  }

  function validQuery(q) {
    const length = Array.from(q).length;
    return length >= 2 && length <= 20 && /[\p{L}\p{N}]/u.test(q);
  }

  function pressed(barId, attr, value) {
    $(barId).querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset[attr] === value)));
  }

  function postHref(card) {
    return '/blog/' + encodeURIComponent(card.blogSlug) + '/posts/' + encodeURIComponent(card.id);
  }

  function url(type, page, size) {
    const q = new URLSearchParams({ q: state.q, sort: state.sort, page: String(page), size: String(size) });
    if (type === 'post') {
      q.set('target', state.target);
    }
    return '/api/search/' + (type === 'post' ? 'posts' : 'blogs') + '?' + q.toString();
  }

  async function load() {
    $('search-empty').classList.add('hidden');
    const result = await window.api.get(url(state.type, state.page, state.size), { userAction: true });
    if (!result.ok || !result.data) {
      const data = result.data || {};
      const field = Array.isArray(data.fieldErrors) && data.fieldErrors[0] ? data.fieldErrors[0].message : null;
      document.querySelector('[data-error-for="q"]').textContent = field || data.message || '검색하지 못했어요.';
      return;
    }
    const data = result.data;
    state.page = data.page;
    $((state.type === 'post' ? 'post' : 'blog') + '-count').textContent = String(data.totalItems);
    const posts = $('post-results');
    const blogs = $('blog-results');
    posts.replaceChildren();
    blogs.replaceChildren();
    posts.classList.toggle('hidden', state.type !== 'post');
    blogs.classList.toggle('hidden', state.type !== 'blog');
    if (state.type === 'post') {
      data.items.forEach(c => posts.append(window.postItem.create(c, postHref(c), { blogName: c.blogName })));
    } else {
      data.items.forEach(b => blogs.append(window.blogCard.create(b)));
    }
    $('search-empty').classList.toggle('hidden', data.items.length > 0);
    window.pager.render($('search-pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      load();
    });
    if (state.loggedIn) {
      loadRecent();
    }
  }

  async function loadOtherCount() {
    const other = state.type === 'post' ? 'blog' : 'post';
    const result = await window.api.get(url(other, 1, 10));
    if (result.ok && result.data) {
      $(other + '-count').textContent = String(result.data.totalItems);
    }
  }

  function search() {
    document.querySelector('[data-error-for="q"]').textContent = '';
    const q = $('search-q').value.trim().replace(/\s+/g, ' ');
    if (!validQuery(q)) {
      document.querySelector('[data-error-for="q"]').textContent = '검색어는 글자나 숫자를 넣어 2~20자로 입력해 주세요.';
      return;
    }
    state.q = q;
    state.page = 1;
    window.history.replaceState(null, '', '/search?q=' + encodeURIComponent(q));
    document.title = q + ' - 검색 - One Blog';
    load();
    loadOtherCount();
  }

  async function loadRecent() {
    const result = await window.api.get('/api/me/recent-searches');
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const list = $('recent-list');
    list.replaceChildren();
    result.data.forEach(r => {
      const li = document.createElement('li');
      li.className = 'tag';
      const go = document.createElement('button');
      go.type = 'button';
      go.className = 'link-button';
      go.textContent = r.keyword;
      go.addEventListener('click', () => {
        $('search-q').value = r.keyword;
        search();
      });
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'tag-remove';
      remove.textContent = '×';
      remove.setAttribute('aria-label', r.keyword + ' 지우기');
      remove.addEventListener('click', async () => {
        await window.api.delete('/api/me/recent-searches/' + r.id, { userAction: true });
        loadRecent();
      });
      li.append(go, remove);
      list.append(li);
    });
    $('recent-area').classList.toggle('hidden', result.data.length === 0);
  }

  function selectType(type) {
    state.type = type;
    state.page = 1;
    $('tab-post').setAttribute('aria-selected', String(type === 'post'));
    $('tab-blog').setAttribute('aria-selected', String(type === 'blog'));
    $('target-bar').classList.toggle('hidden', type !== 'post');
    if (state.q) {
      load();
    }
  }

  document.addEventListener('header:user', (event) => {
    state.loggedIn = event.detail.role !== 'ADMIN';
    if (state.loggedIn) {
      loadRecent();
    }
  });

  document.addEventListener('DOMContentLoaded', () => {
    $('search-form').addEventListener('submit', (e) => {
      e.preventDefault();
      search();
    });
    $('tab-post').addEventListener('click', () => selectType('post'));
    $('tab-blog').addEventListener('click', () => selectType('blog'));
    $('target-bar').addEventListener('click', (e) => {
      const target = e.target.closest('button');
      if (target) {
        state.target = target.dataset.target;
        pressed('target-bar', 'target', state.target);
        if (state.q) {
          state.page = 1;
          load();
        }
      }
    });
    $('sort-bar').addEventListener('click', (e) => {
      const button = e.target.closest('button');
      if (button) {
        state.sort = button.dataset.sort;
        pressed('sort-bar', 'sort', state.sort);
        if (state.q) {
          state.page = 1;
          load();
        }
      }
    });
    $('search-size').addEventListener('change', (e) => {
      state.size = parseInt(e.target.value, 10);
      state.page = 1;
      if (state.q) {
        load();
      }
    });
    $('recent-clear').addEventListener('click', async () => {
      await window.api.delete('/api/me/recent-searches', { userAction: true });
      loadRecent();
    });
    const q = new URLSearchParams(window.location.search).get('q');
    if (q) {
      $('search-q').value = q;
      search();
    }
  });
})();
