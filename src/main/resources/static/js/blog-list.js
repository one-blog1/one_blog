// 메인 블로그 목록 (BLG-02). 최신순·인기순, 한 페이지 10·20·30개, 번호 페이지 (D-06, D-76, D-89).
// 정렬·페이지는 주소창(?sort=&page=&size=)에도 남겨 새로고침·뒤로 가기에도 유지한다.
(function () {
  'use strict';

  const state = { sort: 'latest', page: 1, size: 10 };

  function readQuery() {
    const q = new URLSearchParams(window.location.search);
    state.sort = q.get('sort') === 'popular' ? 'popular' : 'latest';
    state.page = Math.max(1, parseInt(q.get('page'), 10) || 1);
    const size = parseInt(q.get('size'), 10);
    state.size = [10, 20, 30].includes(size) ? size : 10;
  }

  function writeQuery() {
    // 블로그 둘러보기는 메인 피드 화면에 있으므로 view=feed를 함께 남긴다 (D-100)
    const q = new URLSearchParams({ view: 'feed', sort: state.sort, page: String(state.page), size: String(state.size) });
    window.history.replaceState(null, '', '/?' + q.toString());
  }

  function renderControls() {
    document.querySelectorAll('[data-sort]').forEach(b => {
      b.setAttribute('aria-pressed', String(b.dataset.sort === state.sort));
    });
    document.getElementById('page-size').value = String(state.size);
  }

  function renderPagination(totalPages) {
    const nav = document.getElementById('pagination');
    nav.replaceChildren();
    if (totalPages <= 1) {
      return;
    }
    // 현재 페이지 주변 최대 10개 번호
    const start = Math.max(1, Math.min(state.page - 4, totalPages - 9));
    const end = Math.min(totalPages, start + 9);
    const add = (label, page, disabled, current) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = label;
      b.disabled = disabled;
      if (current) {
        b.setAttribute('aria-current', 'page');
      }
      b.addEventListener('click', () => go(page));
      nav.append(b);
    };
    add('이전', state.page - 1, state.page <= 1, false);
    for (let p = start; p <= end; p++) {
      add(String(p), p, false, p === state.page);
    }
    add('다음', state.page + 1, state.page >= totalPages, false);
  }

  async function load() {
    const list = document.getElementById('blog-list');
    const message = document.getElementById('list-message');
    message.textContent = '불러오는 중…';
    const q = new URLSearchParams({ sort: state.sort, page: String(state.page), size: String(state.size) });
    const result = await window.api.get('/api/blogs?' + q.toString());
    list.replaceChildren();
    if (!result.ok || !result.data) {
      message.textContent = '목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
      return;
    }
    const data = result.data;
    // 서버가 바로잡은 값(범위 밖 페이지 등)을 따른다
    state.sort = data.sort;
    state.page = data.page;
    state.size = data.size;
    writeQuery();
    renderControls();

    message.textContent = data.totalItems === 0 ? '아직 공개된 블로그가 없습니다. 첫 블로그를 만들어 보세요.' : '';
    data.items.forEach(b => list.append(window.blogCard.create(b)));
    renderPagination(data.totalPages);
  }

  function go(page) {
    state.page = page;
    load();
    window.scrollTo({ top: 0 });
  }

  document.addEventListener('DOMContentLoaded', () => {
    readQuery();
    renderControls();
    document.querySelectorAll('[data-sort]').forEach(b => {
      b.addEventListener('click', () => {
        state.sort = b.dataset.sort;
        state.page = 1;
        load();
      });
    });
    document.getElementById('page-size').addEventListener('change', (e) => {
      state.size = parseInt(e.target.value, 10);
      state.page = 1;
      load();
    });
    load();
  });
})();
