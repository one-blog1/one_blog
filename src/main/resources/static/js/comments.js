// 댓글·대댓글·좋아요 (BRD-06, 6.6, D-78, D-87). 글 상세(post.js)가 글을 불러오면 이어서 그린다.
// 댓글 내용·닉네임은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  let post = null;

  function $(id) {
    return document.getElementById(id);
  }

  function keyQuery() {
    const key = new URLSearchParams(window.location.search).get('key');
    return key ? '?key=' + encodeURIComponent(key) : '';
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

  function formatDateTime(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('ko-KR');
  }

  function message(text) {
    $('comment-message').textContent = text || '';
  }

  function replyForm(parentId) {
    const form = el('form', 'comment-form reply-form');
    const input = el('textarea');
    input.maxLength = 500;
    input.rows = 2;
    input.placeholder = '답글 (500자까지)';
    const submit = el('button', 'secondary', '답글 쓰기');
    submit.type = 'submit';
    form.append(input, submit);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      await write(input.value, parentId);
    });
    return form;
  }

  function renderComment(c, isReply) {
    const li = el('li', isReply ? 'comment reply' : 'comment');
    // 알림의 링크(#comment-번호)로 바로 찾아갈 수 있게 (011)
    li.id = 'comment-' + c.id;
    if (c.deleted) {
      li.append(el('p', 'comment-deleted', '삭제된 댓글입니다'));
    } else {
      const head = el('p', 'comment-head');
      head.append(el('strong', null, c.authorName), el('span', 'comment-date', ' ' + formatDateTime(c.createdAt)));
      if (c.edited) {
        head.append(el('span', 'comment-edited', ' (수정됨)'));
      }
      li.append(head);
      const body = el('p', 'comment-body');
      if (c.replyToNickname) {
        body.append(el('span', 'mention', '@' + c.replyToNickname + ' '));
      }
      body.append(document.createTextNode(c.content));
      li.append(body);

      const actions = el('div', 'comment-actions');
      if (post.canComment) {
        const reply = el('button', 'link-button', '답글');
        reply.type = 'button';
        reply.addEventListener('click', () => {
          const existing = li.querySelector(':scope > .reply-form');
          if (existing) {
            existing.remove();
          } else {
            // 대댓글에 답해도 같은 첫 댓글 아래에 달린다 (1단계, D-78)
            li.append(replyForm(c.id));
          }
        });
        actions.append(reply);
      }
      if (c.canEdit) {
        const edit = el('button', 'link-button', '수정');
        edit.type = 'button';
        edit.addEventListener('click', () => startEdit(li, c));
        actions.append(edit);
      }
      if (c.canDelete) {
        const remove = el('button', 'link-button danger', '삭제');
        remove.type = 'button';
        remove.addEventListener('click', () => removeComment(c.id));
        actions.append(remove);
      }
      const report = el('button', 'link-button report-comment hidden', '신고');
      report.type = 'button';
      report.dataset.commentId = String(c.id);
      report.dataset.mine = String(!!c.canEdit);
      actions.append(report);
      li.append(actions);
    }
    if (Array.isArray(c.replies) && c.replies.length > 0) {
      const ul = el('ul', 'comment-list replies');
      c.replies.forEach(r => ul.append(renderComment(r, true)));
      li.append(ul);
    }
    return li;
  }

  function startEdit(li, c) {
    const body = li.querySelector('.comment-body');
    const form = el('form', 'comment-form');
    const input = el('textarea');
    input.maxLength = 500;
    input.rows = 2;
    input.value = c.content;
    const save = el('button', 'secondary', '저장');
    save.type = 'submit';
    form.append(input, save);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const result = await window.api.put('/api/comments/' + encodeURIComponent(c.id), { content: input.value },
        { userAction: true });
      if (result.ok) {
        load();
      } else {
        message((result.data && result.data.message) || '고치지 못했어요.');
      }
    });
    body.replaceWith(form);
  }

  async function removeComment(id) {
    if (!window.confirm('댓글을 삭제할까요?')) {
      return;
    }
    const result = await window.api.delete('/api/comments/' + encodeURIComponent(id), { userAction: true });
    if (result.ok) {
      load();
    } else {
      message((result.data && result.data.message) || '삭제하지 못했어요.');
    }
  }

  async function write(content, parentId) {
    message('');
    const result = await window.api.post('/api/posts/' + encodeURIComponent(post.id) + '/comments' + keyQuery(),
      { content: content, parentId: parentId || null }, { userAction: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (result.ok) {
      $('comment-input').value = '';
      load();
      return;
    }
    message((result.data && result.data.message) || '댓글을 쓰지 못했어요.');
  }

  // 주소에 #comment-번호가 있으면(관리자 화면·알림의 바로가기) 그 댓글로 내려가 잠시 표시한다. 처음 한 번만
  let linkedHandled = false;
  function focusLinkedComment() {
    const match = /^#comment-(\d+)$/.exec(window.location.hash);
    if (linkedHandled || !match) {
      return;
    }
    linkedHandled = true;
    const target = document.getElementById('comment-' + match[1]);
    if (!target) {
      message('이 댓글은 숨겨졌거나 삭제돼서 보이지 않아요.');
      return;
    }
    target.classList.add('comment-target');
    target.setAttribute('tabindex', '-1');
    target.scrollIntoView({ block: 'center' });
    target.focus({ preventScroll: true });
    window.setTimeout(() => target.classList.remove('comment-target'), 4000);
  }

  async function load() {
    const result = await window.api.get('/api/posts/' + encodeURIComponent(post.id) + '/comments' + keyQuery());
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const list = $('comment-list');
    list.replaceChildren();
    let count = 0;
    result.data.forEach(c => {
      list.append(renderComment(c, false));
      count += (c.deleted ? 0 : 1) + (c.replies ? c.replies.length : 0);
    });
    $('comment-count').textContent = String(count);
    focusLinkedComment();
    document.dispatchEvent(new CustomEvent('comments:rendered'));
  }

  function renderLike(liked, count) {
    const button = $('like-button');
    button.setAttribute('aria-pressed', String(liked));
    button.textContent = (liked ? '♥ 좋아요 ' : '♡ 좋아요 ') + count;
  }

  async function toggleLike() {
    const result = await window.api.post('/api/posts/' + encodeURIComponent(post.id) + '/like' + keyQuery(), undefined,
      { userAction: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (result.ok && result.data) {
      renderLike(result.data.liked, result.data.likeCount);
    } else {
      $('post-message').textContent = (result.data && result.data.message) || '좋아요를 누르지 못했어요.';
    }
  }

  document.addEventListener('post:loaded', (event) => {
    post = event.detail;
    $('comments').classList.remove('hidden');
    if (post.canComment) {
      $('comment-form').classList.remove('hidden');
      $('comment-form').addEventListener('submit', (e) => {
        e.preventDefault();
        write($('comment-input').value, null);
      });
      $('like-button').classList.remove('hidden');
      $('like-button').addEventListener('click', toggleLike);
    } else {
      $('comment-login').classList.remove('hidden');
    }
    renderLike(post.liked, post.likeCount);
    load();
  });
})();
