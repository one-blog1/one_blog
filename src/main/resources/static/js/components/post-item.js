// 글 목록 한 줄. 블로그 화면, 카테고리·태그 목록, 검색·피드, 메인이 함께 쓴다.
// 왼쪽: 작성자 사진·닉네임·날짜 → 굵은 제목 → 본문 2줄 미리보기 → 블로그·카테고리와 좋아요·댓글·조회 → 태그
// (블로그·카테고리를 머리줄 오른쪽 끝에 두면 썸네일이 있을 때 글자가 밀려 아래 줄 맨 앞으로 옮김)
// 오른쪽: 썸네일(본문 첫 사진, 첫 미디어가 영상 링크면 서버가 받아 둔 영상 썸네일과 "▶ YouTube" 표시) (D-128)
// 블로그 공지(notice)는 예전처럼 제목과 정보 한 줄로 짧게.
// 사용자 입력(제목·작성자·미리보기·태그)은 textContent로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const el = window.ui.el;

  const formatDate = window.ui.formatDate;

  const VIDEO_LABELS = { youtube: 'YouTube', chzzk: '치지직', soop: 'SOOP' };

  function authorNode(post) {
    return window.userLink ? window.userLink(post.authorName, 'author') : el('span', 'author', post.authorName);
  }

  function titleLink(post, href) {
    const link = el('a', 'post-link');
    link.href = href;
    if (post.notice) {
      link.append(el('span', 'badge', '공지'));
    }
    link.append(el('span', 'post-title', post.title));
    return link;
  }

  /** 이 기기에서 이미 읽은 글은 흐리게 하고 "읽음"을 붙인다 (019, D-123) */
  function markRead(li, link, post) {
    if (window.ui.isRead && window.ui.isRead(post.id)) {
      li.classList.add('is-read');
      link.append(el('span', 'read-mark', '읽음'));
    }
  }

  function tagList(post) {
    if (!Array.isArray(post.tags) || post.tags.length === 0) {
      return null;
    }
    const tags = el('ul', 'tag-list');
    post.tags.forEach(t => {
      const tag = el('li', 'tag');
      const a = el('a', null, '#' + t);
      a.href = '/tags/' + encodeURIComponent(t);
      tag.append(a);
      tags.append(tag);
    });
    // 휴대폰에서는 3개까지만 보이고 나머지 수를 "+N"으로 (app.css)
    if (post.tags.length > 3) {
      const more = el('li', 'tag tag-more', '+' + (post.tags.length - 3));
      more.setAttribute('aria-hidden', 'true');
      tags.append(more);
    }
    return tags;
  }

  /** 오른쪽 썸네일. 우리 서버 사진(/files/...)만. 누르면 글로 간다. 사진을 못 불러오면 칸을 치운다 */
  function thumbnail(li, post, href) {
    if (!post.thumbnailUrl || !post.thumbnailUrl.startsWith('/files/')) {
      return;
    }
    const thumbLink = el('a', 'post-item-thumb');
    thumbLink.href = href;
    thumbLink.tabIndex = -1;
    thumbLink.setAttribute('aria-hidden', 'true');
    const img = el('img');
    img.src = post.thumbnailUrl;
    img.alt = '';
    img.loading = 'lazy';
    img.addEventListener('error', () => {
      thumbLink.remove();
      li.classList.remove('has-thumb');
    });
    thumbLink.append(img);
    if (post.thumbnailVideo) {
      thumbLink.append(el('span', 'thumb-play', VIDEO_LABELS[post.thumbnailVideo] || '영상'));
    }
    li.append(thumbLink);
    li.classList.add('has-thumb');
  }

  /** 블로그 공지: 제목과 정보 한 줄 */
  function noticeItem(post, href, opts) {
    const li = el('li', 'post-item');
    const main = el('div', 'post-item-main');
    const link = titleLink(post, href);
    markRead(li, link, post);
    main.append(link);
    const metaLine = el('p', 'post-meta');
    const meta = [opts.blogName, post.categoryName, authorNode(post), formatDate(post.createdAt),
      '조회 ' + post.viewCount, '좋아요 ' + post.likeCount, '댓글 ' + post.commentCount];
    if (window.joinMeta) {
      window.joinMeta(metaLine, meta);
    } else {
      metaLine.textContent = meta.filter(p => typeof p === 'string' && p).join(' · ');
    }
    main.append(metaLine);
    li.append(main);
    thumbnail(li, post, href);
    return li;
  }

  /**
   * @param {object} post 목록 항목
   * @param {string} href 글 주소
   * @param {object} [options] { blogName } 여러 블로그가 섞인 목록에서 어느 블로그 글인지
   */
  function create(post, href, options) {
    const opts = options || {};
    if (post.notice) {
      return noticeItem(post, href, opts);
    }
    const li = el('li', 'post-item post-card');
    const main = el('div', 'post-item-main');

    const head = el('div', 'post-item-head');
    const avatar = el('img', 'avatar small');
    avatar.src = window.ui.avatarSrc(post.authorImageUrl);
    avatar.alt = '';
    avatar.loading = 'lazy';
    head.append(avatar, authorNode(post), el('span', 'post-date', formatDate(post.createdAt)));
    main.append(head);

    const link = titleLink(post, href);
    markRead(li, link, post);
    main.append(link);

    if (post.excerpt) {
      main.append(el('p', 'post-excerpt', post.excerpt));
    }
    const stats = el('p', 'post-stats');
    const where = [opts.blogName, post.categoryName].filter(Boolean).join(' · ');
    if (where) {
      stats.append(el('span', 'post-where', where));
    }
    stats.append(el('span', null, '좋아요 ' + post.likeCount), el('span', null, '댓글 ' + post.commentCount),
      el('span', null, '조회 ' + post.viewCount));
    main.append(stats);
    const tags = tagList(post);
    if (tags) {
      main.append(tags);
    }
    li.append(main);
    thumbnail(li, post, href);
    return li;
  }

  window.postItem = { create: create, formatDate: formatDate, el: el };
})();
