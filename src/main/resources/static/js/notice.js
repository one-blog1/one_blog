// 메인 공지 보기 (BRD-10). 본문은 서버가 걸러 보낸 HTML이라 innerHTML로 넣는다 (SEC-06). 나머지는 textContent.
(async () => {
  'use strict';
  const id = new URLSearchParams(window.location.search).get('id') || '';
  const result = await window.api.get('/api/notices/' + encodeURIComponent(id));
  if (!result.ok || !result.data) {
    document.getElementById('notice-missing').classList.remove('hidden');
    return;
  }
  const n = result.data;
  document.title = n.title + ' - One Blog';
  document.getElementById('notice-title').textContent = n.title;
  document.getElementById('notice-meta').textContent = n.authorName + ' · ' + new Date(n.createdAt).toLocaleString('ko-KR');
  document.getElementById('notice-body').innerHTML = n.contentHtml;
  document.getElementById('notice').classList.remove('hidden');
})();
