// 블로그 관리 /blog/{주소}/manage (2장 블로그장 표, BLG-01, BLG-08, BLG-09, D-71).
// 보이는 칸은 내 권한으로 정하지만, 실제 권한은 API마다 서버가 다시 확인한다 (SEC-07).
(function () {
  'use strict';

  const ROLE = { OWNER: '블로그장', SUB_OWNER: '부블로그장', MEMBER: '멤버' };
  const state = { slug: '', blog: null, coverFileId: null, removeCover: false, me: null };

  document.addEventListener('header:user', (event) => {
    state.me = event.detail.id;
  });

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

  function base() {
    return '/api/blogs/' + encodeURIComponent(state.slug);
  }

  function say(id, text, ok) {
    $(id).className = ok ? 'message ok' : 'message error';
    $(id).textContent = text || '';
  }

  function errorText(result, fallback) {
    const data = result.data || {};
    return data.message || fallback;
  }

  function showFieldErrors(result) {
    const data = result.data || {};
    (data.fieldErrors || []).forEach(f => {
      const node = document.querySelector('[data-error-for="' + (f.field.startsWith('tags') ? 'tags' : f.field) + '"]');
      if (node) {
        node.textContent = f.message;
      }
    });
  }

  function formatTime(iso) {
    return new Date(iso).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric' });
  }

  // ---- 정보 ----
  function fillInfo(blog) {
    $('name').value = blog.name;
    $('description').value = blog.description || '';
    $('tags').value = (blog.tags || []).join(', ');
    $('cover-preview').src = blog.coverImageUrl && blog.coverImageUrl.startsWith('/files/')
      ? blog.coverImageUrl : '/images/blog-default.svg';
  }

  async function uploadCover(e) {
    const file = e.target.files[0];
    if (!file) {
      return;
    }
    const result = await window.api.upload('/api/files/blog-cover', file, { userAction: true });
    if (!result.ok || !result.data) {
      document.querySelector('[data-error-for="cover"]').textContent = errorText(result, '이미지를 올리지 못했어요.');
      return;
    }
    state.coverFileId = result.data.fileId;
    state.removeCover = false;
    $('cover-preview').src = result.data.url;
  }

  async function saveInfo(e) {
    e.preventDefault();
    document.querySelectorAll('#info-form [data-error-for]').forEach(n => { n.textContent = ''; });
    const tags = $('tags').value.split(',').map(t => t.trim()).filter(Boolean);
    const result = await window.api.put(base() + '/info', {
      name: $('name').value.trim(), description: $('description').value, coverFileId: state.coverFileId,
      removeCover: state.removeCover, tags: tags
    }, { userAction: true });
    if (!result.ok) {
      showFieldErrors(result);
      say('info-message', errorText(result, '저장하지 못했어요.'));
      return;
    }
    state.coverFileId = null;
    say('info-message', '저장했어요.', true);
  }

  // ---- 공개 범위 ----
  function fillSettings(blog) {
    document.querySelectorAll('input[name="visibility"]').forEach(r => { r.checked = r.value === blog.visibility; });
    document.querySelectorAll('input[name="joinPolicy"]').forEach(r => { r.checked = r.value === blog.joinPolicy; });
    $('share-block').classList.toggle('hidden', blog.visibility !== 'UNLISTED' || !blog.shareUrl);
    if (blog.shareUrl) {
      $('share-url').value = window.location.origin + blog.shareUrl;
    }
  }

  async function saveSettings(e) {
    e.preventDefault();
    const visibility = document.querySelector('input[name="visibility"]:checked');
    const joinPolicy = document.querySelector('input[name="joinPolicy"]:checked');
    const result = await window.api.put(base() + '/settings', {
      visibility: visibility ? visibility.value : null, joinPolicy: joinPolicy ? joinPolicy.value : null
    }, { userAction: true });
    if (!result.ok) {
      say('settings-message', errorText(result, '저장하지 못했어요.'));
      return;
    }
    say('settings-message', '저장했어요.', true);
    await reloadBlog();
  }

  async function regenerateShare() {
    if (!window.confirm('새 링크를 만들면 이전 링크로는 들어올 수 없어요. 계속할까요?')) {
      return;
    }
    const result = await window.api.post(base() + '/share-link', undefined, { userAction: true });
    if (result.ok && result.data) {
      $('share-url').value = window.location.origin + result.data.shareUrl;
    }
  }

  // ---- 멤버 ----
  function permissionBox(label, checked) {
    const wrap = el('label', 'checkbox inline');
    const input = el('input');
    input.type = 'checkbox';
    input.checked = checked;
    wrap.append(input, document.createTextNode(' ' + label));
    return { wrap, input };
  }

  async function loadMembers() {
    const result = await window.api.get(base() + '/members');
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const owner = state.blog.myRole === 'OWNER';
    const rows = $('member-rows');
    rows.replaceChildren();
    result.data.forEach(m => {
      const tr = el('tr');
      [m.nickname, m.name, m.email, m.phone, ROLE[m.role] || m.role].forEach(v => tr.append(el('td', null, v || '')));
      const actions = el('td');
      if (m.suspensionCount >= 3) {
        actions.append(el('span', 'badge', '정지 ' + m.suspensionCount + '회'));
      }
      if (m.suspendedUntil) {
        actions.append(el('span', 'hint', ' 정지 중(' + formatTime(m.suspendedUntil) + '까지) '));
      }
      const canSanction = m.role === 'MEMBER' || (owner && m.role === 'SUB_OWNER');
      if (canSanction && m.userId !== state.me) {
        actions.append(sanctionButtons(m));
      }
      if (owner && m.role !== 'OWNER') {
        const sub = permissionBox('부블로그장', m.role === 'SUB_OWNER');
        const info = permissionBox('정보 수정', m.canEditInfo);
        const members = permissionBox('멤버 관리', m.canManageMembers);
        const posts = permissionBox('글 관리', m.canManagePosts);
        const save = el('button', 'link-button', '권한 저장');
        save.type = 'button';
        save.addEventListener('click', async () => {
          const r = await window.api.put(base() + '/members/' + m.userId + '/sub-owner', {
            subOwner: sub.input.checked, canEditInfo: info.input.checked,
            canManageMembers: members.input.checked, canManagePosts: posts.input.checked
          }, { userAction: true });
          say('members-message', r.ok ? m.nickname + '님의 권한을 저장했어요.' : errorText(r, '저장하지 못했어요.'), r.ok);
          loadMembers();
        });
        const transfer = el('button', 'link-button', '위임');
        transfer.type = 'button';
        transfer.disabled = state.blog.status === 'CLOSING';
        transfer.addEventListener('click', async () => {
          if (!window.confirm(m.nickname + '님에게 블로그장 위임을 요청할까요? 수락하면 나는 일반 멤버가 돼요.')) {
            return;
          }
          const r = await window.api.post(base() + '/transfer', { toUserId: m.userId }, { userAction: true });
          say('transfer-message', r.ok ? m.nickname + '님에게 위임을 요청했어요.' : errorText(r, '요청하지 못했어요.'), r.ok);
          loadTransfer();
        });
        actions.append(sub.wrap, info.wrap, members.wrap, posts.wrap, save, transfer);
      }
      tr.append(actions);
      rows.append(tr);
    });
    // 위임할 멤버가 없으면 폐쇄만 할 수 있다 (BLG-08)
    if (owner && result.data.length <= 1) {
      $('transfer-text').textContent = '위임할 멤버가 없어요. 블로그를 정리하려면 폐쇄해 주세요.';
    }
  }

  // ---- 제재 (BLG-11, BLG-13) ----
  function askDays() {
    const raw = window.prompt('정지 기간을 골라 주세요: 3, 14, 30 (일) 또는 영구', '3');
    if (raw === null) {
      return undefined;
    }
    const value = raw.trim();
    if (value === '영구') {
      return null;
    }
    const days = parseInt(value, 10);
    return [3, 14, 30].includes(days) ? days : undefined;
  }

  async function sanction(userId, nickname, type, reportId) {
    const labels = { WARNING: '경고', SUSPENSION: '정지', RELEASE: '정지 해제', KICK: '강제 퇴장' };
    let days = null;
    if (type === 'SUSPENSION') {
      days = askDays();
      if (days === undefined) {
        return false;
      }
    }
    const reason = type === 'RELEASE' ? '' : window.prompt(nickname + '님에게 ' + labels[type] + ' 사유를 적어 주세요.');
    if (reason === null || (type !== 'RELEASE' && !reason.trim())) {
      return false;
    }
    if (type === 'KICK' && !window.confirm(nickname + '님을 강제 퇴장할까요? 블랙리스트에 올라 다시 참여할 수 없어요.')) {
      return false;
    }
    const result = reportId
      ? await window.api.post(base() + '/reports/' + reportId + '/resolve', { result: type, days: days, reason: reason },
        { userAction: true })
      : await window.api.post(base() + '/members/' + userId + '/sanctions', { type: type, days: days, reason: reason },
        { userAction: true });
    say(reportId ? 'reports-message' : 'members-message',
      result.ok ? nickname + '님에게 ' + labels[type] + ' 처리했어요.' : errorText(result, '처리하지 못했어요.'), result.ok);
    return result.ok;
  }

  function sanctionButtons(m) {
    const box = el('span');
    [['WARNING', '경고'], ['SUSPENSION', '정지'], ['RELEASE', '정지 해제'], ['KICK', '강제 퇴장']].forEach(([type, label]) => {
      if (type === 'RELEASE' && !m.suspendedUntil) {
        return;
      }
      const b = el('button', type === 'KICK' ? 'link-button danger' : 'link-button', label);
      b.type = 'button';
      b.addEventListener('click', async () => {
        if (await sanction(m.userId, m.nickname, type)) {
          loadMembers();
          loadBlacklist();
        }
      });
      box.append(b);
    });
    return box;
  }

  // ---- 신고 처리 ----
  function snapshotText(raw) {
    try {
      const s = JSON.parse(raw);
      return [s.title, s.postTitle, s.content, s.description].filter(Boolean).join(' / ');
    } catch (e) {
      return '';
    }
  }

  async function loadReports() {
    const result = await window.api.get(base() + '/reports');
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    const list = $('report-list');
    list.replaceChildren();
    const TYPE = { POST: '글', COMMENT: '댓글', USER: '멤버' };
    result.data.forEach(r => {
      const li = el('li');
      li.append(el('strong', null, (TYPE[r.targetType] || r.targetType) + ' · ' + (r.targetNickname || '') + ' · ' + r.reasonLabel));
      li.append(el('p', 'hint', snapshotText(r.snapshot)));
      if (r.detail) {
        li.append(el('p', 'hint', '신고 내용: ' + r.detail));
      }
      li.append(el('p', 'hint', '신고한 사람 ' + r.reporterNickname + ' · ' + formatTime(r.createdAt)));
      const actions = el('div', 'row');
      const ok = el('button', 'link-button', '문제 없음');
      ok.type = 'button';
      ok.addEventListener('click', async () => {
        const res = await window.api.post(base() + '/reports/' + r.id + '/resolve', { result: 'NO_ISSUE' }, { userAction: true });
        say('reports-message', res.ok ? '처리했어요.' : errorText(res, '처리하지 못했어요.'), res.ok);
        loadReports();
      });
      actions.append(ok);
      if (r.targetUserId) {
        [['WARNING', '경고'], ['SUSPENSION', '정지'], ['KICK', '강제 퇴장']].forEach(([type, label]) => {
          const b = el('button', type === 'KICK' ? 'link-button danger' : 'link-button', label);
          b.type = 'button';
          b.addEventListener('click', async () => {
            if (await sanction(r.targetUserId, r.targetNickname || '', type, r.id)) {
              loadReports();
              loadMembers();
            }
          });
          actions.append(b);
        });
      }
      li.append(actions);
      list.append(li);
    });
    $('report-empty').classList.toggle('hidden', result.data.length > 0);
  }

  // ---- 블랙리스트·해제 문의 (BLG-11, BLG-12) ----
  async function loadBlacklist() {
    const inquiries = await window.api.get(base() + '/blacklist-inquiries');
    if (inquiries.ok && Array.isArray(inquiries.data)) {
      const list = $('inquiry-list');
      list.replaceChildren();
      inquiries.data.forEach(i => {
        const li = el('li');
        li.append(el('strong', null, i.nickname + ' · ' + (i.status === 'PENDING' ? '대기' : i.status === 'RELEASED' ? '해제함' : '거절함')));
        li.append(el('p', null, i.message));
        li.append(el('p', 'hint', '이름 ' + (i.nameMatched ? '일치' : '불일치') + ' · 전화번호 ' + (i.phoneMatched ? '일치' : '불일치')
          + ' · ' + formatTime(i.createdAt)));
        if (i.status === 'PENDING') {
          ['release', 'reject'].forEach(action => {
            const b = el('button', action === 'release' ? 'link-button' : 'link-button danger',
              action === 'release' ? '해제' : '거절');
            b.type = 'button';
            b.addEventListener('click', async () => {
              await window.api.post(base() + '/blacklist-inquiries/' + i.id + '/' + action, undefined, { userAction: true });
              loadBlacklist();
            });
            li.append(b);
          });
        }
        list.append(li);
      });
      $('inquiry-empty').classList.toggle('hidden', inquiries.data.length > 0);
    }
    const entries = await window.api.get(base() + '/blacklist');
    if (entries.ok && Array.isArray(entries.data)) {
      const list = $('blacklist-list');
      list.replaceChildren();
      entries.data.forEach(b => {
        list.append(el('li', null, formatTime(b.createdAt) + ' · 등록 ' + (b.createdBy || '') + ' · 사유: ' + (b.reason || '')
          + (b.releasedAt ? ' · 해제됨' : '')));
      });
      $('blacklist-empty').classList.toggle('hidden', entries.data.length > 0);
    }
  }

  // ---- 위임 ----
  async function loadTransfer() {
    const result = await window.api.get(base() + '/transfer');
    const pending = result.status === 200 && result.data;
    $('transfer-cancel').classList.toggle('hidden', !pending);
    if (pending) {
      $('transfer-text').textContent = result.data.toNickname + '님에게 위임을 요청했어요. ' + formatTime(result.data.expiresAt)
        + '까지 응답이 없으면 자동으로 취소돼요.';
    }
  }

  async function cancelTransfer() {
    const result = await window.api.delete(base() + '/transfer', { userAction: true });
    say('transfer-message', result.ok ? '위임 요청을 취소했어요.' : errorText(result, '취소하지 못했어요.'), result.ok);
    $('transfer-text').textContent = '멤버 목록에서 "위임"을 누르면 그 멤버에게 요청이 가요.';
    loadTransfer();
  }

  // ---- 폐쇄 ----
  function fillClose(blog) {
    const closing = blog.status === 'CLOSING';
    $('close-button').classList.toggle('hidden', closing);
    $('close-cancel').classList.toggle('hidden', !closing);
    if (closing && blog.closeScheduledAt) {
      $('close-text').textContent = formatTime(blog.closeScheduledAt) + '에 폐쇄될 예정이에요. 그 전에 철회할 수 있어요.';
    }
  }

  async function closeBlog() {
    if (!window.confirm('블로그를 폐쇄할까요? 7일 뒤 새벽 4시에 닫히고, 대기 중인 위임 요청은 취소돼요.')) {
      return;
    }
    const result = await window.api.post(base() + '/close', undefined, { userAction: true });
    say('close-message', result.ok ? '폐쇄를 예약했어요.' : errorText(result, '폐쇄하지 못했어요.'), result.ok);
    await reloadBlog();
  }

  async function cancelClose() {
    const result = await window.api.delete(base() + '/close', { userAction: true });
    say('close-message', result.ok ? '폐쇄를 철회했어요.' : errorText(result, '철회하지 못했어요.'), result.ok);
    $('close-text').textContent = '폐쇄를 누르면 7일 뒤 새벽 4시에 닫혀요.';
    await reloadBlog();
  }

  async function reloadBlog() {
    const result = await window.api.get(base(), { userAction: true });
    if (!result.ok || !result.data) {
      $('page-message').textContent = errorText(result, '블로그를 불러오지 못했어요.');
      return null;
    }
    state.blog = result.data;
    const blog = state.blog;
    const p = blog.permissions || {};
    const owner = blog.myRole === 'OWNER';
    $('blog-link').textContent = blog.name;
    $('blog-link').href = '/blog/' + encodeURIComponent(blog.slug);
    $('info-form').classList.toggle('hidden', !p.canEditInfo);
    $('settings-form').classList.toggle('hidden', !owner);
    $('members-block').classList.toggle('hidden', !p.canManageMembers);
    $('reports-block').classList.toggle('hidden', !p.canManageMembers);
    $('blacklist-block').classList.toggle('hidden', !p.canManageMembers);
    $('transfer-block').classList.toggle('hidden', !owner);
    $('close-block').classList.toggle('hidden', !owner);
    if (!p.canEditInfo && !p.canManageMembers && !owner) {
      $('page-message').textContent = '이 블로그를 관리할 권한이 없어요.';
    }
    fillInfo(blog);
    fillSettings(blog);
    fillClose(blog);
    return blog;
  }

  document.addEventListener('DOMContentLoaded', async () => {
    state.slug = decodeURIComponent(window.location.pathname.split('/')[2] || '');
    const blog = await reloadBlog();
    if (!blog) {
      return;
    }
    $('info-form').addEventListener('submit', saveInfo);
    $('cover').addEventListener('change', uploadCover);
    $('cover-remove').addEventListener('click', () => {
      state.coverFileId = null;
      state.removeCover = true;
      $('cover-preview').src = '/images/blog-default.svg';
    });
    $('settings-form').addEventListener('submit', saveSettings);
    $('share-regenerate').addEventListener('click', regenerateShare);
    $('transfer-cancel').addEventListener('click', cancelTransfer);
    $('close-button').addEventListener('click', closeBlog);
    $('close-cancel').addEventListener('click', cancelClose);
    if (blog.permissions && blog.permissions.canManageMembers) {
      loadMembers();
      loadReports();
      loadBlacklist();
    }
    if (blog.myRole === 'OWNER') {
      loadTransfer();
    }
  });
})();
