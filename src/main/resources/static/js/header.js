// 상단 메뉴 (USR-03, 4.2 일관성, D-101)
// - 비회원: "로그인" 버튼 하나 (회원가입은 로그인 화면의 링크로)
// - 회원: 알림 종(notifications.js가 맨 앞에 넣음) · 닉네임(내 프로필) · 프로필 사진 → 내 정보·설정·로그아웃
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
      area.append(name, profileMenu(me));
      // 알림 종(011): 일반 회원에게만. 모든 화면에 따로 넣지 않도록 여기서 불러온다
      const script = document.createElement('script');
      script.src = '/js/notifications.js';
      document.body.append(script);
    }
    document.dispatchEvent(new CustomEvent('header:user', { detail: me }));
  }

  document.addEventListener('DOMContentLoaded', renderHeader);
})();
