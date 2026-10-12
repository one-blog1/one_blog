// 화면 스크립트가 함께 쓰는 작은 도우미 (여러 파일에 똑같이 있던 것을 모음).
// $: id로 요소 찾기, el: 요소 만들기(글자는 textContent로만, SEC-06), 날짜 표시, 프로필 사진 주소,
// 공유 링크 key 이어 붙이기, 글 상세 주소, 입력칸 오류 글.
// 날짜·주소 모양을 바꿀 때는 여기만 고친다. 화면 파일에서 toLocaleString이나 '/posts/'를 직접 쓰지 않는다.
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

  /** 10월 8일 오후 2시처럼 월·일·시만 (예약된 시각 안내, 블로그 폐쇄 예정 등). 잘못된 값이면 빈 글자. */
  function formatDayHour(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? ''
      : d.toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric' });
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

  /** 글 상세 주소 /blog/{블로그 주소}/posts/{글 번호}. query는 '?key=...'나 '#comment-3'처럼 뒤에 그대로 붙일 글자 (없으면 생략). */
  function postHref(blogSlug, postId, query) {
    return '/blog/' + encodeURIComponent(blogSlug) + '/posts/' + encodeURIComponent(postId) + (query || '');
  }

  /** 입력칸 아래 오류 글([data-error-for="칸 이름"])을 넣거나 비운다. */
  function setError(field, text) {
    const node = document.querySelector('[data-error-for="' + field + '"]');
    if (node) {
      node.textContent = text || '';
    }
  }

  window.ui = { $: $, el: el, formatDate: formatDate, formatDateTime: formatDateTime, formatDayHour: formatDayHour,
    avatarSrc: avatarSrc, keyQuery: keyQuery, postHref: postHref, setError: setError };
})();
