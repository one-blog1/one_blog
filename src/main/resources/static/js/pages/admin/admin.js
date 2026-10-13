// 관리자 화면 (ADM-01~08, BRD-10). 서버가 가린 개인정보만 받고, "전체 보기"는 서버에 기록이 남는다 (4.4).
// 모든 값은 textContent로 넣는다 (SEC-06).
(function () {
  'use strict';

  const state = { tab: 'stats', q: '', page: 1 };

  const $ = window.ui.$;

  const el = window.ui.el;

  const date = window.ui.formatDateTime;

  function message(text) {
    $('admin-message').textContent = text || '';
  }

  // 글·댓글이 있는 화면으로 바로 가는 링크. 관리자 화면(탭·검색어)을 잃지 않게 새 탭으로 연다
  function goto(text, href) {
    if (!href) {
      return text;
    }
    const a = el('a', 'goto-link', text);
    a.href = href;
    a.target = '_blank';
    a.rel = 'noopener';
    return a;
  }

  function postHref(blogSlug, postId) {
    return blogSlug ? window.ui.postHref(blogSlug, postId) : '/notice.html?id=' + encodeURIComponent(postId);
  }

  function button(label, onClick, className) {
    const b = el('button', className || 'link-button', label);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function table(headers, rows) {
    const t = el('table', 'admin-table');
    const head = el('tr');
    headers.forEach(h => head.append(el('th', null, h)));
    const thead = el('thead');
    thead.append(head);
    const tbody = el('tbody');
    rows.forEach(cells => {
      const tr = el('tr');
      cells.forEach(c => {
        const td = el('td');
        if (c instanceof Node) {
          td.append(c);
        } else {
          td.textContent = c === null || c === undefined ? '' : String(c);
        }
        tr.append(td);
      });
      tbody.append(tr);
    });
    t.append(thead, tbody);
    return t;
  }

  async function act(url, body, done) {
    const reason = await window.dialog.prompt('사유를 적어 주세요 (활동 기록에 남습니다).') || '';
    const result = await window.api.post(url, Object.assign({ reason: reason }, body || {}), { userAction: true });
    if (done) {
      done(); // 다시 불러오면 안내가 지워지므로 안내는 그 뒤에
    }
    message(result.ok ? '처리했습니다.' : ((result.data && result.data.message) || '처리하지 못했습니다.'));
  }

  function paged(path, render) {
    return async () => {
      const q = new URLSearchParams({ page: String(state.page), size: '20' });
      if (state.q) {
        q.set('q', state.q);
      }
      const result = await window.api.get(path + '?' + q.toString(), { userAction: true });
      if (result.status === 401 || result.status === 403) {
        window.location.href = '/admin-login.html';
        return;
      }
      if (!result.ok || !result.data) {
        message('불러오지 못했습니다.');
        return;
      }
      $('admin-content').replaceChildren(render(result.data.items));
      window.pager.render($('admin-pagination'), result.data.page, result.data.totalPages, (p) => {
        state.page = p;
        load();
      });
    };
  }

  const views = {
    stats: async () => {
      const result = await window.api.get('/api/admin/stats?days=14', { userAction: true });
      if (result.status === 401 || result.status === 403) {
        window.location.href = '/admin-login.html';
        return;
      }
      const s = result.data;
      const box = el('div');
      box.append(el('p', null, '회원 ' + s.users + '명 · 블로그 ' + s.blogs + '개 · 글 ' + s.posts + '개'));
      box.append(table(['날짜', '가입', '글'], s.daily.map(d => [d.date, d.signups, d.posts])));
      $('admin-content').replaceChildren(box);
      $('admin-pagination').replaceChildren();
    },
    users: paged('/api/admin/users', (items) => table(
      ['닉네임', '이름', '이메일', '전화번호', '상태', '운영/참여', '가입일', ''],
      items.map(u => {
        const reveal = button('전체 보기', async () => {
          const r = await window.api.post('/api/admin/users/' + u.id + '/reveal', undefined, { userAction: true });
          if (r.ok && r.data) {
            message(u.nickname + ': ' + r.data.email + ' / ' + r.data.name + ' / ' + r.data.phone + ' (조회 기록이 남았습니다)');
          }
        });
        return [u.nickname, u.name, u.email, u.phone, u.status, u.ownedBlogs + '/' + u.joinedBlogs, date(u.createdAt),
          reveal];
      }))),
    blogs: paged('/api/admin/blogs', (items) => table(
      ['이름', '주소', '공개', '상태', '블로그장', '멤버', '글', ''],
      items.map(b => {
        const link = el('a', null, b.slug);
        link.href = '/blog/' + encodeURIComponent(b.slug);
        const actions = el('span');
        actions.append(button(b.hidden ? '숨김 풀기' : '숨기기',
          () => act('/api/admin/blogs/' + b.id + '/hide', { hidden: !b.hidden }, load)));
        actions.append(button('블로그장 경고', () => act('/api/admin/blogs/' + b.id + '/owner-warning', {}, load)));
        actions.append(button('블로그장 강퇴', async () => {
          if (await window.dialog.confirm('블로그장 권한을 박탈할까요? 부블로그장이 있으면 넘어가고, 없으면 7일 뒤 폐쇄돼요.')) {
            act('/api/admin/blogs/' + b.id + '/owner-revoke', {}, load);
          }
        }, 'link-button danger'));
        actions.append(button('강제 폐쇄', () => act('/api/admin/blogs/' + b.id + '/close', {}, load), 'link-button danger'));
        return [b.name, link, b.visibility, b.status + (b.hidden ? ' (숨김)' : ''), b.ownerNickname, b.memberCount,
          b.postCount, actions];
      }))),
    posts: paged('/api/admin/posts', (items) => table(
      ['제목', '블로그', '작성자', '상태', '작성일', ''],
      items.map(p => {
        const actions = el('span');
        if (!p.deleted) {
          actions.append(button(p.hidden ? '숨김 풀기' : '숨기기',
            () => act('/api/admin/posts/' + p.id + '/hide', { hidden: !p.hidden }, load)));
          actions.append(button('삭제', () => act('/api/admin/posts/' + p.id + '/delete', {}, load), 'link-button danger'));
        }
        return [goto(p.title, p.deleted ? null : postHref(p.blogSlug, p.id)), p.blogName || '메인 공지', p.authorNickname,
          p.deleted ? '삭제됨' : (p.hidden ? '숨김' : '보임'), date(p.createdAt), actions];
      }))),
    comments: paged('/api/admin/comments', (items) => table(
      ['내용', '글', '작성자', '상태', '작성일', ''],
      items.map(c => {
        const actions = el('span');
        if (!c.deleted) {
          actions.append(button(c.hidden ? '숨김 풀기' : '숨기기',
            () => act('/api/admin/comments/' + c.id + '/hide', { hidden: !c.hidden }, load)));
          actions.append(button('삭제', () => act('/api/admin/comments/' + c.id + '/delete', {}, load), 'link-button danger'));
        }
        // 댓글은 글 화면의 그 댓글 위치(#comment-번호)로 간다. 메인 공지 댓글은 공지 화면으로
        const commentHref = c.deleted ? null
          : postHref(c.blogSlug, c.postId) + (c.blogSlug ? '#comment-' + encodeURIComponent(c.id) : '');
        return [goto(c.content, commentHref), goto(c.postTitle, postHref(c.blogSlug, c.postId)), c.authorNickname,
          c.deleted ? '삭제됨' : (c.hidden ? '숨김' : '보임'),
          date(c.createdAt), actions];
      }))),
    reports: paged('/api/admin/reports', (items) => {
      if (window.adminReports) {
        return window.adminReports.render(items, { table: table, button: button, el: el, date: date, reload: () => load() });
      }
      return el('p', 'hint', '신고 처리는 013에서 붙습니다.');
    }),
    notices: paged('/api/notices', (items) => table(
      ['종류', '제목', '작성일', ''],
      items.map(n => {
        // 수정이 삭제 왼쪽 (018)
        const actions = el('span');
        actions.append(button('수정', () => startNoticeEdit(n.id)), button('삭제', async () => {
          if (!await window.dialog.confirm('공지를 삭제할까요?')) {
            return;
          }
          const r = await window.api.delete('/api/admin/notices/' + n.id, { userAction: true });
          if (r.ok && noticeEditId === n.id) {
            resetNoticeForm();
          }
          load();
          message(r.ok ? '삭제했습니다.' : '삭제하지 못했습니다.');
        }, 'link-button danger'));
        return [n.categoryLabel || '일반', goto(n.title, '/notice.html?id=' + encodeURIComponent(n.id)),
          date(n.createdAt), actions];
      }))),
    // 메인 배너 (ADM-08, D-132): 광고 관리와 인기 글 설정. 페이지 없음
    banners: async () => {
      $('admin-pagination').replaceChildren();
      await window.adminBanners.load({
        table: table, button: button, message: message, content: $('admin-content'),
        onUnauthorized: () => { window.location.href = '/admin-login.html'; }
      });
    },
    actions: paged('/api/admin/actions', (items) => table(
      ['시각', '관리자', '조치', '대상', '내용', 'IP'],
      items.map(a => [date(a.createdAt), a.adminLoginId, a.actionType, a.targetType + ' #' + a.targetId, a.detail,
        a.ipAddress])))
  };

  // ---- 공지 쓰기·고치기 (BRD-10, D-122) ----
  let noticeEditId = null;
  // 공지 에디터 (020). 공지 탭을 처음 열 때 만든다(숨긴 칸에서 만들면 크기가 어긋남). 못 불러오면 글상자로
  let noticeEditor = null;

  function ensureNoticeEditor() {
    if (!noticeEditor && window.postEditor) {
      noticeEditor = window.postEditor.create($('notice-editor'), $('notice-content'), () => {}, { height: '380px' });
    }
  }

  function noticeContent() {
    return noticeEditor ? noticeEditor.getMarkdown() : $('notice-content').value;
  }

  function setNoticeContent(text) {
    if (noticeEditor) {
      noticeEditor.setMarkdown(text || '');
    }
    $('notice-content').value = text || '';
    window.ui.setError('imageFileIds', '');
  }

  function resetNoticeForm() {
    noticeEditId = null;
    $('notice-category').value = 'GENERAL';
    $('notice-title').value = '';
    setNoticeContent('');
    $('notice-submit').textContent = '공지 올리기';
    $('notice-cancel').classList.add('hidden');
    $('notice-editing').classList.add('hidden');
  }

  /** [수정]: 원문(마크다운)을 받아 위 칸에 채운다. */
  async function startNoticeEdit(id) {
    const result = await window.api.get('/api/admin/notices/' + encodeURIComponent(id), { userAction: true });
    if (!result.ok || !result.data) {
      message('공지를 불러오지 못했습니다.');
      return;
    }
    const n = result.data;
    noticeEditId = n.id;
    $('notice-category').value = n.category;
    $('notice-title').value = n.title;
    setNoticeContent(n.content);
    $('notice-submit').textContent = '수정 저장';
    $('notice-cancel').classList.remove('hidden');
    $('notice-editing').textContent = '"' + n.title + '" 공지를 고치는 중이에요.';
    $('notice-editing').classList.remove('hidden');
    $('notice-form').scrollIntoView({ block: 'start' });
    $('notice-title').focus();
  }

  function load() {
    message('');
    const searchable = ['users', 'blogs', 'posts', 'comments'].includes(state.tab);
    $('search-form').classList.toggle('hidden', !searchable);
    $('notice-form').classList.toggle('hidden', state.tab !== 'notices');
    if (state.tab === 'notices') {
      ensureNoticeEditor();
    }
    views[state.tab]();
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-tab]').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('[data-tab]').forEach(t => t.setAttribute('aria-selected', 'false'));
        tab.setAttribute('aria-selected', 'true');
        state.tab = tab.dataset.tab;
        state.page = 1;
        state.q = '';
        $('search-input').value = '';
        resetNoticeForm();
        window.adminBanners.reset();
        load();
      });
    });
    $('search-form').addEventListener('submit', (e) => {
      e.preventDefault();
      state.q = $('search-input').value.trim();
      state.page = 1;
      load();
    });
    $('notice-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const body = {
        title: $('notice-title').value.trim(),
        content: noticeContent(),
        category: $('notice-category').value
      };
      const editing = noticeEditId !== null;
      const result = editing
        ? await window.api.put('/api/admin/notices/' + encodeURIComponent(noticeEditId), body, { userAction: true })
        : await window.api.post('/api/admin/notices', body, { userAction: true });
      if (result.ok) {
        resetNoticeForm();
        load(); // load()가 안내를 비우므로 안내는 그 뒤에
        message(editing ? '공지를 고쳤습니다.' : '공지를 올렸습니다.');
      } else {
        message((result.data && result.data.message) || (editing ? '고치지 못했습니다.' : '올리지 못했습니다.'));
      }
    });
    $('notice-cancel').addEventListener('click', () => {
      resetNoticeForm();
      message('');
    });
    load();
  });
})();
