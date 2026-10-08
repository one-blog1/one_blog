// 블로그 첫 화면 /blog/{주소} (BLG-01, D-70). 볼 수 있는지는 서버가 판단한다 (SEC-07).
// 볼 수 없으면 서버는 블로그 정보를 보내지 않고, 화면은 안내만 보여준다.
(function () {
  'use strict';

  const NOTICE = {
    BLOG_NOT_FOUND: ['블로그를 찾을 수 없습니다', '주소가 맞는지 확인해 주세요.'],
    LINK_REQUIRED: ['링크가 있어야 볼 수 있는 블로그입니다', '블로그장에게 공유 링크를 받아 들어와 주세요.'],
    PRIVATE_BLOG: ['비공개 블로그입니다', '이 블로그의 멤버만 볼 수 있어요.'],
    MEMBER_SUSPENDED: ['이 블로그에서 정지되었습니다', '']
  };

  function $(id) {
    return document.getElementById(id);
  }

  function slugFromPath() {
    const parts = window.location.pathname.split('/').filter(Boolean);
    return parts.length >= 2 && parts[0] === 'blog' ? decodeURIComponent(parts[1]) : '';
  }

  function showNotice(code, serverMessage) {
    const [title, fallback] = NOTICE[code] || ['블로그를 불러오지 못했습니다', '잠시 후 다시 시도해 주세요.'];
    // 정지 안내는 서버가 기간과 사유를 담아 보낸다 (BLG-13)
    const text = code === 'MEMBER_SUSPENDED' && serverMessage ? serverMessage : fallback;
    $('notice-title').textContent = title;
    $('notice-text').textContent = text;
    $('blog-notice').classList.remove('hidden');
    document.title = title + ' - One Blog';
  }

  function render(blog) {
    const c = window.blogCard;
    document.title = blog.name + ' - One Blog';
    // 대표 이미지가 없으면 블로그 색과 첫 글자로 (목록 카드와 같은 색)
    $('blog-cover').replaceChildren(c.cover(blog, 'blog-hero'));
    $('blog-name').textContent = blog.name;

    const meta = [c.VISIBILITY_LABEL[blog.visibility] || blog.visibility,
      blog.joinPolicy === 'APPROVAL' ? '승인제' : '자유 참여'];
    if (blog.ownerNickname) {
      meta.push('블로그장 ' + blog.ownerNickname);
    }
    meta.push('멤버 ' + blog.memberCount + '명');
    if (blog.myRole) {
      meta.push('나: ' + (c.ROLE_LABEL[blog.myRole] || blog.myRole));
    }
    $('blog-meta').textContent = meta.join(' · ');
    $('blog-description').textContent = blog.description || '';

    const tags = $('blog-tags');
    tags.replaceChildren();
    (blog.tags || []).forEach(t => tags.append(c.el('li', 'tag', '#' + t)));

    // 공유 링크는 일부 공개 블로그의 멤버에게만 온다. 비공개 블로그에는 없다 (BLG-01)
    if (blog.shareUrl) {
      $('share-url').value = window.location.origin + blog.shareUrl;
      $('share-area').classList.remove('hidden');
    }
    $('blog-home').classList.remove('hidden');
    // 참여·신청 관리 화면(blog-join.js)이 이 정보를 이어서 쓴다
    document.dispatchEvent(new CustomEvent('blog:loaded', { detail: blog }));
  }

  async function copyShare() {
    const input = $('share-url');
    try {
      await navigator.clipboard.writeText(input.value);
    } catch (e) {
      input.select();
      document.execCommand('copy');
    }
    $('share-message').textContent = '링크를 복사했어요.';
  }

  document.addEventListener('DOMContentLoaded', async () => {
    const slug = slugFromPath();
    const key = new URLSearchParams(window.location.search).get('key');
    let url = '/api/blogs/' + encodeURIComponent(slug);
    if (key) {
      url += '?key=' + encodeURIComponent(key);
    }
    const result = await window.api.get(url, { userAction: true });
    if (result.ok && result.data) {
      render(result.data);
    } else {
      showNotice(result.data && result.data.code, result.data && result.data.message);
    }
    $('copy-share').addEventListener('click', copyShare);
  });
})();
