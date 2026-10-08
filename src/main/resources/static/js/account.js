// 내 정보 (USR-07): 보기 → [프로필 수정] → 비밀번호 재확인(D-102) → 닉네임·프로필 사진·전화번호·소개, 비밀번호 수정.
// 재확인은 서버가 HttpOnly 쿠키로 기억하고, 수정 API가 직접 검사한다. 화면 전환은 보기 편하라고 하는 것뿐이다.
(function () {
  'use strict';

  const state = { fileId: null, remove: false };

  function $(id) {
    return document.getElementById(id);
  }

  function setError(field, text) {
    const node = document.querySelector('[data-error-for="' + field + '"]');
    if (node) {
      node.textContent = text || '';
    }
  }

  function clearErrors(form) {
    form.querySelectorAll('[data-error-for]').forEach(n => { n.textContent = ''; });
  }

  function showErrors(result, messageId, fallback) {
    const data = result.data || {};
    if (Array.isArray(data.fieldErrors)) {
      data.fieldErrors.forEach(f => setError(f.field, f.message));
    }
    const message = $(messageId);
    message.className = 'message error';
    message.textContent = data.message || fallback;
  }

  function avatarSrc(url) {
    return url && url.startsWith('/files/') ? url : '/images/avatar-default.svg';
  }

  function show(view) {
    ['account-view', 'reauth-view', 'edit-view'].forEach(id => $(id).classList.toggle('hidden', id !== view));
    const focus = { 'reauth-view': 'reauth-password', 'edit-view': 'nickname' }[view];
    if (focus) {
      $(focus).focus();
    }
  }

  function formatDate(value) {
    const d = value ? new Date(value) : null;
    return d && !isNaN(d) ? d.getFullYear() + '. ' + (d.getMonth() + 1) + '. ' + d.getDate() + '.' : '';
  }

  function formatTime(value) {
    const d = new Date(value);
    return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  // 내 정보 화면: 서버가 가린 값 그대로 (이메일·이름·전화번호)
  function fill(account) {
    $('account-email').textContent = account.email;
    $('account-name').textContent = account.name;
    $('view-nickname').textContent = account.nickname;
    $('view-bio').textContent = account.bio || '소개가 아직 없어요.';
    $('view-phone').textContent = account.phone || '';
    $('view-created').textContent = formatDate(account.createdAt);
    $('view-avatar').src = avatarSrc(account.profileImageUrl);
  }

  // 수정 화면: 비밀번호 재확인 뒤 받은 가리지 않은 값
  function fillForm(account) {
    $('nickname').value = account.nickname;
    $('phone').value = account.phone || '';
    $('bio').value = account.bio || '';
    $('profile-preview').src = avatarSrc(account.profileImageUrl);
  }

  async function upload(event) {
    setError('file', '');
    const file = event.target.files[0];
    if (!file) {
      return;
    }
    if (file.size > 3 * 1024 * 1024) {
      setError('file', '3MB 이하의 사진만 올릴 수 있어요.');
      event.target.value = '';
      return;
    }
    const result = await window.api.upload('/api/files/profile-image', file, { userAction: true });
    if (!result.ok || !result.data) {
      setError('file', (result.data && result.data.message) || '사진을 올리지 못했어요.');
      return;
    }
    state.fileId = result.data.fileId;
    state.remove = false;
    $('profile-preview').src = avatarSrc(result.data.url);
  }

  // 확인 시간이 지나 서버가 거절하면 다시 비밀번호부터
  function needsReauth(result) {
    if (result.status === 403 && result.data && result.data.code === 'REAUTH_REQUIRED') {
      $('reauth-message').className = 'message error';
      $('reauth-message').textContent = '확인한 지 10분이 지났어요. 비밀번호를 다시 넣어 주세요.';
      show('reauth-view');
      return true;
    }
    return false;
  }

  async function startEdit() {
    const status = await window.api.get('/api/me/reauth', { userAction: true });
    if (status.ok && status.data && status.data.verified) {
      openEdit(status.data.expiresAt);
      return;
    }
    $('reauth-form').reset();
    $('reauth-message').textContent = '';
    setError('password', '');
    show('reauth-view');
  }

  // 수정 화면에서만 가리지 않은 값을 받아 채운다. 내 정보 화면은 늘 가린 값 (D-110)
  async function openEdit(expiresAt) {
    const result = await window.api.get('/api/me/account?full=true', { userAction: true });
    if (!result.ok || !result.data || result.data.masked) {
      $('reauth-message').className = 'message error';
      $('reauth-message').textContent = '비밀번호를 다시 넣어 주세요.';
      show('reauth-view');
      return;
    }
    fillForm(result.data);
    $('reauth-until').textContent = expiresAt ? formatTime(expiresAt) + '까지 수정할 수 있어요.' : '';
    show('edit-view');
  }

  async function backToAccount() {
    const result = await window.api.get('/api/me/account', { userAction: true });
    if (result.ok && result.data) {
      fill(result.data);
    }
    show('account-view');
  }

  async function reauth(event) {
    event.preventDefault();
    setError('password', '');
    $('reauth-message').textContent = '';
    const result = await window.api.post('/api/me/reauth', { password: $('reauth-password').value },
      { userAction: true });
    $('reauth-password').value = '';
    if (!result.ok || !result.data) {
      showErrors(result, 'reauth-message', '확인하지 못했어요.');
      return;
    }
    openEdit(result.data.expiresAt);
  }

  async function saveProfile(event) {
    event.preventDefault();
    clearErrors($('profile-form'));
    $('profile-message').textContent = '';
    const body = {
      nickname: $('nickname').value.trim(),
      phone: $('phone').value.trim(),
      bio: $('bio').value,
      profileFileId: state.fileId,
      removeProfileImage: state.remove
    };
    const result = await window.api.put('/api/me/profile', body, { userAction: true });
    if (needsReauth(result)) {
      return;
    }
    if (!result.ok || !result.data) {
      showErrors(result, 'profile-message', '저장하지 못했어요.');
      return;
    }
    state.fileId = null;
    state.remove = false;
    fillForm(result.data);
    $('profile-message').className = 'message ok';
    $('profile-message').textContent = '저장했어요.';
  }

  async function changePassword(event) {
    event.preventDefault();
    clearErrors($('password-form'));
    $('password-message').textContent = '';
    const body = {
      newPassword: $('new-password').value,
      newPasswordConfirm: $('new-password-confirm').value
    };
    const result = await window.api.put('/api/me/password', body, { userAction: true });
    if (needsReauth(result)) {
      return;
    }
    if (!result.ok) {
      showErrors(result, 'password-message', '바꾸지 못했어요.');
      return;
    }
    $('password-form').reset();
    $('password-message').className = 'message ok';
    $('password-message').textContent = '비밀번호를 바꿨어요. 다른 기기의 로그인은 끝났어요.';
  }

  document.addEventListener('DOMContentLoaded', async () => {
    const result = await window.api.get('/api/me/account', { userAction: true, redirectOnLogout: true });
    if (result.status === 401) {
      window.location.href = '/login.html';
      return;
    }
    if (!result.ok || !result.data) {
      $('account-message').className = 'message error';
      $('account-message').textContent = (result.data && result.data.message) || '정보를 불러오지 못했어요.';
      $('edit-start').disabled = true;
      return;
    }
    fill(result.data);
    $('profile-file').addEventListener('change', upload);
    $('profile-remove').addEventListener('click', () => {
      state.fileId = null;
      state.remove = true;
      $('profile-file').value = '';
      $('profile-preview').src = avatarSrc(null);
    });
    $('profile-form').addEventListener('submit', saveProfile);
    $('password-form').addEventListener('submit', changePassword);
    $('edit-start').addEventListener('click', startEdit);
    $('reauth-form').addEventListener('submit', reauth);
    $('reauth-cancel').addEventListener('click', () => show('account-view'));
    $('edit-done').addEventListener('click', () => {
      $('profile-message').textContent = '';
      $('password-message').textContent = '';
      backToAccount().then(() => $('edit-start').focus());
    });
  });
})();
