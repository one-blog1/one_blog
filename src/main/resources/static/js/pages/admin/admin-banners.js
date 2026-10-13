// 관리자 "배너" 탭 (ADM-08, 6.8, D-132, D-133): 메인 배너에 관리자가 넣는 광고·공지·이벤트를 넣고 고치고 지우고 순서를 바꾸며,
// 자동으로 넣는 인기 글·새 블로그·인기 블로그의 개수·기간을 정하고 지금 나갈 것을 미리 본다. 조치는 서버가 활동 기록에 남긴다 (ADM-06).
// 서버가 준 값은 textContent로만 넣고, 사진은 /files/ 주소만 쓴다 (SEC-06).
(function () {
  'use strict';

  const el = window.ui.el;
  const DAYS = [[1, '오늘(1일)'], [7, '최근 7일'], [30, '최근 30일']];
  const KINDS = [['AD', '광고'], ['NOTICE', '공지'], ['EVENT', '이벤트']];
  // 자동 종류: 설정 이름, 화면 이름, 설명
  const AUTO = [
    ['popular', '인기 글', '썸네일(첫 사진·영상)이 있는 글을 좋아요 많은 순으로. 사진과 제목은 글에서 가져와요.'],
    ['newBlog', '새 블로그', '기간 안에 만든 공개 블로그, 최신순. 대표 이미지가 없으면 블로그 색과 첫 글자로 보여요.'],
    ['popularBlog', '인기 블로그', '기간 안에 새 멤버가 많이 들어온 공개 블로그. 새 블로그로 나온 블로그는 빠져요.']
  ];

  let ctx = null; // { content, message, reload }
  let editing = null; // 고치는 광고 (없으면 새 광고)
  let imageFileId = null;

  function img(url, className) {
    if (typeof url !== 'string' || !url.startsWith('/files/')) {
      return el('span', 'hint', '사진 없음');
    }
    const image = el('img', className);
    image.src = url;
    image.alt = '';
    image.loading = 'lazy';
    return image;
  }

  function field(id, labelText, input, hint) {
    const box = el('div', 'banner-admin-field');
    const label = el('label', null, labelText);
    label.htmlFor = id;
    input.id = id;
    box.append(label, input);
    if (hint) {
      box.append(el('p', 'hint', hint));
    }
    const error = el('p', 'message error');
    error.dataset.errorFor = 'banner-' + id.replace('banner-', '');
    error.setAttribute('role', 'alert');
    box.append(error);
    return box;
  }

  function setError(name, text) {
    const node = ctx.content.querySelector('[data-error-for="banner-' + name + '"]');
    if (node) {
      node.textContent = text || '';
    }
  }

  function clearErrors() {
    ctx.content.querySelectorAll('[data-error-for^="banner-"]').forEach(n => { n.textContent = ''; });
  }

  // ---- 자동으로 넣는 것: 인기 글·새 블로그·인기 블로그 ----
  function autoSection(data) {
    const section = el('section', 'banner-admin-section');
    section.append(el('h2', null, '자동으로 넣는 것'));
    section.append(el('p', 'hint', '종류마다 개수와 기간을 고르세요. 0개면 그 종류는 넣지 않아요. 조건에 맞는 것이 적으면 고른 개수보다 적게 나가요.'));
    const form = el('form', 'banner-auto-form');
    const selects = {};
    AUTO.forEach(([key, name, help]) => {
      const row = el('div', 'banner-auto-row');
      row.append(el('strong', 'banner-auto-name', name));
      const count = el('select');
      count.setAttribute('aria-label', name + ' 개수');
      for (let i = 0; i <= 5; i++) {
        count.append(new Option(i === 0 ? '0개 (끄기)' : i + '개', String(i)));
      }
      count.value = String(data.settings[key].count);
      const days = el('select');
      days.setAttribute('aria-label', name + ' 기간');
      DAYS.forEach(([v, label]) => days.append(new Option(label, String(v))));
      days.value = String(data.settings[key].days);
      selects[key] = { count: count, days: days };
      row.append(count, days);
      const preview = (data.autoPreview && data.autoPreview[key]) || [];
      const wanted = data.settings[key].count;
      row.append(el('span', 'hint banner-auto-help', help));
      row.append(previewList(preview, wanted));
      form.append(row);
    });
    const save = el('button', 'secondary inline-button', '저장');
    save.type = 'submit';
    form.append(save);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const body = {};
      AUTO.forEach(([key]) => {
        body[key] = { count: Number(selects[key].count.value), days: Number(selects[key].days.value) };
      });
      const r = await window.api.put('/api/admin/banner-settings', body, { userAction: true });
      await ctx.reload();
      ctx.message(r.ok ? '자동 배너 설정을 저장했습니다.' : ((r.data && r.data.message) || '저장하지 못했습니다.'));
    });
    section.append(form);
    return section;
  }

  /** 지금 나갈 자동 배너. 고른 개수보다 적으면 그 이유를 알려 준다. */
  function previewList(preview, wanted) {
    const box = el('div', 'banner-auto-preview');
    if (wanted === 0) {
      box.append(el('p', 'hint', '넣지 않아요.'));
      return box;
    }
    if (preview.length < wanted) {
      box.append(el('p', 'hint', wanted + '개를 골랐지만 지금 조건에 맞는 것이 ' + preview.length + '개라 '
        + preview.length + '개만 나가요. 기간을 늘리면 더 나올 수 있어요.'));
    }
    if (preview.length > 0) {
      const list = el('ul', 'banner-admin-preview');
      preview.forEach(p => {
        const li = el('li');
        const a = el('a');
        a.href = p.linkUrl;
        a.target = '_blank';
        a.rel = 'noopener';
        a.append(img(p.imageUrl, 'banner-admin-thumb'));
        const text = el('span');
        text.append(el('strong', null, p.title), el('span', 'hint', p.caption || ''));
        a.append(text);
        li.append(a);
        list.append(li);
      });
      box.append(list);
    }
    return box;
  }

  // ---- 관리자가 넣는 것: 광고·공지·이벤트 ----
  function adForm() {
    const form = el('form', 'banner-admin-form');
    form.noValidate = true;
    const heading = el('h3', null, editing ? '"' + editing.title + '" 고치기' : '배너 추가');
    // 실제 배너와 같은 모양의 미리보기. 사진을 끌거나 막대로 보일 자리를 정한다 (D-134)
    const preview = window.bannerPreview.create();
    const showImage = (url) => preview.setImage(url);
    showImage(editing ? editing.imageUrl : null);
    const pos = (v) => (Number.isFinite(v) ? v : 50);
    preview.setPosition(editing ? pos(editing.imagePosX) : 50, editing ? pos(editing.imagePosY) : 50);
    imageFileId = editing ? editing.imageFileId : null;

    const file = el('input');
    file.type = 'file';
    file.accept = 'image/jpeg,image/png,image/gif,image/webp';
    file.addEventListener('change', async () => {
      if (!file.files || !file.files[0]) {
        return;
      }
      setError('image', '올리는 중…');
      const r = await window.api.upload('/api/admin/banners/image', file.files[0], { userAction: true });
      if (r.ok && r.data) {
        imageFileId = r.data.fileId;
        showImage(r.data.url);
        preview.setPosition(50, 50); // 새 사진은 가운데부터
        setError('image', '');
      } else {
        setError('image', (r.data && r.data.message) || '사진을 올리지 못했습니다.');
      }
    });
    const kind = el('select');
    KINDS.forEach(([v, label]) => kind.append(new Option(label, v)));
    kind.value = editing ? editing.kind : 'AD';
    const title = el('input');
    title.type = 'text';
    title.maxLength = 100;
    title.value = editing ? editing.title : '';
    const caption = el('input');
    caption.type = 'text';
    caption.maxLength = 100;
    caption.value = editing && editing.caption ? editing.caption : '';
    const link = el('input');
    link.type = 'text';
    link.maxLength = 500;
    link.placeholder = 'https://… 또는 /blog/…';
    link.value = editing ? editing.linkUrl : '';
    const visible = el('input');
    visible.type = 'checkbox';
    visible.checked = editing ? editing.visible : true;
    const visibleLabel = el('label', 'checkbox');
    visibleLabel.append(visible, document.createTextNode(' 메인에 보이기'));
    const label = () => (KINDS.find(k => k[0] === kind.value) || KINDS[0])[1];
    const refreshText = () => preview.setText(kind.value, label(), title.value.trim(), caption.value.trim());
    [kind, title, caption].forEach(input => input.addEventListener('input', refreshText));
    kind.addEventListener('change', refreshText);
    refreshText();

    const submit = el('button', 'primary inline-button', editing ? '수정 저장' : '배너 추가');
    submit.type = 'submit';
    const actions = el('div', 'row');
    actions.append(submit);
    if (editing) {
      const cancel = el('button', 'secondary inline-button', '취소');
      cancel.type = 'button';
      cancel.addEventListener('click', () => {
        editing = null;
        ctx.reload();
      });
      actions.append(cancel);
    }

    form.append(heading, preview.element,
      field('banner-kind', '종류', kind, '배너에 "광고"·"공지"·"이벤트" 표시가 붙어요.'),
      field('banner-image', '사진', file, 'jpg·png·gif·webp, 3MB까지. 가로로 긴 사진(1080×380 이상)이 좋아요. 가운데를 기준으로 잘려 보여요.'),
      field('banner-title', '제목', title),
      field('banner-caption', '작은 글씨 (광고주·기간 등, 비워도 돼요)', caption),
      field('banner-linkUrl', '링크', link, '바깥 주소는 새 창으로 열려요.'),
      visibleLabel, actions);
    // 사진 칸 오류 이름을 image로 맞춘다 (서버 필드는 imageFileId)
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors();
      const position = preview.getPosition();
      const body = { kind: kind.value, title: title.value.trim(), caption: caption.value.trim(), linkUrl: link.value.trim(),
        imageFileId: imageFileId, visible: visible.checked, imagePosX: position.x, imagePosY: position.y };
      const r = editing
        ? await window.api.put('/api/admin/banners/' + encodeURIComponent(editing.id), body, { userAction: true })
        : await window.api.post('/api/admin/banners', body, { userAction: true });
      if (r.ok) {
        const done = editing ? '배너를 고쳤습니다.' : '배너를 추가했습니다.';
        editing = null;
        await ctx.reload();
        ctx.message(done);
        return;
      }
      const errors = (r.data && r.data.fieldErrors) || [];
      errors.forEach(f => setError(f.field === 'imageFileId' ? 'image' : f.field, f.message));
      ctx.message(errors.length ? '입력값을 확인해 주세요.' : ((r.data && r.data.message) || '저장하지 못했습니다.'));
    });
    return form;
  }

  function adTable(data, helpers) {
    const ads = data.banners || [];
    if (ads.length === 0) {
      return el('p', 'hint', '아직 넣은 배너가 없어요.');
    }
    let shown = 0;
    return helpers.table(['종류', '사진', '제목', '링크', '노출', '순서', ''], ads.map((ad, i) => {
      const live = ad.visible && shown < data.maxVisibleBanners;
      if (ad.visible) {
        shown++;
      }
      const titleCell = el('span', 'banner-admin-title');
      titleCell.append(el('strong', null, ad.title));
      if (ad.caption) {
        titleCell.append(el('span', 'hint', ad.caption));
      }
      const linkCell = el('a', 'goto-link', ad.linkUrl);
      linkCell.href = ad.linkUrl;
      linkCell.target = '_blank';
      linkCell.rel = 'noopener noreferrer';
      const state = el('span', null, ad.visible ? (live ? '보임' : '대기 (' + data.maxVisibleBanners + '개 넘음)') : '숨김');
      const order = el('span', 'banner-admin-order');
      const move = (direction) => async () => {
        const r = await window.api.post('/api/admin/banners/' + encodeURIComponent(ad.id) + '/move',
          { direction: direction }, { userAction: true });
        await ctx.reload();
        if (!r.ok) {
          ctx.message('순서를 바꾸지 못했습니다.');
        }
      };
      const up = helpers.button('위로', move('up'));
      up.disabled = i === 0;
      const down = helpers.button('아래로', move('down'));
      down.disabled = i === ads.length - 1;
      order.append(up, down);
      const actions = el('span');
      actions.append(helpers.button(ad.visible ? '숨기기' : '보이기', async () => {
        const r = await window.api.put('/api/admin/banners/' + encodeURIComponent(ad.id), {
          kind: ad.kind, title: ad.title, imagePosX: ad.imagePosX, imagePosY: ad.imagePosY, caption: ad.caption, linkUrl: ad.linkUrl, imageFileId: ad.imageFileId, visible: !ad.visible
        }, { userAction: true });
        await ctx.reload();
        ctx.message(r.ok ? (ad.visible ? '배너를 숨겼습니다.' : '배너를 보이게 했습니다.') : '바꾸지 못했습니다.');
      }), helpers.button('수정', () => {
        editing = ad;
        render(data, helpers);
        const form = ctx.content.querySelector('.banner-admin-form');
        if (form) {
          form.scrollIntoView({ block: 'start' });
        }
      }), helpers.button('삭제', async () => {
        if (!await window.dialog.confirm('"' + ad.title + '" 배너를 삭제할까요?')) {
          return;
        }
        const r = await window.api.delete('/api/admin/banners/' + encodeURIComponent(ad.id), { userAction: true });
        if (editing && editing.id === ad.id) {
          editing = null;
        }
        await ctx.reload();
        ctx.message(r.ok ? '배너를 삭제했습니다.' : '삭제하지 못했습니다.');
      }, 'link-button danger'));
      return [ad.kindLabel || ad.kind, img(ad.imageUrl, 'banner-admin-thumb'), titleCell, linkCell, state, order, actions];
    }));
  }

  function render(data, helpers) {
    const box = el('div', 'banner-admin');
    box.append(autoSection(data));
    const ads = el('section', 'banner-admin-section');
    ads.append(el('h2', null, '관리자가 넣는 것 (광고·공지·이벤트)'));
    ads.append(el('p', 'hint', '노출을 켠 배너 중 위에서부터 ' + data.maxVisibleBanners + '개가 나가요. 배너는 인기 글 → 관리자 배너 → 새 블로그 → 인기 블로그 순서로 하나씩 번갈아 놓여요.'));
    ads.append(adTable(data, helpers), adForm());
    box.append(ads);
    ctx.content.replaceChildren(box);
  }

  /** admin.js가 배너 탭을 열 때 부른다. helpers: { table, button, message, content, onUnauthorized } */
  async function load(helpers) {
    ctx = {
      content: helpers.content,
      message: helpers.message,
      reload: () => load(helpers)
    };
    const r = await window.api.get('/api/admin/banners', { userAction: true });
    if (r.status === 401 || r.status === 403) {
      helpers.onUnauthorized();
      return;
    }
    if (!r.ok || !r.data) {
      helpers.message('불러오지 못했습니다.');
      return;
    }
    render(r.data, helpers);
  }

  function reset() {
    editing = null;
    imageFileId = null;
  }

  window.adminBanners = { load: load, reset: reset };
})();
