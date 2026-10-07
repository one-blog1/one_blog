// 블로그 카테고리 (BRD-03): 카테고리별로 글 보기와, 글 관리 권한이 있는 사람의 카테고리 관리.
// 고른 카테고리는 'category:selected' 이벤트로 글 목록(blog-posts.js)에 알린다 (null = 전체).
(function () {
  'use strict';

  let blog = null;
  let categories = [];
  let selected = null;

  function $(id) {
    return document.getElementById(id);
  }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = text;
    }
    return node;
  }

  function keyQuery() {
    const key = new URLSearchParams(window.location.search).get('key');
    return key ? '?key=' + encodeURIComponent(key) : '';
  }

  function canManage() {
    return !!(blog && blog.permissions && blog.permissions.canManagePosts);
  }

  function select(id) {
    selected = id;
    renderNav();
    document.dispatchEvent(new CustomEvent('category:selected', { detail: id }));
  }

  function navButton(label, id) {
    const button = el('button', null, label);
    button.type = 'button';
    button.setAttribute('aria-pressed', String(selected === id));
    button.addEventListener('click', () => select(id));
    return button;
  }

  function renderNav() {
    const nav = $('category-nav');
    nav.replaceChildren();
    nav.append(navButton('전체', null));
    categories.forEach(c => nav.append(navButton(c.name + ' (' + c.postCount + ')', c.id)));
    $('category-area').classList.toggle('hidden', categories.length === 0 && !canManage());
  }

  function showError(result, fallback) {
    const data = result.data || {};
    const field = Array.isArray(data.fieldErrors) && data.fieldErrors.length > 0 ? data.fieldErrors[0].message : null;
    $('category-message').textContent = field || data.message || fallback;
  }

  async function rename(category, input) {
    const name = input.value.trim();
    if (!name || name === category.name) {
      return;
    }
    const result = await window.api.put('/api/categories/' + category.id, { name: name }, { userAction: true });
    if (!result.ok) {
      showError(result, '이름을 바꾸지 못했어요.');
      input.value = category.name;
      return;
    }
    await load();
  }

  async function move(index, delta) {
    const other = index + delta;
    if (other < 0 || other >= categories.length) {
      return;
    }
    // 두 카테고리의 순서 값을 맞바꾼다. 같은 값이면 위치 번호를 순서로 쓴다
    const a = categories[index];
    const b = categories[other];
    const orderA = a.sortOrder === b.sortOrder ? other : b.sortOrder;
    const orderB = a.sortOrder === b.sortOrder ? index : a.sortOrder;
    const first = await window.api.put('/api/categories/' + a.id, { sortOrder: orderA }, { userAction: true });
    const second = await window.api.put('/api/categories/' + b.id, { sortOrder: orderB }, { userAction: true });
    if (!first.ok || !second.ok) {
      showError(first.ok ? second : first, '순서를 바꾸지 못했어요.');
    }
    await load();
  }

  async function remove(category) {
    if (!window.confirm('"' + category.name + '" 카테고리를 지울까요? 이 카테고리의 글은 분류 없음이 돼요.')) {
      return;
    }
    const result = await window.api.delete('/api/categories/' + category.id, { userAction: true });
    if (!result.ok) {
      showError(result, '지우지 못했어요.');
      return;
    }
    if (selected === category.id) {
      select(null);
    }
    await load();
  }

  function renderManage() {
    const list = $('category-edit-list');
    list.replaceChildren();
    categories.forEach((c, index) => {
      const li = el('li');
      const input = el('input');
      input.type = 'text';
      input.maxLength = 30;
      input.value = c.name;
      input.setAttribute('aria-label', c.name + ' 이름');
      input.addEventListener('change', () => rename(c, input));
      const up = el('button', 'link-button', '위로');
      up.type = 'button';
      up.disabled = index === 0;
      up.addEventListener('click', () => move(index, -1));
      const down = el('button', 'link-button', '아래로');
      down.type = 'button';
      down.disabled = index === categories.length - 1;
      down.addEventListener('click', () => move(index, 1));
      const del = el('button', 'link-button danger', '지우기');
      del.type = 'button';
      del.addEventListener('click', () => remove(c));
      li.append(input, up, down, del);
      list.append(li);
    });
  }

  async function load() {
    const result = await window.api.get('/api/blogs/' + encodeURIComponent(blog.slug) + '/categories' + keyQuery());
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    categories = result.data;
    renderNav();
    if (canManage()) {
      renderManage();
    }
  }

  async function add(event) {
    event.preventDefault();
    $('category-message').textContent = '';
    const name = $('category-name').value.trim();
    if (!name) {
      $('category-message').textContent = '카테고리 이름을 입력해 주세요.';
      return;
    }
    const result = await window.api.post('/api/blogs/' + encodeURIComponent(blog.slug) + '/categories',
      { name: name }, { userAction: true });
    if (!result.ok) {
      showError(result, '카테고리를 만들지 못했어요.');
      return;
    }
    $('category-name').value = '';
    await load();
  }

  document.addEventListener('blog:loaded', (event) => {
    blog = event.detail;
    if (canManage()) {
      const toggle = $('category-manage-toggle');
      toggle.classList.remove('hidden');
      toggle.addEventListener('click', () => $('category-manage').classList.toggle('hidden'));
      $('category-form').addEventListener('submit', add);
    }
    load();
  });
})();
