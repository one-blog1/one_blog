// 글쓰기·고치기 (BRD-01, 6.6). 주소: /blog/{주소}/write 또는 /blog/{주소}/posts/{번호}/edit
// 본문은 마크다운. 에디터(Toast UI, 005)가 없으면 글상자로 쓴다. 쓰는 중에는 브라우저에만 자동 저장한다 (6.6 임시저장).
(function () {
  'use strict';

  const TAG = /^[가-힣a-z0-9_]{1,20}$/;
  const state = { slug: '', postId: null, tags: [], images: [] };
  let editor = null;

  function $(id) {
    return document.getElementById(id);
  }

  function keyQuery() {
    const key = new URLSearchParams(window.location.search).get('key');
    return key ? '?key=' + encodeURIComponent(key) : '';
  }

  function draftKey() {
    return 'oneblog.draft.' + state.slug + '.' + (state.postId || 'new');
  }

  function parsePath() {
    const parts = window.location.pathname.split('/').filter(Boolean);
    state.slug = decodeURIComponent(parts[1] || '');
    state.postId = parts[2] === 'posts' ? parts[3] : null;
  }

  function getContent() {
    return editor ? editor.getMarkdown() : $('content').value;
  }

  function setContent(text) {
    if (editor) {
      editor.setMarkdown(text || '');
    } else {
      $('content').value = text || '';
    }
    updateCount();
  }

  function updateCount() {
    $('content-count').textContent = String(Array.from(getContent()).length);
  }

  function saveDraft() {
    try {
      window.localStorage.setItem(draftKey(), JSON.stringify({
        title: $('title').value, content: getContent(), tags: state.tags, savedAt: Date.now()
      }));
      $('draft-message').textContent = '이 브라우저에 임시 저장했어요.';
    } catch (e) {
      // 저장소를 쓸 수 없는 브라우저면 넘어간다
    }
  }

  function loadDraft() {
    try {
      const raw = window.localStorage.getItem(draftKey());
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  }

  function clearDraft() {
    try {
      window.localStorage.removeItem(draftKey());
    } catch (e) {
      // 무시
    }
  }

  function normalizeTag(raw) {
    return raw.trim().replace(/^#+/, '').trim().replace(/\s+/g, '_').toLowerCase();
  }

  function renderTags() {
    const list = $('tag-chips');
    list.replaceChildren();
    state.tags.forEach((tag, index) => {
      const li = document.createElement('li');
      li.className = 'tag';
      li.textContent = '#' + tag + ' ';
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'tag-remove';
      remove.setAttribute('aria-label', tag + ' 태그 빼기');
      remove.textContent = '×';
      remove.addEventListener('click', () => {
        state.tags.splice(index, 1);
        renderTags();
      });
      li.append(remove);
      list.append(li);
    });
  }

  function addTags(text) {
    setError('tags', '');
    for (const tag of text.split(',').map(normalizeTag).filter(t => t.length > 0)) {
      if (!TAG.test(tag)) {
        setError('tags', '"' + tag + '"는 쓸 수 없는 태그예요. 한글, 영문, 숫자, _만 1~20자로 입력해 주세요.');
        return false;
      }
      if (!state.tags.includes(tag) && state.tags.length < 10) {
        state.tags.push(tag);
      }
    }
    renderTags();
    return true;
  }

  function setError(field, text) {
    const node = document.querySelector('[data-error-for="' + field + '"]');
    if (node) {
      node.textContent = text || '';
    }
  }

  async function loadBlog() {
    const result = await window.api.get('/api/blogs/' + encodeURIComponent(state.slug) + keyQuery(), { userAction: true });
    if (!result.ok || !result.data) {
      $('form-message').textContent = (result.data && result.data.message) || '블로그를 불러오지 못했어요.';
      return null;
    }
    const blog = result.data;
    $('blog-link').textContent = blog.name;
    $('blog-link').href = '/blog/' + encodeURIComponent(blog.slug) + keyQuery();
    if (!blog.myRole) {
      $('form-message').textContent = '이 블로그의 멤버만 글을 쓸 수 있어요.';
    }
    if (!state.postId && (blog.myRole === 'OWNER' || blog.myRole === 'SUB_OWNER')) {
      $('notice-field').classList.remove('hidden');
    }
    document.dispatchEvent(new CustomEvent('editor:blog', { detail: blog }));
    return blog;
  }

  async function loadPost() {
    const result = await window.api.get('/api/posts/' + encodeURIComponent(state.postId) + keyQuery(), { userAction: true });
    if (!result.ok || !result.data || !result.data.canEdit) {
      $('form-message').textContent = '글은 작성자만 고칠 수 있어요.';
      return;
    }
    const post = result.data;
    $('page-title').textContent = '글 고치기';
    $('title').value = post.title;
    setContent(post.content);
    state.tags = (post.tags || []).slice();
    renderTags();
    document.dispatchEvent(new CustomEvent('editor:post', { detail: post }));
  }

  async function submit(event) {
    event.preventDefault();
    document.querySelectorAll('[data-error-for]').forEach(n => { n.textContent = ''; });
    $('form-message').textContent = '';
    const pending = $('tag-input').value;
    if (pending.trim() && !addTags(pending)) {
      return;
    }
    $('tag-input').value = '';
    const categoryValue = $('category').value;
    const body = {
      title: $('title').value.trim(),
      content: getContent(),
      notice: $('notice').checked,
      categoryId: categoryValue ? Number(categoryValue) : null,
      tags: state.tags,
      imageFileIds: window.postImages ? window.postImages.ids(getContent()) : []
    };
    if (!body.title) {
      setError('title', '제목을 입력해 주세요.');
      return;
    }
    const button = $('post-form').querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const result = state.postId
        ? await window.api.put('/api/posts/' + encodeURIComponent(state.postId), body, { userAction: true })
        : await window.api.post('/api/blogs/' + encodeURIComponent(state.slug) + '/posts' + keyQuery(), body,
          { userAction: true });
      if (result.status === 401) {
        saveDraft();
        window.location.href = '/login.html';
        return;
      }
      if (result.ok && result.data) {
        clearDraft();
        const id = result.data.id;
        window.location.href = '/blog/' + encodeURIComponent(state.slug) + '/posts/' + id + keyQuery();
        return;
      }
      const data = result.data || {};
      if (Array.isArray(data.fieldErrors)) {
        data.fieldErrors.forEach(f => setError(f.field.startsWith('tags') ? 'tags' : f.field, f.message));
      }
      $('form-message').textContent = data.message || '저장하지 못했어요. 다시 시도해 주세요.';
    } finally {
      button.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', async () => {
    parsePath();
    if (window.postEditor) {
      editor = window.postEditor.create($('editor'), $('content'), () => {
        updateCount();
        window.sessionKeeper.touch();
      });
    }
    if (!editor) {
      $('editor').classList.add('hidden');
    }
    $('content').addEventListener('input', updateCount);
    $('tag-input').addEventListener('keydown', (e) => {
      if ((e.key === 'Enter' || e.key === ',') && !e.isComposing) {
        e.preventDefault();
        if (addTags(e.target.value)) {
          e.target.value = '';
        }
      }
    });
    $('post-form').addEventListener('submit', submit);

    const blog = await loadBlog();
    if (!blog) {
      return;
    }
    if (state.postId) {
      await loadPost();
    }
    const draft = loadDraft();
    if (draft && window.confirm('임시 저장한 글이 있어요. 불러올까요?')) {
      $('title').value = draft.title || '';
      setContent(draft.content || '');
      state.tags = draft.tags || [];
      renderTags();
    }
    window.setInterval(saveDraft, 30 * 1000);
  });
})();
