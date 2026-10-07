// 내 정보 수정 (USR-07): 닉네임·프로필 사진·전화번호·소개, 비밀번호.
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

  function fill(account) {
    $('account-email').textContent = account.email;
    $('account-name').textContent = account.name;
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
    if (!result.ok || !result.data) {
      showErrors(result, 'profile-message', '저장하지 못했어요.');
      return;
    }
    state.fileId = null;
    state.remove = false;
    fill(result.data);
    $('profile-message').className = 'message ok';
    $('profile-message').textContent = '저장했어요.';
  }

  async function changePassword(event) {
    event.preventDefault();
    clearErrors($('password-form'));
    $('password-message').textContent = '';
    const body = {
      currentPassword: $('current-password').value,
      newPassword: $('new-password').value,
      newPasswordConfirm: $('new-password-confirm').value
    };
    const result = await window.api.put('/api/me/password', body, { userAction: true });
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
      $('profile-message').textContent = (result.data && result.data.message) || '정보를 불러오지 못했어요.';
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
  });
})();
