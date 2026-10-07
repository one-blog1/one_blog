// 블로그 만들기 (BLG-01, BLG-10). 화면 검사는 편의용이고 최종 판단은 서버가 한다.
// 서버 문구와 사용자 입력은 textContent로만 표시한다 (SEC-06).
(function () {
  'use strict';

  const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
  const TAG = /^[가-힣a-z0-9_]{1,20}$/;
  const MAX_TAGS = 10;
  const MAX_COVER = 3 * 1024 * 1024;
  const SLUG_REASON = {
    INVALID_FORMAT: '영문 소문자, 숫자, -로 된 3~30자로 입력해 주세요. -로 시작하거나 끝날 수 없어요.',
    RESERVED: '사용할 수 없는 주소입니다.',
    TAKEN: '이미 쓰이는 주소입니다.'
  };

  const state = { tags: [], coverFileId: null };

  function $(id) {
    return document.getElementById(id);
  }

  function goLogin() {
    window.location.href = '/login.html';
  }

  function setError(field, text) {
    const node = document.querySelector('[data-error-for="' + field + '"]');
    if (node) {
      node.textContent = text || '';
      node.classList.toggle('error', Boolean(text));
      node.classList.remove('ok');
    }
  }

  function clearErrors() {
    document.querySelectorAll('[data-error-for]').forEach(n => { n.textContent = ''; });
    $('form-message').textContent = '';
  }

  /** 서버 TagPolicy와 같은 정리 규칙 (6.4) */
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
    $('tag-input').disabled = state.tags.length >= MAX_TAGS;
  }

  function addTags(text) {
    setError('tags', '');
    const parts = text.split(',').map(normalizeTag).filter(t => t.length > 0);
    for (const tag of parts) {
      if (!TAG.test(tag)) {
        setError('tags', '"' + tag + '"는 쓸 수 없는 태그예요. 한글, 영문, 숫자, _만 1~20자로 입력해 주세요.');
        return false;
      }
      if (state.tags.includes(tag)) {
        continue;
      }
      if (state.tags.length >= MAX_TAGS) {
        setError('tags', '태그는 10개까지 달 수 있어요.');
        break;
      }
      state.tags.push(tag);
    }
    renderTags();
    return true;
  }

  async function loadQuota() {
    const result = await window.api.get('/api/me/blog-quota', { userAction: true, redirectOnLogout: true });
    if (result.status === 401) {
      goLogin();
      return;
    }
    const quota = $('quota');
    if (result.status === 403) {
      quota.textContent = '관리자 계정은 블로그를 만들 수 없습니다.';
      document.querySelector('#blog-form button[type="submit"]').disabled = true;
      return;
    }
    if (result.ok && result.data) {
      const p = result.data.public;
      const v = result.data.private;
      quota.textContent = '공개(일부 공개 포함) ' + p.used + '/' + p.limit + '개, 비공개 ' + v.used + '/' + v.limit
        + '개 만들었어요. 공개 ' + p.remaining + '개, 비공개 ' + v.remaining + '개 더 만들 수 있어요.';
    }
  }

  async function checkSlug() {
    const input = $('slug');
    const message = $('slug-message');
    const slug = input.value.trim().toLowerCase();
    input.value = slug;
    message.classList.remove('ok', 'error');
    if (!SLUG.test(slug) || slug.length < 3 || slug.length > 30) {
      message.textContent = SLUG_REASON.INVALID_FORMAT;
      message.classList.add('error');
      return false;
    }
    const result = await window.api.get('/api/blog-slugs/availability?slug=' + encodeURIComponent(slug),
      { userAction: true });
    if (result.status === 401) {
      goLogin();
      return false;
    }
    if (result.ok && result.data && result.data.available) {
      message.textContent = '사용할 수 있는 주소예요.';
      message.classList.add('ok');
      return true;
    }
    const reason = result.data && result.data.reason;
    message.textContent = SLUG_REASON[reason] || '주소를 확인하지 못했어요. 다시 시도해 주세요.';
    message.classList.add('error');
    return false;
  }

  async function uploadCover(file) {
    const message = $('cover-message');
    message.textContent = '';
    state.coverFileId = null;
    if (!file) {
      return;
    }
    if (file.size > MAX_COVER) {
      message.textContent = '대표 이미지는 3MB까지 올릴 수 있어요.';
      $('cover').value = '';
      return;
    }
    const result = await window.api.upload('/api/files/blog-cover', file, { userAction: true });
    if (result.status === 401) {
      goLogin();
      return;
    }
    if (result.ok && result.data) {
      state.coverFileId = result.data.fileId;
      const url = String(result.data.url || '');
      $('cover-preview').src = url.startsWith('/files/') ? url : '/images/blog-default.svg';
      $('cover-remove').classList.remove('hidden');
      return;
    }
    message.textContent = (result.data && result.data.message) || '이미지를 올리지 못했어요.';
    $('cover').value = '';
  }

  function removeCover() {
    state.coverFileId = null;
    $('cover').value = '';
    $('cover-preview').src = '/images/blog-default.svg';
    $('cover-remove').classList.add('hidden');
  }

  function showServerError(data) {
    const code = data && data.code;
    const text = (data && data.message) || '블로그를 만들지 못했어요. 다시 시도해 주세요.';
    if (code === 'VALIDATION_FAILED' && Array.isArray(data.fieldErrors)) {
      data.fieldErrors.forEach(f => {
        const field = f.field.startsWith('tags') ? 'tags' : f.field;
        setError(field, f.message);
      });
      $('form-message').textContent = text;
      return;
    }
    if (code === 'INVALID_SLUG' || code === 'RESERVED_SLUG' || code === 'SLUG_TAKEN') {
      setError('slug', text);
      return;
    }
    if (code === 'INVALID_COVER_FILE') {
      setError('cover', text);
      removeCover();
      return;
    }
    $('form-message').textContent = text;
  }

  async function submit(event) {
    event.preventDefault();
    clearErrors();
    const form = $('blog-form');
    const pending = $('tag-input').value;
    if (pending.trim() && !addTags(pending)) {
      return;
    }
    $('tag-input').value = '';

    const name = form.name.value.trim();
    if (!name) {
      setError('name', '블로그 이름을 입력해 주세요.');
      return;
    }
    if (!(await checkSlug())) {
      return;
    }

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const result = await window.api.post('/api/blogs', {
        name: name,
        slug: form.slug.value.trim().toLowerCase(),
        description: form.description.value,
        coverFileId: state.coverFileId,
        tags: state.tags,
        visibility: form.visibility.value,
        joinPolicy: form.joinPolicy.value
      }, { userAction: true });
      if (result.status === 401) {
        goLogin();
        return;
      }
      if (result.ok && result.data) {
        window.location.href = result.data.url;
        return;
      }
      showServerError(result.data);
      loadQuota();
    } finally {
      button.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    loadQuota();
    $('check-slug').addEventListener('click', checkSlug);
    $('slug').addEventListener('input', () => {
      $('slug-message').textContent = '';
    });
    $('description').addEventListener('input', (e) => {
      $('description-count').textContent = String(e.target.value.length);
    });
    $('cover').addEventListener('change', (e) => uploadCover(e.target.files[0]));
    $('cover-remove').addEventListener('click', removeCover);
    $('tag-input').addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ',') {
        if (e.isComposing) {
          return; // 한글 조합 중에는 넘긴다
        }
        e.preventDefault();
        if (addTags(e.target.value)) {
          e.target.value = '';
        }
      }
    });
    $('blog-form').addEventListener('submit', submit);
  });
})();
