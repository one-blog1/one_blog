// 메인 화면 위 공지사항 (BRD-10). 최근 3개.
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', async () => {
    const result = await window.api.get('/api/notices?size=10');
    if (!result.ok || !result.data || result.data.items.length === 0) {
      return;
    }
    const list = document.getElementById('notice-links');
    result.data.items.slice(0, 3).forEach(n => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = '/notice.html?id=' + encodeURIComponent(n.id);
      a.textContent = n.title;
      li.append(a);
      list.append(li);
    });
    document.getElementById('notice-strip').classList.remove('hidden');
  });
})();
