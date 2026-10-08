// 블로그장 전용 멤버 더보기 창 (D-112): 닉네임·역할·글 수·댓글 수·경고 횟수·강제 퇴장.
// 글 수·댓글 수를 누르면 그 회원이 이 블로그에서 쓴 글·댓글 목록이 나오고, 하나를 누르면 그 위치로 간다.
// 강제 퇴장은 경고 창을 한 번 거친다. 서버도 블로그장인지 확인한다. 값은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const ROLE = { OWNER: '블로그장', SUB_OWNER: '부블로그장', MEMBER: '멤버' };
  let slug = '';

  const el = window.ui.el;

  function api(path) {
    return '/api/blogs/' + encodeURIComponent(slug) + path;
  }

  function postUrl(postId) {
    return '/blog/' + encodeURIComponent(slug) + '/posts/' + encodeURIComponent(postId);
  }

  function day(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('ko-KR');
  }

  function loading(body) {
    body.replaceChildren(el('p', 'hint', '불러오는 중…'));
  }

  function failed(body, result) {
    body.replaceChildren(el('p', 'message error', (result.data && result.data.message) || '불러오지 못했어요.'));
  }

  function pager(page, totalPages, go) {
    const nav = el('nav', 'pagination');
    if (window.pager) {
      window.pager.render(nav, page, totalPages, go);
    }
    return nav;
  }

  function open() {
    const m = window.modal.open({ title: '멤버', wide: true });
    showMembers(m, 1);
  }

  async function showMembers(m, page) {
    m.setTitle('멤버');
    loading(m.body);
    const result = await window.api.get(api('/member-stats?size=20&page=' + page), { userAction: true });
    if (!result.ok || !result.data) {
      failed(m.body, result);
      return;
    }
    const data = result.data;
    const wrap = el('div', 'table-scroll');
    const table = el('table', 'member-stats');
    const head = el('tr');
    [['닉네임', ''], ['역할', ''], ['글', 'num'], ['댓글', 'num'], ['경고', 'num'], ['', '']].forEach(([t, c]) => {
      head.append(el('th', c, t));
    });
    const thead = el('thead');
    thead.append(head);
    const tbody = el('tbody');
    const notice = el('div');
    data.items.forEach(member => {
      const tr = el('tr');
      const nick = el('td', 'nick');
      nick.append(window.userLink(member.nickname));
      tr.append(nick, el('td', null, ROLE[member.role] || member.role));
      [['posts', member.postCount], ['comments', member.commentCount]].forEach(([kind, count]) => {
        const td = el('td', 'num');
        const b = el('button', 'count-link', count);
        b.type = 'button';
        b.disabled = count === 0;
        b.setAttribute('aria-label', member.nickname + '님의 ' + (kind === 'posts' ? '글' : '댓글') + ' ' + count + '개 보기');
        b.addEventListener('click', () => showActivity(m, member, kind, 1));
        td.append(b);
        tr.append(td);
      });
      tr.append(el('td', 'num', member.warningCount));
      const action = el('td', 'num');
      if (member.role !== 'OWNER') {
        const kick = el('button', 'link-button danger', '강제 퇴장');
        kick.type = 'button';
        kick.addEventListener('click', () => confirmKick(m, member, notice, page));
        action.append(kick);
      }
      tr.append(action);
      tbody.append(tr);
    });
    table.append(thead, tbody);
    wrap.append(table);
    m.body.replaceChildren(el('p', 'hint', '멤버 ' + data.totalItems + '명 · 글·댓글 수를 누르면 이 블로그에서 쓴 목록을 볼 수 있어요.'),
      wrap, notice, pager(data.page, data.totalPages, (p) => showMembers(m, p)));
  }

  // 강제 퇴장 경고 (한 번): 사유를 적고 확인해야 한다
  function confirmKick(m, member, box, page) {
    box.replaceChildren();
    const panel = el('div', 'kick-confirm');
    panel.setAttribute('role', 'alertdialog');
    panel.append(el('p', null, member.nickname + '님을 강제 퇴장할까요?'),
      el('p', 'hint', '블랙리스트에 올라 다시 참여할 수 없고, 이 블로그에 쓴 글은 "탈퇴한 계정"으로 남아요. 되돌릴 수 없어요.'));
    const label = el('label', null, '사유');
    const input = el('input');
    input.type = 'text';
    input.maxLength = 200;
    input.id = 'kick-reason';
    label.htmlFor = input.id;
    const message = el('p', 'message error');
    const row = el('div', 'row');
    const cancel = el('button', 'secondary', '취소');
    cancel.type = 'button';
    cancel.addEventListener('click', () => box.replaceChildren());
    const go = el('button', 'primary danger inline-button', '강제 퇴장');
    go.type = 'button';
    go.addEventListener('click', async () => {
      if (!input.value.trim()) {
        message.textContent = '사유를 적어 주세요.';
        input.focus();
        return;
      }
      go.disabled = true;
      const result = await window.api.post(api('/members/' + encodeURIComponent(member.userId) + '/sanctions'),
        { type: 'KICK', reason: input.value.trim() }, { userAction: true });
      go.disabled = false;
      if (!result.ok) {
        message.textContent = (result.data && result.data.message) || '처리하지 못했어요.';
        return;
      }
      document.dispatchEvent(new CustomEvent('members:changed'));
      showMembers(m, page);
    });
    row.append(cancel, go);
    panel.append(label, input, message, row);
    box.append(panel);
    input.focus();
    panel.scrollIntoView({ block: 'nearest' });
  }

  async function showActivity(m, member, kind, page) {
    const posts = kind === 'posts';
    m.setTitle(member.nickname + '님의 ' + (posts ? '글' : '댓글'));
    loading(m.body);
    const path = '/members/' + encodeURIComponent(member.userId) + (posts ? '/posts' : '/comments') + '?page=' + page;
    const result = await window.api.get(api(path), { userAction: true });
    if (!result.ok || !result.data) {
      failed(m.body, result);
      return;
    }
    const data = result.data;
    const top = el('div', 'activity-tabs');
    const back = window.icons.button('back', '멤버 목록으로', 'tip-start');
    back.addEventListener('click', () => showMembers(m, 1));
    const tabs = el('div', 'segmented');
    tabs.setAttribute('role', 'group');
    [['posts', '글 ' + member.postCount], ['comments', '댓글 ' + member.commentCount]].forEach(([k, label]) => {
      const b = el('button', null, label);
      b.type = 'button';
      b.setAttribute('aria-pressed', String(k === kind));
      b.addEventListener('click', () => showActivity(m, member, k, 1));
      tabs.append(b);
    });
    top.append(back, tabs);
    const list = el('ul', 'member-activity');
    data.items.forEach(item => {
      const li = el('li');
      const a = el('a');
      if (posts) {
        a.href = postUrl(item.id);
        const title = el('span', null, (item.notice ? '[공지] ' : '') + item.title);
        a.append(title, el('span', 'sub', day(item.createdAt) + (item.hidden ? ' · 관리자가 숨김' : '')));
      } else {
        // 그 댓글 위치로 (글 화면이 형광 노랑으로 강조한다)
        a.href = postUrl(item.postId) + '#comment-' + encodeURIComponent(item.id);
        a.append(el('span', null, item.content),
          el('span', 'sub', '「' + item.postTitle + '」 · ' + day(item.createdAt) + (item.hidden ? ' · 관리자가 숨김' : '')));
      }
      li.append(a);
      list.append(li);
    });
    const empty = data.items.length === 0 ? el('p', 'hint', posts ? '쓴 글이 없어요.' : '쓴 댓글이 없어요.') : null;
    m.body.replaceChildren(top, list);
    if (empty) {
      m.body.append(empty);
    }
    m.body.append(pager(data.page, data.totalPages, (p) => showActivity(m, member, kind, p)));
    back.focus();
  }

  document.addEventListener('DOMContentLoaded', () => {
    slug = decodeURIComponent(window.location.pathname.split('/')[2] || '');
    const more = document.getElementById('members-more');
    if (more) {
      more.addEventListener('click', open);
    }
  });

  window.membersModal = { open: open };
})();
