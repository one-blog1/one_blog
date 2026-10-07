// 글쓰기 화면의 카테고리 고르기 (BRD-03). 블로그에 카테고리가 있을 때만 보인다.
// 글 고치기에서는 글의 카테고리를 미리 골라 둔다. 두 정보의 도착 순서는 정해져 있지 않다.
(function () {
  'use strict';

  let loaded = null;
  let wanted = null;

  function apply() {
    if (loaded && wanted !== null) {
      const select = document.getElementById('category');
      if (Array.from(select.options).some(o => o.value === String(wanted))) {
        select.value = String(wanted);
      }
    }
  }

  document.addEventListener('editor:blog', async (event) => {
    const blog = event.detail;
    if (!blog.myRole) {
      return;
    }
    const key = new URLSearchParams(window.location.search).get('key');
    const result = await window.api.get('/api/blogs/' + encodeURIComponent(blog.slug) + '/categories'
      + (key ? '?key=' + encodeURIComponent(key) : ''));
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const select = document.getElementById('category');
    result.data.forEach(c => {
      const option = document.createElement('option');
      option.value = String(c.id);
      option.textContent = c.name;
      select.append(option);
    });
    document.getElementById('category-field').classList.toggle('hidden', result.data.length === 0);
    loaded = true;
    apply();
  });

  document.addEventListener('editor:post', (event) => {
    wanted = event.detail.categoryId;
    apply();
  });
})();
