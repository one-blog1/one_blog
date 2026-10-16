// 글 공유 창 (031, BRD-07, 6.2). 위에 글 주소 칸과 복사, 아래에 서비스 아이콘을 가로로 둔다.
// window.share.open({ title, url, scope, message })
//  - scope: 서버가 정한 공유 범위(PUBLIC·LINK·MEMBERS). 어떤 아이콘을 보일지 정한다 (ShareScope)
//  - message: 복사·실패 안내를 넣을 글 화면의 칸
// X·텔레그램·페이스북은 각 서비스의 공유 주소로 가는 새 창 링크라 키·외부 스크립트가 없고 팝업 차단에도 걸리지 않는다 (R1, R2).
// 기기 공유는 브라우저 공유 창(navigator.share)이 있을 때만 (R3). 글자는 모두 textContent (SEC-06).
(function () {
  'use strict';

  const NOTE = {
    LINK: '링크가 있어야 볼 수 있는 블로그라 SNS에는 올릴 수 없어요.',
    MEMBERS: '비공개 블로그라 멤버만 열 수 있어요.'
  };

  const encode = encodeURIComponent;
  const isPublic = (scope) => scope === 'PUBLIC';

  // 아이콘 줄 (순서대로). 카카오톡은 키가 준비되면 여기에 하나 더한다 (R7).
  // image: 각 서비스가 브랜드 자료로 주는 공식 아이콘 파일(/images/share/…, 텔레그램·페이스북). 없으면 short 글자를 원 안에 넣는다.
  // X는 공식 브랜드 툴킷에 새 로고 파일이 없어 글자로 둔다(밝은 화면은 검정 원, 어두운 화면은 흰 원: X 가이드의 배경 규칙).
  const TARGETS = [
    {
      key: 'x', label: 'X', short: 'X', image: null, show: isPublic,
      href: (p) => 'https://x.com/intent/tweet?text=' + encode(p.title) + '&url=' + encode(p.url)
    },
    {
      key: 'telegram', label: '텔레그램', short: '텔', image: '/images/share/telegram.svg', show: isPublic,
      href: (p) => 'https://t.me/share/url?url=' + encode(p.url) + '&text=' + encode(p.title)
    },
    {
      key: 'facebook', label: '페이스북', short: '페', image: '/images/share/facebook.png', show: isPublic,
      href: (p) => 'https://www.facebook.com/sharer/sharer.php?u=' + encode(p.url)
    },
    {
      key: 'device', label: '기기 공유', icon: 'share',
      show: (scope) => (scope === 'PUBLIC' || scope === 'LINK') && typeof navigator.share === 'function',
      run: deviceShare
    }
  ];

  function say(p, text) {
    if (p.message) {
      p.message.textContent = text;
    }
  }

  /** 클립보드에 넣는다. 브라우저가 막으면(권한 거부, 인앱 브라우저, http 등) false. */
  async function writeClipboard(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (e) {
      return false;
    }
  }

  async function deviceShare(p) {
    try {
      await navigator.share({ title: p.title, url: p.url });
    } catch (e) {
      // 공유 창에서 취소한 것은 실패가 아니다
      if (!e || e.name !== 'AbortError') {
        say(p, '공유하지 못했어요. 링크 복사를 이용해 주세요.');
      }
    }
  }

  function linkRow(p, close, failNote) {
    const row = document.createElement('div');
    row.className = 'share-link';
    const input = document.createElement('input');
    input.type = 'text';
    input.readOnly = true;
    input.value = p.url;
    input.dataset.share = 'url';
    input.setAttribute('aria-label', '글 주소');
    input.addEventListener('focus', () => input.select());
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'primary';
    button.dataset.share = 'copy';
    button.textContent = '복사';
    button.addEventListener('click', async () => {
      if (await writeClipboard(p.url)) {
        close();
        say(p, '링크를 복사했어요.');
        return;
      }
      // 자동 복사가 막히면 창을 닫지 않고, 주소를 선택해 두어 바로 직접 복사하게 한다
      input.focus();
      input.select();
      failNote.textContent = '자동 복사가 안 돼요. 선택된 주소를 직접 복사해 주세요.';
    });
    row.append(input, button);
    return row;
  }

  function badge(target) {
    const circle = document.createElement('span');
    circle.className = 'share-badge';
    if (target.icon) {
      circle.append(window.icons.svg(target.icon));
    } else if (target.image) {
      const img = document.createElement('img');
      img.src = target.image;
      img.alt = '';
      img.width = 52;
      img.height = 52;
      circle.classList.add('has-image');
      circle.append(img);
    } else {
      circle.textContent = target.short;
    }
    return circle;
  }

  function item(target, p, close) {
    const el = document.createElement(target.href ? 'a' : 'button');
    el.className = 'share-item';
    el.dataset.share = target.key;
    if (target.href) {
      el.href = target.href(p);
      el.target = '_blank';
      // 새 창이 이 화면을 건드리지 못하게, 글 주소(일부 공개면 key)가 상대 사이트로 넘어가지 않게
      el.rel = 'noopener noreferrer';
      el.addEventListener('click', () => close());
    } else {
      el.type = 'button';
      el.addEventListener('click', () => {
        close();
        target.run(p);
      });
    }
    const name = document.createElement('span');
    name.className = 'share-name';
    name.textContent = target.label;
    el.append(badge(target), name);
    return el;
  }

  function open(options) {
    const p = Object.assign({}, options);
    const modal = window.modal.open({ title: '공유' });
    modal.body.parentElement.classList.add('modal-narrow');
    const failNote = document.createElement('p');
    failNote.className = 'message error share-copy-fail';
    failNote.setAttribute('role', 'status');
    modal.body.append(linkRow(p, modal.close, failNote), failNote);

    const shown = TARGETS.filter(t => t.show(p.scope));
    if (shown.length) {
      const list = document.createElement('ul');
      list.className = 'share-icons';
      shown.forEach(t => {
        const li = document.createElement('li');
        li.append(item(t, p, modal.close));
        list.append(li);
      });
      modal.body.append(list);
    }
    if (NOTE[p.scope]) {
      const note = document.createElement('p');
      note.className = 'share-note';
      note.textContent = NOTE[p.scope];
      modal.body.append(note);
    }
    return modal;
  }

  window.share = { open: open };
})();
