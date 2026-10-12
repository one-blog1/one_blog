// 블로그 구독 (SOC-01, D-50). 일부 공개 블로그를 링크로 들어와 구독하면 그 뒤로 링크 없이 들어올 수 있다.
// 비회원에게는 버튼을 누르면 로그인 화면으로 보낸다. 관리자에게는 보이지 않는다 (D-90).
(function () {
  'use strict';

  let blog = null;
  let user = null;

  const $ = window.ui.$;

  function render() {
    $('subscribe-button').textContent = blog.subscribed ? '구독 중 (취소)' : '구독하기';
    $('subscriber-count').textContent = '구독자 ' + blog.subscriberCount + '명';
  }

  async function toggle() {
    if (!user) {
      window.location.href = '/login.html';
      return;
    }
    $('subscribe-message').textContent = '';
    const key = new URLSearchParams(window.location.search).get('key');
    const result = await window.api.post('/api/blogs/' + encodeURIComponent(blog.slug) + '/subscription'
      + (key ? '?key=' + encodeURIComponent(key) : ''), undefined, { userAction: true });
    if (!result.ok || !result.data) {
      $('subscribe-message').textContent = (result.data && result.data.message) || '처리하지 못했어요.';
      return;
    }
    blog.subscribed = result.data.subscribed;
    blog.subscriberCount = result.data.subscriberCount;
    render();
  }

  function show() {
    if (!blog || (user && user.role === 'ADMIN')) {
      return;
    }
    render();
    $('subscribe-area').classList.remove('hidden');
  }

  document.addEventListener('header:user', (event) => {
    user = event.detail;
    if (user.role === 'ADMIN') {
      $('subscribe-area').classList.add('hidden');
    }
  });

  document.addEventListener('blog:loaded', (event) => {
    blog = event.detail;
    $('subscribe-button').addEventListener('click', toggle);
    show();
  });
})();
