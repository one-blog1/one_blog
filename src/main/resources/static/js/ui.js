// 화면 스크립트가 함께 쓰는 작은 도우미 (여러 파일에 똑같이 있던 것을 모음).
// $: id로 요소 찾기, el: 요소 만들기(글자는 textContent로만, SEC-06), 날짜 표시, 프로필 사진 주소, 공유 링크 key 이어 붙이기.
(function () {
  'use strict';

  function $(id) {
    return document.getElementById(id);
  }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = String(text);
    }
    return node;
  }

  /** 2026. 10. 8. 처럼 날짜만. 잘못된 값이면 빈 글자. */
  function formatDate(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('ko-KR');
  }

  /** 날짜와 시각. 잘못된 값이면 빈 글자. */
  function formatDateTime(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('ko-KR');
  }

  /** 서버가 준 /files/ 주소만 사진으로 쓰고, 아니면 기본 사진. */
  function avatarSrc(url) {
    return typeof url === 'string' && url.startsWith('/files/') ? url : '/images/avatar-default.svg';
  }

  /** 일부 공개 블로그의 공유 링크 key를 API 주소 뒤에 이어 붙인다 (BLG-01). */
  function keyQuery() {
    const key = new URLSearchParams(window.location.search).get('key');
    return key ? '?key=' + encodeURIComponent(key) : '';
  }

  window.ui = { $: $, el: el, formatDate: formatDate, formatDateTime: formatDateTime, avatarSrc: avatarSrc,
    keyQuery: keyQuery };
})();
