// 메인 배너 자리 (BRD-12, D-132): "내 블로그"·"메인 피드" 탭 맨 위. "참여 신청" 탭에서는 숨기고 넘김도 멈춘다.
// 어느 탭인지는 home.js가 home:view 이벤트로 알려 준다. 비회원은 메인 피드만 보므로 늘 보인다.
(function () {
  'use strict';

  let controller = null;
  let view = 'home';
  let requested = false;

  function apply() {
    const root = document.getElementById('main-banner');
    const on = view !== 'requests';
    root.classList.toggle('off-view', !on);
    if (controller) {
      controller.setActive(on);
    }
  }

  async function load() {
    if (requested) {
      return;
    }
    requested = true;
    const result = await window.api.get('/api/banners');
    const root = document.getElementById('main-banner');
    controller = window.banner.mount(root, result.ok && result.data ? result.data.items : []);
    apply();
  }

  document.addEventListener('home:view', (event) => {
    view = event.detail;
    apply();
    load();
  });
})();
