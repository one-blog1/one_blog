// 글 목록 한 줄. 블로그 화면, 카테고리·태그 목록, 검색·피드가 함께 쓴다.
// 사용자 입력(제목·작성자·태그)은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

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

  function formatDate(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('ko-KR');
  }

  /**
   * @param {object} post 목록 항목
   * @param {string} href 글 주소
   * @param {object} [options] { blogName }
   */
  function create(post, href, options) {
    const opts = options || {};
    const li = el('li', 'post-item');
    const link = el('a', 'post-link');
    link.href = href;
    if (post.notice) {
      link.append(el('span', 'badge', '공지'));
    }
    link.append(el('span', 'post-title', post.title));
    if (post.thumbnailUrl && post.thumbnailUrl.startsWith('/files/')) {
      // 글 제목에 마우스를 올리면 첫 사진이 보인다 (6.3 썸네일)
      const img = el('img', 'post-thumb');
      img.src = post.thumbnailUrl;
      img.alt = '';
      img.loading = 'lazy';
      link.append(img);
    }
    li.append(link);

    const meta = [];
    if (opts.blogName) {
      meta.push(opts.blogName);
    }
    if (post.categoryName) {
      meta.push(post.categoryName);
    }
    meta.push(window.userLink ? window.userLink(post.authorName) : post.authorName, formatDate(post.createdAt),
      '조회 ' + post.viewCount, '좋아요 ' + post.likeCount, '댓글 ' + post.commentCount);
    const metaLine = el('p', 'post-meta');
    if (window.joinMeta) {
      window.joinMeta(metaLine, meta);
    } else {
      metaLine.textContent = meta.filter(Boolean).join(' · ');
    }
    li.append(metaLine);
    if (Array.isArray(post.tags) && post.tags.length > 0) {
      const tags = el('ul', 'tag-list');
      post.tags.forEach(t => {
        const tag = el('li', 'tag');
        const a = el('a', null, '#' + t);
        a.href = '/tags/' + encodeURIComponent(t);
        tag.append(a);
        tags.append(tag);
      });
      li.append(tags);
    }
    return li;
  }

  window.postItem = { create: create, formatDate: formatDate, el: el };
})();
