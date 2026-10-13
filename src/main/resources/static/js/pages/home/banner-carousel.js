// 메인 배너 넘기기 (BRD-12, 6.8, D-132): 큰 사진을 5초마다 한 장씩 넘긴다. 자리와 불러오기는 main-banner.js.
// 자동으로 움직이는 내용이라 멈출 수 있어야 한다(WCAG 2.2.2): 마우스·키보드가 배너 안에 있으면 멈추고,
// [멈춤]·[이전]·[다음]·점 단추를 두고, 기기의 "움직임 줄이기"가 켜져 있으면 처음부터 멈춘 채로 둔다.
// 서버가 준 제목·작은 글씨는 textContent로만 넣고, 사진은 우리 서버(/files/) 주소만 쓴다 (SEC-06).
(function () {
  'use strict';

  const INTERVAL_MS = 5000;
  const el = window.ui.el;

  function safeImage(url) {
    return typeof url === 'string' && url.startsWith('/files/') ? url : null;
  }

  /** 서버가 검사한 링크라도 화면에서 한 번 더: /로 시작하는 우리 주소이거나 http(s) 주소만. */
  function safeLink(url) {
    if (typeof url !== 'string') {
      return null;
    }
    if (url.startsWith('/') && !url.startsWith('//')) {
      return url;
    }
    return /^https?:\/\//i.test(url) ? url : null;
  }

  function iconButton(name, label, className) {
    const b = el('button', 'banner-btn ' + (className || ''));
    b.type = 'button';
    b.setAttribute('aria-label', label);
    b.append(window.icons.svg(name));
    return b;
  }

  function slideElement(item, index, total) {
    const slide = el('div', 'banner-slide');
    slide.setAttribute('role', 'group');
    slide.setAttribute('aria-roledescription', '배너');
    slide.setAttribute('aria-label', (index + 1) + ' / ' + total);
    const href = safeLink(item.linkUrl);
    const link = el(href ? 'a' : 'div', 'banner-link');
    if (href) {
      link.href = href;
      if (item.external) {
        link.target = '_blank';
        link.rel = item.kind === 'AD' ? 'noopener noreferrer sponsored' : 'noopener noreferrer';
      }
    }
    const image = safeImage(item.imageUrl);
    if (image) {
      const img = el('img', 'banner-image');
      img.src = image;
      img.alt = '';
      img.loading = index === 0 ? 'eager' : 'lazy';
      img.decoding = 'async';
      link.append(img);
    } else if (item.blogSlug && window.blogCard) {
      // 대표 이미지가 없는 블로그: 그 블로그 색과 첫 글자 (blog-card와 같은 표지)
      link.append(window.blogCard.cover({ slug: item.blogSlug, name: item.title }, 'banner-image banner-tile'));
    }
    const caption = el('span', 'banner-caption');
    caption.append(el('span', 'banner-label' + (item.kind === 'AD' ? ' ad' : ''), item.label || ''));
    // 종류: 자동(인기 글·새 블로그·인기 블로그)과 관리자(광고·공지·이벤트). 광고만 초록 표시 (D-133)
    caption.append(el('strong', 'banner-title', item.title || ''));
    if (item.caption) {
      caption.append(el('span', 'banner-sub', item.caption));
    }
    link.append(caption);
    slide.append(link);
    return slide;
  }

  /**
   * root 안에 배너를 그린다. items가 비면 root를 숨긴다.
   * @returns {{ setActive: function(boolean) }} 화면에서 숨길 때 setActive(false)로 넘김을 멈춘다
   */
  function mount(root, items) {
    const list = Array.isArray(items) ? items.filter(i => i && i.title) : [];
    root.replaceChildren();
    root.classList.toggle('hidden', list.length === 0);
    if (list.length === 0) {
      return { setActive: () => {} };
    }
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const state = { index: 0, playing: !reduce, hover: false, focus: false, active: true, timer: null };

    const frame = el('div', 'banner-frame');
    const slides = list.map((item, i) => slideElement(item, i, list.length));
    frame.append(...slides);

    const controls = el('div', 'banner-controls');
    const dots = el('div', 'banner-dots');
    dots.setAttribute('role', 'group');
    dots.setAttribute('aria-label', '배너 고르기');
    const dotButtons = list.map((item, i) => {
      const dot = el('button', 'banner-dot');
      dot.type = 'button';
      dot.setAttribute('aria-label', (i + 1) + '번째 배너: ' + (item.title || ''));
      dot.addEventListener('click', () => go(i));
      dots.append(dot);
      return dot;
    });
    const position = el('span', 'banner-position');
    position.setAttribute('aria-hidden', 'true');
    const play = el('button', 'banner-btn banner-play');
    play.type = 'button';
    play.addEventListener('click', () => {
      state.playing = !state.playing;
      render();
      schedule();
    });
    const prev = iconButton('back', '이전 배너');
    prev.addEventListener('click', () => go(state.index - 1));
    const next = iconButton('next', '다음 배너');
    next.addEventListener('click', () => go(state.index + 1));
    controls.append(dots, position, play, prev, next);
    // 한 장이면 넘길 것이 없다
    controls.classList.toggle('hidden', list.length < 2);
    root.append(frame, controls);

    function render() {
      slides.forEach((slide, i) => {
        const on = i === state.index;
        slide.classList.toggle('active', on);
        slide.setAttribute('aria-hidden', String(!on));
        slide.inert = !on;
      });
      dotButtons.forEach((dot, i) => dot.setAttribute('aria-pressed', String(i === state.index)));
      position.textContent = (state.index + 1) + ' / ' + list.length;
      play.setAttribute('aria-label', state.playing ? '자동 넘김 멈추기' : '자동 넘김 다시 시작');
      play.replaceChildren(window.icons.svg(state.playing ? 'pause' : 'play'));
    }

    function go(i) {
      state.index = (i + list.length) % list.length;
      render();
      schedule(); // 직접 넘기면 5초를 다시 센다
    }

    function schedule() {
      window.clearTimeout(state.timer);
      state.timer = null;
      if (list.length < 2 || !state.playing || state.hover || state.focus || !state.active || document.hidden) {
        return;
      }
      state.timer = window.setTimeout(() => go(state.index + 1), INTERVAL_MS);
    }

    root.addEventListener('mouseenter', () => { state.hover = true; schedule(); });
    root.addEventListener('mouseleave', () => { state.hover = false; schedule(); });
    root.addEventListener('focusin', () => { state.focus = true; schedule(); });
    root.addEventListener('focusout', (e) => {
      if (!root.contains(e.relatedTarget)) {
        state.focus = false;
        schedule();
      }
    });
    document.addEventListener('visibilitychange', schedule);

    render();
    schedule();
    return {
      setActive(on) {
        state.active = on;
        schedule();
      }
    };
  }

  window.banner = { mount: mount };
})();
