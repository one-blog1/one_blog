// 글 본문 에디터 (D-74 Toast UI Editor, 마크다운). 이미지를 붙이면 바로 올리고 본문에 넣는다 (BRD-05, 6.3).
// Toast UI를 불러오지 못하면(네트워크 등) post-edit.js가 글상자로 대신 쓴다.
(function () {
  'use strict';

  const MAX_IMAGE = 3 * 1024 * 1024;
  const MAX_IMAGES = 10;
  const ALLOWED = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  const IMAGE_URL = /\/files\/[0-9a-f-]{36}\.(?:jpg|jpeg|png|gif|webp)/g;

  function countImages(markdown) {
    const found = new Set(markdown.match(IMAGE_URL) || []);
    return found.size;
  }

  function showError(text) {
    const node = document.querySelector('[data-error-for="imageFileIds"]');
    if (node) {
      node.textContent = text || '';
    }
  }

  /**
   * @param {HTMLElement} container 에디터를 그릴 곳
   * @param {HTMLTextAreaElement} fallback 에디터가 없을 때 쓰는 글상자 (에디터가 뜨면 숨긴다)
   * @param {() => void} onChange 내용이 바뀔 때
   * @returns 에디터 또는 null
   */
  function create(container, fallback, onChange) {
    if (!window.toastui || !window.toastui.Editor) {
      return null;
    }
    fallback.classList.add('hidden');
    const editor = new window.toastui.Editor({
      el: container,
      height: '520px',
      initialEditType: 'markdown',
      previewStyle: window.matchMedia('(max-width: 768px)').matches ? 'tab' : 'vertical',
      language: 'ko-KR',
      usageStatistics: false,
      events: { change: onChange },
      hooks: {
        addImageBlobHook: async (blob, callback) => {
          showError('');
          if (!ALLOWED.includes(blob.type)) {
            showError('jpg, png, gif, webp 이미지만 넣을 수 있어요.');
            return;
          }
          if (blob.size > MAX_IMAGE) {
            showError('이미지는 한 장에 3MB까지 넣을 수 있어요.');
            return;
          }
          if (countImages(editor.getMarkdown()) >= MAX_IMAGES) {
            showError('이미지는 글 하나에 10장까지 넣을 수 있어요.');
            return;
          }
          const file = blob instanceof File ? blob : new File([blob], 'image.' + blob.type.split('/')[1], { type: blob.type });
          const result = await window.api.upload('/api/files/post-image', file, { userAction: true });
          if (result.ok && result.data && String(result.data.url).startsWith('/files/')) {
            callback(result.data.url, '');
          } else {
            showError((result.data && result.data.message) || '이미지를 올리지 못했어요.');
          }
        }
      }
    });
    return editor;
  }

  window.postEditor = { create: create };
  // 서버가 본문에서 이미지를 찾아 연결하므로 따로 보낼 번호는 없다 (PostImageExtension)
  window.postImages = { ids: () => [] };
})();
