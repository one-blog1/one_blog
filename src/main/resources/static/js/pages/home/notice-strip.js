// 메인 화면 위 공지 줄 (BRD-10, D-122). 최신 공지 5개를 한 줄에 하나씩, 2초마다 다음 공지로 넘긴다.
// - 마우스를 올리거나 키보드로 들어가면 멈추고, 벗어나면 다시 돈다 (움직이는 내용은 멈출 수 있어야 함)
// - 움직임 줄이기 설정이면 효과 없이 글자만 바뀐다 (app.css가 처리)
// - 화면 읽기 프로그램이 2초마다 읽지 않게 aria-live="off". 전체는 "전체 보기"(/notice.html)에서 본다
(function () {
  'use strict';

  const INTERVAL_MS = 2000;
  const MAX_ITEMS = 5;

  document.addEventListener('DOMContentLoaded', async () => {
    const result = await window.api.get('/api/notices?size=' + MAX_ITEMS);
    if (!result.ok || !result.data || result.data.items.length === 0) {
      return;
    }
    const items = result.data.items.slice(0, MAX_ITEMS);
    const strip = document.getElementById('notice-strip');
    const current = document.getElementById('notice-current');
    const count = document.getElementById('notice-count');
    let index = 0;
    let timer = null;
    let paused = false;

    function show(i) {
      const n = items[i];
      const tag = window.ui.el('span', 'notice-kind kind-' + String(n.category || 'GENERAL').toLowerCase(),
        n.categoryLabel || '일반');
      const title = window.ui.el('span', 'notice-current-title', n.title);
      current.href = '/notice.html?id=' + encodeURIComponent(n.id);
      current.replaceChildren(tag, title);
      current.setAttribute('aria-label', (n.categoryLabel || '일반') + ' 공지: ' + n.title);
      count.textContent = items.length > 1 ? (i + 1) + ' / ' + items.length : '';
      // 다시 그릴 때마다 들어오는 효과를 처음부터
      current.classList.remove('entering');
      void current.offsetWidth;
      current.classList.add('entering');
    }

    function start() {
      if (timer || paused || items.length < 2) {
        return;
      }
      timer = window.setInterval(() => {
        index = (index + 1) % items.length;
        show(index);
      }, INTERVAL_MS);
    }

    function stop() {
      window.clearInterval(timer);
      timer = null;
    }

    function pause() {
      paused = true;
      stop();
    }

    function resume() {
      // 키보드 초점이 아직 줄 안에 있으면 그대로 멈춰 둔다
      if (strip.matches(':hover') || strip.contains(document.activeElement)) {
        return;
      }
      paused = false;
      start();
    }

    strip.addEventListener('mouseenter', pause);
    strip.addEventListener('mouseleave', resume);
    strip.addEventListener('focusin', pause);
    strip.addEventListener('focusout', () => window.setTimeout(resume, 0));
    // 다른 탭에 가 있으면 돌리지 않는다
    document.addEventListener('visibilitychange', () => (document.hidden ? stop() : start()));

    show(0);
    strip.classList.remove('hidden');
    start();
  });
})();
