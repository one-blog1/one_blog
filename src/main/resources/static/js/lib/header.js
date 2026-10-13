// 상단 바 (USR-03, 4.2 일관성, D-101, D-116): 가운데 검색창과 오른쪽 회원 메뉴. 닉네임 프로필 링크 도우미(userLink, joinMeta)도 여기 있다.
// - 비회원: "로그인" 버튼 하나 (회원가입은 로그인 화면의 링크로)
// - 회원: 알림 종(notifications.js가 맨 앞에 넣음) · 글쓰기 · 닉네임(내 프로필) · 프로필 사진 → 내 정보·설정·로그아웃
//   글쓰기(D-130): 참여 중인 블로그가 하나면 바로 그 블로그 글쓰기, 여럿이면 블로그 목록을 펼쳐 고르게(마지막에 쓴 블로그가 위),
//   없으면 블로그 만들기. 고른 뒤에는 블로그 안 [글쓰기]와 같은 화면(카테고리는 그 화면에서 고름)
// - 관리자: 관리자 화면 · 로그아웃
// 사용자 입력(닉네임)은 textContent로만 넣는다 (SEC-06). 지금 보고 있는 화면의 메뉴는 aria-current로 표시한다 (D-104).
(function () {
  'use strict';

  const DEFAULT_AVATAR = '/images/avatar-default.svg';

  function link(href, text, className) {
    const a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    if (className) {
      a.className = className;
    }
    if (window.location.pathname === href) {
      a.setAttribute('aria-current', 'page');
    }
    return a;
  }

  async function logout() {
    await window.api.post('/api/auth/logout', undefined, { userAction: true });
    window.location.href = '/';
  }

  function logoutButton(className) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = className;
    button.textContent = '로그아웃';
    button.addEventListener('click', logout);
    return button;
  }

  // 프로필 사진 메뉴: 버튼을 누르면 열고, 바깥을 누르거나 Esc·Tab으로 벗어나면 닫는다
  function profileMenu(me) {
    const wrap = document.createElement('div');
    wrap.className = 'profile-menu';

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'avatar-button';
    button.setAttribute('aria-haspopup', 'menu');
    button.setAttribute('aria-expanded', 'false');
    button.setAttribute('aria-label', '내 메뉴 열기');
    const img = document.createElement('img');
    img.className = 'avatar';
    img.alt = '';
    img.src = me.profileImageUrl && me.profileImageUrl.startsWith('/files/') ? me.profileImageUrl : DEFAULT_AVATAR;
    button.append(img);

    const menu = document.createElement('ul');
    menu.className = 'profile-menu-list hidden';
    menu.setAttribute('role', 'menu');
    const items = [link('/account.html', '내 정보'), link('/settings.html', '설정'), logoutButton('link-button')];
    if (window.location.pathname === '/withdraw.html') {
      items[0].setAttribute('aria-current', 'page');
    }
    items.forEach(item => {
      item.setAttribute('role', 'menuitem');
      const li = document.createElement('li');
      li.setAttribute('role', 'none');
      li.append(item);
      menu.append(li);
    });

    function setOpen(open) {
      menu.classList.toggle('hidden', !open);
      button.setAttribute('aria-expanded', String(open));
      button.classList.toggle('open', open);
    }

    button.addEventListener('click', () => {
      const open = menu.classList.contains('hidden');
      setOpen(open);
      if (open) {
        items[0].focus();
      }
    });
    document.addEventListener('click', (event) => {
      if (!wrap.contains(event.target)) {
        setOpen(false);
      }
    });
    wrap.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !menu.classList.contains('hidden')) {
        setOpen(false);
        button.focus();
      } else if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && !menu.classList.contains('hidden')) {
        event.preventDefault();
        const index = items.indexOf(document.activeElement);
        const next = (index + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length;
        items[next].focus();
      }
    });
    wrap.addEventListener('focusout', (event) => {
      if (event.relatedTarget && !wrap.contains(event.relatedTarget)) {
        setOpen(false);
      }
    });

    wrap.append(button, menu);
    return wrap;
  }

  const LAST_WRITE_KEY = 'oneblog.lastWriteBlog';

  function writeUrl(slug) {
    return '/blog/' + encodeURIComponent(slug) + '/write';
  }

  function lastWriteBlog() {
    try {
      return window.localStorage.getItem(LAST_WRITE_KEY) || '';
    } catch (e) {
      return '';
    }
  }

  // 글쓰기 단추 (D-130). 블로그 목록은 처음 누를 때 한 번만 불러온다
  function writeMenu() {
    const wrap = document.createElement('div');
    wrap.className = 'write-menu';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'header-write';
    button.textContent = '글쓰기';
    button.setAttribute('aria-haspopup', 'menu');
    button.setAttribute('aria-expanded', 'false');
    const menu = document.createElement('div');
    menu.className = 'write-menu-list hidden';
    let items = null;

    function setOpen(open) {
      menu.classList.toggle('hidden', !open);
      button.setAttribute('aria-expanded', String(open));
    }

    async function loadItems() {
      const result = await window.api.get('/api/me/blogs');
      if (!result.ok || !result.data) {
        return null;
      }
      const seen = new Set();
      const blogs = [].concat(result.data.owned || [], result.data.joined || [])
        .filter(b => b && b.slug && !seen.has(b.slug) && seen.add(b.slug));
      const last = lastWriteBlog();
      blogs.sort((a, b) => (b.slug === last) - (a.slug === last));
      return blogs;
    }

    button.addEventListener('click', async () => {
      if (!menu.classList.contains('hidden')) {
        setOpen(false);
        return;
      }
      if (items === null) {
        const blogs = await loadItems();
        if (blogs === null) {
          return;
        }
        if (blogs.length === 0) {
          window.location.href = '/blog-new.html';
          return;
        }
        if (blogs.length === 1) {
          window.location.href = writeUrl(blogs[0].slug);
          return;
        }
        const title = document.createElement('p');
        title.className = 'write-menu-title';
        title.textContent = '어느 블로그에 쓸까요?';
        const list = document.createElement('ul');
        list.setAttribute('role', 'menu');
        list.setAttribute('aria-label', '글을 쓸 블로그');
        items = blogs.map(b => {
          const a = document.createElement('a');
          a.href = writeUrl(b.slug);
          a.textContent = b.name; // textContent만 (SEC-06)
          a.setAttribute('role', 'menuitem');
          const li = document.createElement('li');
          li.setAttribute('role', 'none');
          li.append(a);
          list.append(li);
          return a;
        });
        menu.append(title, list);
      }
      if (items.length === 1) {
        window.location.href = items[0].href;
        return;
      }
      setOpen(true);
      items[0].focus();
    });
    document.addEventListener('click', (event) => {
      if (!wrap.contains(event.target)) {
        setOpen(false);
      }
    });
    wrap.addEventListener('keydown', (event) => {
      if (menu.classList.contains('hidden') || !items) {
        return;
      }
      if (event.key === 'Escape') {
        setOpen(false);
        button.focus();
      } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const index = items.indexOf(document.activeElement);
        const next = (index + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length;
        items[next].focus();
      }
    });
    wrap.addEventListener('focusout', (event) => {
      if (event.relatedTarget && !wrap.contains(event.relatedTarget)) {
        setOpen(false);
      }
    });
    wrap.append(button, menu);
    return wrap;
  }

  async function renderHeader() {
    const area = document.getElementById('header-user');
    if (!area) {
      return;
    }
    // 페이지를 연 것은 사용자가 직접 한 행동이다 (D-62)
    const result = await window.api.get('/api/me', { userAction: true });
    area.replaceChildren();

    if (!result.ok || !result.data) {
      area.append(link('/login.html', '로그인', 'header-login'));
      document.dispatchEvent(new CustomEvent('header:guest'));
      return;
    }

    const me = result.data;
    if (me.role === 'ADMIN') {
      // 관리자는 블로그 활동과 프로필이 없다 (D-90)
      area.append(link('/admin.html', '관리자 화면'), logoutButton('link-button header-logout'));
    } else {
      const profilePath = '/users/' + encodeURIComponent(me.nickname);
      const name = link(profilePath, me.nickname, 'nickname');
      if (decodeURIComponent(window.location.pathname) === '/users/' + me.nickname) {
        name.setAttribute('aria-current', 'page');
      }
      area.append(writeMenu(), name, profileMenu(me));
      // 알림 종(011): 일반 회원에게만. 모든 화면에 따로 넣지 않도록 여기서 불러온다
      const script = document.createElement('script');
      script.src = '/js/components/notifications.js';
      document.body.append(script);
    }
    document.dispatchEvent(new CustomEvent('header:user', { detail: me }));
  }

  // 닉네임을 그 회원의 프로필 링크로 (글·댓글·멤버 목록 어디서나, SOC-03). 탈퇴한 회원은 링크 없이 글자만
  const NO_PROFILE = ['탈퇴한 회원', '탈퇴한 계정'];
  window.userLink = function (nickname, className) {
    const name = nickname || '';
    if (!name || NO_PROFILE.includes(name)) {
      const span = document.createElement('span');
      span.textContent = name;
      return span;
    }
    const a = document.createElement('a');
    a.className = 'user-name' + (className ? ' ' + className : '');
    a.href = '/users/' + encodeURIComponent(name);
    a.textContent = name;
    return a;
  };

  /** 텍스트와 노드를 " · "로 이어 붙인다 (글 정보 줄 등). */
  window.joinMeta = function (target, parts) {
    target.replaceChildren();
    parts.filter(p => p !== null && p !== undefined && p !== ''
      && !(p instanceof Node && p.textContent.trim() === '')).forEach((part, i) => {
      if (i > 0) {
        target.append(document.createTextNode(' · '));
      }
      target.append(part instanceof Node ? part : document.createTextNode(String(part)));
    });
  };

  // 상단 가운데 검색창 (BRD-08, D-116): 모든 화면에서 블로그·글·태그·닉네임을 찾는다
  function renderSearch() {
    const header = document.querySelector('.site-header');
    if (!header || header.querySelector('.header-search')) {
      return;
    }
    const form = document.createElement('form');
    form.className = 'header-search';
    form.action = '/search';
    form.method = 'get';
    form.setAttribute('role', 'search');
    const input = document.createElement('input');
    input.type = 'search';
    input.name = 'q';
    input.maxLength = 20;
    input.placeholder = '블로그, 글, 태그, 닉네임 검색';
    input.setAttribute('aria-label', '검색어');
    if (window.location.pathname === '/search') {
      input.value = new URLSearchParams(window.location.search).get('q') || '';
    }
    const button = window.icons ? window.icons.button('search', '검색', 'tip-end') : document.createElement('button');
    button.type = 'submit';
    form.append(input, button);
    header.insertBefore(form, document.getElementById('header-user'));
  }

  // 모든 화면 아래의 약관·처리방침 링크 (4.6 "모든 화면 아래에 개인정보 처리방침 링크"). 화면마다 넣지 않고 여기서 한 번에 붙인다
  function renderFooter() {
    if (document.querySelector('.site-footer')) {
      return;
    }
    const footer = document.createElement('footer');
    footer.className = 'site-footer';
    const nav = document.createElement('nav');
    nav.setAttribute('aria-label', '서비스 정책');
    const privacy = link('/privacy.html', '개인정보 처리방침', 'site-footer-strong');
    const terms = link('/terms.html', '이용약관');
    nav.append(terms, privacy);
    const copy = document.createElement('p');
    copy.textContent = '© One Blog';
    footer.append(nav, copy);
    document.body.append(footer);
  }

  document.addEventListener('DOMContentLoaded', () => {
    renderSearch();
    renderHeader();
    renderFooter();
  });
})();
