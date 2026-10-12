// 아이콘 단추 (D-108): 닫기·설정·삭제처럼 자주 쓰는 동작은 글자 대신 아이콘으로 보이고,
// 마우스를 올리거나 키보드로 가면 이름이 뜬다(data-tip). 화면 읽기 프로그램에는 aria-label로 이름을 알린다.
// 쓰는 법: <button data-icon="trash">삭제</button> → 글자는 이름이 되고 아이콘으로 바뀐다.
// 스크립트로 만든 단추는 window.icons.apply(button)을 부른다.
(function () {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';
  // 선으로 그린 24×24 아이콘 (채우지 않고 currentColor 선)
  const PATHS = {
    close: ['M6 6l12 12', 'M18 6L6 18'],
    settings: ['M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
      'M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z'],
    trash: ['M4 7h16', 'M9 7V4h6v3', 'M6 7l1 13h10l1-13', 'M10 11v6', 'M14 11v6'],
    checks: ['M2 12.5l4 4 8-9', 'M11 15.5l1 1 9-9'],
    back: ['M15 18l-6-6 6-6'],
    edit: ['M4 20h4L19 9l-4-4L4 16v4z', 'M13.5 6.5l4 4'],
    reply: ['M9 7L4 12l5 5', 'M4 12h10a6 6 0 0 1 6 6v1'],
    flag: ['M5 21V4', 'M5 4h12l-2 4.5L17 13H5'],
    link: ['M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1', 'M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1'],
    more: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
    search: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'M20 20l-4-4'],
    exit: ['M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4', 'M10 16l-4-4 4-4', 'M6 12h10']
  };

  function svg(name) {
    const icon = document.createElementNS(NS, 'svg');
    icon.setAttribute('viewBox', '0 0 24 24');
    icon.setAttribute('aria-hidden', 'true');
    icon.setAttribute('class', 'icon');
    PATHS[name].forEach(d => {
      const path = document.createElementNS(NS, 'path');
      path.setAttribute('d', d);
      icon.append(path);
    });
    return icon;
  }

  function apply(el) {
    const name = el.dataset.icon;
    if (!PATHS[name] || el.dataset.iconReady === 'true') {
      return el;
    }
    const label = el.getAttribute('aria-label') || el.textContent.trim();
    el.setAttribute('aria-label', label);
    el.dataset.tip = label;
    el.replaceChildren(svg(name));
    el.classList.add('icon-button');
    el.dataset.iconReady = 'true';
    return el;
  }

  /** 아이콘 단추를 새로 만든다. tag는 button(기본) 또는 a. */
  function button(name, label, className, tag) {
    const el = document.createElement(tag || 'button');
    if (!tag || tag === 'button') {
      el.type = 'button';
    }
    if (className) {
      el.className = className;
    }
    el.dataset.icon = name;
    el.textContent = label;
    return apply(el);
  }

  function decorate(root) {
    (root || document).querySelectorAll('[data-icon]').forEach(apply);
  }

  window.icons = { apply: apply, button: button, decorate: decorate };
  document.addEventListener('DOMContentLoaded', () => decorate(document));
})();
