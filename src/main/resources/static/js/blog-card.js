// 블로그 카드 하나를 만든다. 메인 목록과 내 블로그가 함께 쓴다 (research R9).
// 사용자 입력(이름·소개·태그·닉네임)은 모두 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const DEFAULT_COVER = '/images/blog-default.svg';
  const VISIBILITY_LABEL = { PUBLIC: '공개', UNLISTED: '일부 공개', PRIVATE: '비공개' };
  const ROLE_LABEL = { OWNER: '블로그장', SUB_OWNER: '부블로그장', MEMBER: '멤버' };

  const el = window.ui.el;

  /** 서버가 준 slug로 블로그 주소를 만든다. */
  function blogUrl(slug) {
    return '/blog/' + encodeURIComponent(slug);
  }

  /** 서버가 준 /files/ 주소만 이미지로 쓴다. 없으면 기본 이미지. */
  function coverSrc(url) {
    return typeof url === 'string' && url.startsWith('/files/') ? url : DEFAULT_COVER;
  }

  /** 블로그마다 정해진 색 (주소로 정하므로 늘 같은 색). 대표 이미지가 없을 때의 표지와 블로그 첫 화면에 쓴다. */
  const TILE_COUNT = 6;
  function tileClass(slug) {
    let hash = 0;
    for (const ch of String(slug || '')) {
      hash = (hash * 31 + ch.codePointAt(0)) >>> 0;
    }
    return 'tile-' + (hash % TILE_COUNT);
  }

  /** 이름의 첫 글자 (표지에 크게 쓴다). */
  function initial(name) {
    const first = Array.from(String(name || '').trim())[0];
    return first ? first.toUpperCase() : '?';
  }

  /** 대표 이미지가 있으면 사진, 없으면 블로그 색 표지에 첫 글자. */
  function cover(blog, className) {
    if (typeof blog.coverImageUrl === 'string' && blog.coverImageUrl.startsWith('/files/')) {
      const img = el('img', className);
      img.src = blog.coverImageUrl;
      img.alt = '';
      img.loading = 'lazy';
      return img;
    }
    const tile = el('div', className + ' cover-tile ' + tileClass(blog.slug));
    tile.setAttribute('aria-hidden', 'true');
    tile.append(el('span', 'cover-initial', initial(blog.name)));
    return tile;
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

    card.classList.add(tileClass(blog.slug));
    card.append(cover(blog, 'blog-cover'));

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
    cover: cover,
    tileClass: tileClass,
    initial: initial,
    el: el,
    VISIBILITY_LABEL: VISIBILITY_LABEL,
    ROLE_LABEL: ROLE_LABEL
  };
})();
