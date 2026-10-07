// 블로그 카드 하나를 만든다. 메인 목록과 내 블로그가 함께 쓴다 (research R9).
// 사용자 입력(이름·소개·태그·닉네임)은 모두 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const DEFAULT_COVER = '/images/blog-default.svg';
  const VISIBILITY_LABEL = { PUBLIC: '공개', UNLISTED: '일부 공개', PRIVATE: '비공개' };
  const ROLE_LABEL = { OWNER: '블로그장', SUB_OWNER: '부블로그장', MEMBER: '멤버' };

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

  /** 서버가 준 slug로 블로그 주소를 만든다. */
  function blogUrl(slug) {
    return '/blog/' + encodeURIComponent(slug);
  }

  /** 서버가 준 /files/ 주소만 이미지로 쓴다. 없으면 기본 이미지. */
  function coverSrc(url) {
    return typeof url === 'string' && url.startsWith('/files/') ? url : DEFAULT_COVER;
  }

  function shorten(text, max) {
    if (!text) {
      return '';
    }
    return text.length > max ? text.slice(0, max) + '…' : text;
  }

  /**
   * @param {object} blog 목록 항목 또는 내 블로그 항목
   * @param {object} [options] { showVisibility, showRole }
   */
  function create(blog, options) {
    const opts = options || {};
    const card = el('a', 'blog-card');
    card.href = blogUrl(blog.slug);

    const img = el('img', 'blog-cover');
    img.src = coverSrc(blog.coverImageUrl);
    img.alt = '';
    img.loading = 'lazy';
    card.append(img);

    const body = el('div', 'blog-card-body');
    body.append(el('strong', 'blog-name', blog.name));
    if (blog.description) {
      body.append(el('p', 'blog-description', shorten(blog.description, 80)));
    }

    const meta = el('p', 'blog-meta');
    const parts = [];
    if (opts.showVisibility && blog.visibility) {
      parts.push(VISIBILITY_LABEL[blog.visibility] || blog.visibility);
    }
    if (opts.showRole && blog.role) {
      parts.push(ROLE_LABEL[blog.role] || blog.role);
    }
    if (blog.ownerNickname) {
      parts.push('블로그장 ' + blog.ownerNickname);
    }
    parts.push('멤버 ' + blog.memberCount + '명');
    meta.textContent = parts.join(' · ');
    body.append(meta);

    if (Array.isArray(blog.tags) && blog.tags.length > 0) {
      const tags = el('ul', 'tag-list');
      blog.tags.forEach(t => tags.append(el('li', 'tag', '#' + t)));
      body.append(tags);
    }
    card.append(body);
    return card;
  }

  window.blogCard = {
    create: create,
    blogUrl: blogUrl,
    coverSrc: coverSrc,
    el: el,
    VISIBILITY_LABEL: VISIBILITY_LABEL,
    ROLE_LABEL: ROLE_LABEL
  };
})();
