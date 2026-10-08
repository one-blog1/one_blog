// 글 상세 (BRD-01). 주소: /blog/{주소}/posts/{번호}?key=
// 본문(contentHtml)만 서버가 거른 HTML이라 innerHTML을 쓰고, 나머지는 모두 textContent (SEC-06).
(function () {
  'use strict';

  const NOTICE = {
    POST_NOT_FOUND: ['글을 찾을 수 없습니다', '지워졌거나 없는 글이에요.'],
    BLOG_NOT_FOUND: ['블로그를 찾을 수 없습니다', '주소가 맞는지 확인해 주세요.'],
    LINK_REQUIRED: ['링크가 있어야 볼 수 있는 블로그입니다', '블로그장에게 공유 링크를 받아 들어와 주세요.'],
    PRIVATE_BLOG: ['비공개 블로그입니다', '이 블로그의 멤버만 볼 수 있어요.'],
    SUSPENDED: ['이 블로그에서 정지된 상태입니다', '정지 기간이 끝나면 다시 들어올 수 있어요.']
  };

  function $(id) {
    return document.getElementById(id);
  }

  function parsePath() {
    const parts = window.location.pathname.split('/').filter(Boolean);
    return { slug: decodeURIComponent(parts[1] || ''), id: parts[3] || '' };
  }

  function keyQuery() {
    const key = new URLSearchParams(window.location.search).get('key');
    return key ? '?key=' + encodeURIComponent(key) : '';
  }

  function formatDateTime(iso) {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('ko-KR');
  }

  function showNotice(data) {
    const code = data && data.code;
    const [title, text] = NOTICE[code] || ['글을 불러오지 못했습니다', (data && data.message) || '잠시 후 다시 시도해 주세요.'];
    $('notice-title').textContent = title;
    $('notice-text').textContent = code === 'SUSPENDED' && data.message ? data.message : text;
    $('post-notice').classList.remove('hidden');
  }

  function render(post) {
    const blogUrl = '/blog/' + encodeURIComponent(post.blogSlug) + keyQuery();
    document.title = post.title + ' - ' + post.blogName;
    $('blog-link').textContent = post.blogName;
    $('blog-link').href = blogUrl;
    $('category-name').textContent = post.categoryName ? '› ' + post.categoryName : '';
    $('post-title').textContent = (post.notice ? '[공지] ' : '') + post.title;
    // 관리자라서 보이는 내용이면 알려 준다 (D-106)
    if (post.adminViewReason) {
      $('admin-view').textContent = post.adminViewReason + ' 관리자에게만 보여요. 연 기록은 관리자 활동 기록에 남아요.';
      $('admin-view').classList.remove('hidden');
    }
    const meta = [post.authorName, formatDateTime(post.createdAt)];
    if (post.updatedAt && post.updatedAt !== post.createdAt) {
      meta.push('수정 ' + formatDateTime(post.updatedAt));
    }
    meta.push('조회 ' + post.viewCount);
    $('post-meta').textContent = meta.join(' · ');
    $('post-body').innerHTML = post.contentHtml;

    const tags = $('post-tags');
    tags.replaceChildren();
    (post.tags || []).forEach(t => {
      const li = document.createElement('li');
      li.className = 'tag';
      const a = document.createElement('a');
      a.href = '/tags/' + encodeURIComponent(t);
      a.textContent = '#' + t;
      li.append(a);
      tags.append(li);
    });

    if (post.canEdit) {
      $('edit-link').href = '/blog/' + encodeURIComponent(post.blogSlug) + '/posts/' + post.id + '/edit' + keyQuery();
      $('edit-link').classList.remove('hidden');
    }
    if (post.canDelete) {
      $('delete-button').classList.remove('hidden');
      $('delete-button').addEventListener('click', () => remove(post, blogUrl));
    }
    $('share-button').addEventListener('click', () => copyLink());
    $('post').classList.remove('hidden');
    // 댓글·좋아요·신고(006, 013)가 이 정보를 이어서 쓴다
    document.dispatchEvent(new CustomEvent('post:loaded', { detail: post }));
  }

  async function copyLink() {
    // 공유 링크에는 일부 공개 블로그의 key가 그대로 들어간다 (BLG-01)
    const url = window.location.origin + window.location.pathname + keyQuery();
    try {
      await navigator.clipboard.writeText(url);
      $('post-message').textContent = '링크를 복사했어요.';
    } catch (e) {
      $('post-message').textContent = url;
    }
  }

  async function remove(post, blogUrl) {
    if (!await window.dialog.confirm('글을 정말 삭제하시겠습니까?')) { // 6.3 글 삭제 시 확인
      return;
    }
    const result = await window.api.delete('/api/posts/' + encodeURIComponent(post.id), { userAction: true });
    if (result.ok) {
      window.location.href = blogUrl;
      return;
    }
    $('post-message').textContent = (result.data && result.data.message) || '삭제하지 못했어요.';
  }

  document.addEventListener('DOMContentLoaded', async () => {
    const path = parsePath();
    const result = await window.api.get('/api/posts/' + encodeURIComponent(path.id) + keyQuery(), { userAction: true });
    if (result.ok && result.data) {
      render(result.data);
    } else {
      showNotice(result.data);
    }
  });
})();
