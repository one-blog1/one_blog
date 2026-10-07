// 상단 메뉴: 로그인 상태에 따라 닉네임·로그아웃 또는 로그인·회원가입을 보여준다 (USR-03, 4.2 일관성)
// 사용자 입력(닉네임)은 textContent로만 넣는다 (SEC-06)
(function () {
  'use strict';

  function link(href, text) {
    const a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    return a;
  }

  async function renderHeader() {
    const area = document.getElementById('header-user');
    if (!area) {
      return;
    }
    // 페이지를 연 것은 사용자가 직접 한 행동이다 (D-62)
    const result = await window.api.get('/api/me', { userAction: true });
    area.replaceChildren();

    if (result.ok && result.data) {
      const name = document.createElement('span');
      name.className = 'nickname';
      name.textContent = result.data.nickname;

      const logout = document.createElement('button');
      logout.type = 'button';
      logout.className = 'link-button';
      logout.textContent = '로그아웃';
      logout.addEventListener('click', async () => {
        await window.api.post('/api/auth/logout', undefined, { userAction: true });
        window.location.href = '/';
      });

      // 블로그 활동은 일반 회원만 (관리자 제외, D-90)
      if (result.data.role !== 'ADMIN') {
        area.append(link('/my-blogs.html', '내 블로그'), link('/blog-new.html', '블로그 만들기'));
      }
      area.append(name, logout);
    } else {
      area.append(link('/login.html', '로그인'), link('/signup.html', '회원가입'));
    }
  }

  document.addEventListener('DOMContentLoaded', renderHeader);
})();
