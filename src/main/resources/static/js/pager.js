// 번호 페이지 버튼 (D-76). 현재 페이지 주변 최대 10개 번호와 이전·다음.
(function () {
  'use strict';

  /**
   * @param {HTMLElement} nav 버튼을 넣을 곳
   * @param {number} page 현재 페이지 (1부터)
   * @param {number} totalPages 전체 페이지 수
   * @param {(page:number) => void} go 페이지를 고르면 부를 함수
   */
  function render(nav, page, totalPages, go) {
    nav.replaceChildren();
    if (totalPages <= 1) {
      return;
    }
    const start = Math.max(1, Math.min(page - 4, totalPages - 9));
    const end = Math.min(totalPages, start + 9);
    const add = (label, target, disabled, current) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = label;
      b.disabled = disabled;
      if (current) {
        b.setAttribute('aria-current', 'page');
      }
      b.addEventListener('click', () => go(target));
      nav.append(b);
    };
    add('이전', page - 1, page <= 1, false);
    for (let p = start; p <= end; p++) {
      add(String(p), p, false, p === page);
    }
    add('다음', page + 1, page >= totalPages, false);
  }

  window.pager = { render: render };
})();
