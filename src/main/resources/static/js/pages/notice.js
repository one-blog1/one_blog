// 메인 공지 (BRD-10, D-122). ?id가 있으면 그 공지 내용, 없으면 종류별 목록(?kind=UPDATE, ?page=2).
// 본문은 서버가 걸러 보낸 HTML이라 innerHTML로 넣는다 (SEC-06). 나머지는 모두 textContent.
(function () {
  'use strict';

  const $ = window.ui.$;
  const el = window.ui.el;
  const params = new URLSearchParams(window.location.search);

  /** 종류 이름표. 종류마다 class가 붙어 점검은 눈에 띄게 (app.css .notice-kind). */
  function kindTag(category, label) {
    return el('span', 'notice-kind kind-' + String(category || 'GENERAL').toLowerCase(), label || '일반');
  }

  async function showDetail(id) {
    const result = await window.api.get('/api/notices/' + encodeURIComponent(id));
    if (!result.ok || !result.data) {
      $('notice-missing').classList.remove('hidden');
      return;
    }
    const n = result.data;
    document.title = n.title + ' - One Blog';
    $('notice-kind').replaceWith(kindTag(n.category, n.categoryLabel));
    $('notice-title').textContent = n.title;
    let meta = n.authorName + ' · ' + window.ui.formatDateTime(n.createdAt);
    if (n.edited) {
      meta += ' · 수정됨 ' + window.ui.formatDateTime(n.updatedAt);
    }
    $('notice-meta').textContent = meta;
    $('notice-body').innerHTML = n.contentHtml;
    // 목록에서 왔으면 보던 종류·페이지로 돌아간다
    const back = new URLSearchParams();
    ['kind', 'page'].forEach(k => {
      if (params.get(k)) {
        back.set(k, params.get(k));
      }
    });
    $('notice-back').href = '/notice.html' + (back.toString() ? '?' + back.toString() : '');
    $('notice').classList.remove('hidden');
  }

  const state = { kind: params.get('kind') || '', page: Number(params.get('page')) || 1 };

  function syncUrl() {
    const q = new URLSearchParams();
    if (state.kind) {
      q.set('kind', state.kind);
    }
    if (state.page > 1) {
      q.set('page', String(state.page));
    }
    history.replaceState(null, '', '/notice.html' + (q.toString() ? '?' + q.toString() : ''));
  }

  async function loadList() {
    document.querySelectorAll('#notice-kinds [data-kind]').forEach(tab =>
      tab.setAttribute('aria-selected', String(tab.dataset.kind === state.kind)));
    const q = new URLSearchParams({ page: String(state.page), size: '20' });
    if (state.kind) {
      q.set('category', state.kind);
    }
    const result = await window.api.get('/api/notices?' + q.toString());
    const list = $('notice-items');
    list.replaceChildren();
    const items = result.ok && result.data ? result.data.items : [];
    const backQuery = new URLSearchParams();
    if (state.kind) {
      backQuery.set('kind', state.kind);
    }
    if (state.page > 1) {
      backQuery.set('page', String(state.page));
    }
    items.forEach(n => {
      const li = el('li', 'notice-row');
      const a = el('a', 'notice-row-title', n.title);
      a.href = '/notice.html?id=' + encodeURIComponent(n.id) + (backQuery.toString() ? '&' + backQuery.toString() : '');
      li.append(kindTag(n.category, n.categoryLabel), a, el('span', 'notice-row-date', window.ui.formatDate(n.createdAt)));
      list.append(li);
    });
    $('notice-empty').textContent = state.kind ? '이 종류의 공지가 없어요.' : '아직 공지가 없어요.';
    $('notice-empty').classList.toggle('hidden', items.length > 0);
    const data = result.data || { page: 1, totalPages: 1 };
    window.pager.render($('notice-pagination'), data.page, data.totalPages, (page) => {
      state.page = page;
      syncUrl();
      loadList();
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const id = params.get('id');
    if (id) {
      showDetail(id);
      return;
    }
    $('notice-list-view').classList.remove('hidden');
    document.querySelectorAll('#notice-kinds [data-kind]').forEach(tab => {
      tab.addEventListener('click', () => {
        state.kind = tab.dataset.kind;
        state.page = 1;
        syncUrl();
        loadList();
      });
    });
    loadList();
  });
})();
