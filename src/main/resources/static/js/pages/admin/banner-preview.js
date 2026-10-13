// 관리자 배너 미리보기와 사진 위치 정하기 (ADM-08, D-134).
// 메인 배너와 같은 모양(PC 1080×380, 휴대폰 358×220)을 줄여 보여 주고, 사진을 끌거나 가로·세로 막대로
// 사진에서 보일 자리(CSS object-position %)를 정한다. 값은 textContent·style 속성으로만 넣는다 (SEC-06).
(function () {
  'use strict';

  const el = window.ui.el;
  const SIZES = { pc: { w: 1080, h: 380, name: 'PC' }, phone: { w: 358, h: 220, name: '휴대폰' } };

  function clamp(v) {
    return Math.max(0, Math.min(100, Math.round(v)));
  }

  /** 미리보기 한 칸(PC 또는 휴대폰). 실제 크기로 그린 뒤 칸 폭에 맞게 줄인다. */
  function stage(size, kindClass) {
    const box = el('div', 'banner-preview-box banner-preview-' + kindClass);
    const stageEl = el('div', 'banner-preview-stage');
    stageEl.style.width = size.w + 'px';
    stageEl.style.height = size.h + 'px';
    const frame = el('div', 'banner-frame' + (kindClass === 'phone' ? ' is-phone' : ''));
    const link = el('div', 'banner-link');
    const img = el('img', 'banner-image');
    img.alt = '';
    img.draggable = false;
    const empty = el('span', 'banner-image banner-empty banner-preview-empty', '사진을 골라 주세요');
    const caption = el('span', 'banner-caption');
    const label = el('span', 'banner-label');
    const title = el('strong', 'banner-title');
    const sub = el('span', 'banner-sub');
    caption.append(label, title, sub);
    link.append(empty, img, caption);
    frame.append(link);
    stageEl.append(frame);
    box.append(stageEl);
    const fit = () => {
      const scale = box.clientWidth / size.w || 0;
      stageEl.style.transform = 'scale(' + scale + ')';
      box.style.height = Math.round(size.h * scale) + 'px';
    };
    return { box, img, empty, label, title, sub, size, fit, getScale: () => box.clientWidth / size.w };
  }

  /**
   * @param {function(number, number)} onChange 위치가 바뀔 때
   * @returns {{ element, setImage, setText, setPosition, getPosition }}
   */
  function create(onChange) {
    const state = { x: 50, y: 50, url: null };
    const root = el('div', 'banner-preview');
    const stages = [stage(SIZES.pc, 'pc'), stage(SIZES.phone, 'phone')];
    const row = el('div', 'banner-preview-row');
    stages.forEach(s => {
      const col = el('figure', 'banner-preview-col');
      col.append(s.box, el('figcaption', 'hint', s.size.name + ' 화면'));
      row.append(col);
    });

    const hint = el('p', 'hint', '사진을 끌어서 보일 자리를 정하세요. 휴대폰은 위아래가 덜 잘리고 양옆이 더 잘려요.');
    const sliders = el('div', 'banner-preview-sliders');
    const rangeX = slider('banner-pos-x', '가로 위치 (왼쪽 ↔ 오른쪽)');
    const rangeY = slider('banner-pos-y', '세로 위치 (위 ↔ 아래)');
    const center = el('button', 'secondary inline-button', '가운데로');
    center.type = 'button';
    center.addEventListener('click', () => setPosition(50, 50, true));
    sliders.append(rangeX.box, rangeY.box, center);
    root.append(row, hint, sliders);

    function slider(id, text) {
      const box = el('div', 'banner-preview-slider');
      const label = el('label', null, text);
      label.htmlFor = id;
      const input = el('input');
      input.type = 'range';
      input.id = id;
      input.min = '0';
      input.max = '100';
      input.step = '1';
      const value = el('span', 'hint');
      box.append(label, input, value);
      input.addEventListener('input', () => {
        if (id === 'banner-pos-x') {
          setPosition(Number(input.value), state.y, true);
        } else {
          setPosition(state.x, Number(input.value), true);
        }
      });
      return { box, input, value };
    }

    function render() {
      stages.forEach(s => {
        s.img.style.objectPosition = state.x + '% ' + state.y + '%';
        s.img.classList.toggle('hidden', !state.url);
        s.empty.classList.toggle('hidden', !!state.url);
      });
      rangeX.input.value = String(state.x);
      rangeY.input.value = String(state.y);
      rangeX.value.textContent = state.x + '%';
      rangeY.value.textContent = state.y + '%';
      sliders.classList.toggle('hidden', !state.url);
      hint.classList.toggle('hidden', !state.url);
    }

    function setPosition(x, y, fromUser) {
      state.x = clamp(x);
      state.y = clamp(y);
      render();
      if (fromUser && onChange) {
        onChange(state.x, state.y);
      }
    }

    // 끌기: 사진을 오른쪽으로 끌면 왼쪽 부분이 더 보인다(가로 % 감소). 넘치는 길이만큼 움직인다
    stages.forEach(s => {
      let start = null;
      s.img.addEventListener('pointerdown', (e) => {
        if (!state.url || !s.img.naturalWidth) {
          return;
        }
        e.preventDefault();
        s.img.setPointerCapture(e.pointerId);
        const cover = Math.max(s.size.w / s.img.naturalWidth, s.size.h / s.img.naturalHeight);
        start = {
          px: e.clientX, py: e.clientY, x: state.x, y: state.y,
          overX: s.img.naturalWidth * cover - s.size.w, overY: s.img.naturalHeight * cover - s.size.h,
          scale: s.getScale()
        };
      });
      s.img.addEventListener('pointermove', (e) => {
        if (!start) {
          return;
        }
        const dx = (e.clientX - start.px) / start.scale;
        const dy = (e.clientY - start.py) / start.scale;
        const x = start.overX > 1 ? start.x - dx / start.overX * 100 : start.x;
        const y = start.overY > 1 ? start.y - dy / start.overY * 100 : start.y;
        setPosition(x, y, true);
      });
      const end = () => { start = null; };
      s.img.addEventListener('pointerup', end);
      s.img.addEventListener('pointercancel', end);
    });

    if (window.ResizeObserver) {
      new ResizeObserver(() => stages.forEach(s => s.fit())).observe(row);
    }
    window.requestAnimationFrame(() => stages.forEach(s => s.fit()));

    render();
    return {
      element: root,
      setImage(url) {
        state.url = typeof url === 'string' && url.startsWith('/files/') ? url : null;
        stages.forEach(s => {
          if (state.url) {
            s.img.src = state.url;
          } else {
            s.img.removeAttribute('src');
          }
        });
        render();
      },
      setText(kind, kindLabel, title, caption) {
        stages.forEach(s => {
          s.label.textContent = kindLabel || '';
          s.label.className = 'banner-label' + (kind === 'AD' ? ' ad' : '');
          s.title.textContent = title || '제목을 적어 주세요';
          s.sub.textContent = caption || '';
          s.sub.classList.toggle('hidden', !caption);
        });
      },
      setPosition(x, y) {
        setPosition(x, y, false);
      },
      getPosition() {
        return { x: state.x, y: state.y };
      }
    };
  }

  window.bannerPreview = { create: create };
})();
