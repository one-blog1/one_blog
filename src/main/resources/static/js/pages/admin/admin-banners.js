// 관리자 "배너" 탭 (ADM-08, 6.8, D-132): 메인 배너의 광고를 넣고 고치고 지우고 순서를 바꾸며,
// 인기 글(자동)의 개수·기간을 정하고 지금 나갈 인기 글을 미리 본다. 조치는 서버가 활동 기록에 남긴다 (ADM-06).
// 서버가 준 값은 textContent로만 넣고, 사진은 /files/ 주소만 쓴다 (SEC-06).
(function () {
  'use strict';

  const el = window.ui.el;
  const DAYS = [[1, '오늘(1일)'], [7, '최근 7일'], [30, '최근 30일']];

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

  // ---- 인기 글 (자동) ----
  function popularSection(data) {
    const section = el('section', 'banner-admin-section');
    section.append(el('h2', null, '인기 글 (자동)'));
    section.append(el('p', 'hint', '최근 글 중 좋아요가 있고 썸네일(첫 사진·영상)이 있는 글을 좋아요 많은 순으로 넣어요. 사진과 제목은 글에서 가져와요.'));
    const form = el('form', 'row banner-settings');
    const count = el('select');
    count.append(new Option('0개 (인기 글 끄기)', '0'));
    for (let i = 1; i <= 5; i++) {
      count.append(new Option(i + '개', String(i)));
    }
    count.value = String(data.settings.popularCount);
    const days = el('select');
    DAYS.forEach(([v, label]) => days.append(new Option(label, String(v))));
    days.value = String(data.settings.popularDays);
    const countLabel = el('label', 'inline', '개수');
    countLabel.append(count);
    const daysLabel = el('label', 'inline', '기간');
    daysLabel.append(days);
    const save = el('button', 'secondary inline-button', '저장');
    save.type = 'submit';
    form.append(countLabel, daysLabel, save);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const r = await window.api.put('/api/admin/banner-settings',
        { popularCount: Number(count.value), popularDays: Number(days.value) }, { userAction: true });
      await ctx.reload();
      ctx.message(r.ok ? '인기 글 설정을 저장했습니다.' : ((r.data && r.data.message) || '저장하지 못했습니다.'));
    });
    section.append(form);

    const preview = data.popularPreview || [];
    if (preview.length === 0) {
      section.append(el('p', 'hint', data.settings.popularCount === 0
        ? '인기 글이 0개라 배너에는 광고만 나가요.' : '지금 조건에 맞는 인기 글이 없어요.'));
    } else {
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
      section.append(el('p', 'hint', '지금 나갈 인기 글'), list);
    }
    return section;
  }

  // ---- 광고 ----
  function adForm() {
    const form = el('form', 'banner-admin-form');
    form.noValidate = true;
    const heading = el('h3', null, editing ? '"' + editing.title + '" 광고 고치기' : '광고 추가');
    const preview = el('div', 'banner-admin-image');
    const showImage = (url) => preview.replaceChildren(url ? img(url, 'banner-admin-photo') : el('span', 'hint', '사진을 골라 주세요'));
    showImage(editing ? editing.imageUrl : null);
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
        setError('image', '');
      } else {
        setError('image', (r.data && r.data.message) || '사진을 올리지 못했습니다.');
      }
    });
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

    const submit = el('button', 'primary inline-button', editing ? '수정 저장' : '광고 추가');
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

    form.append(heading, preview,
      field('banner-image', '사진', file, 'jpg·png·gif·webp, 3MB까지. 가로로 긴 사진(1080×380 이상)이 좋아요. 가운데를 기준으로 잘려 보여요.'),
      field('banner-title', '제목', title),
      field('banner-caption', '작은 글씨 (광고주 등, 비워도 돼요)', caption),
      field('banner-linkUrl', '링크', link, '바깥 주소는 새 창으로 열려요.'),
      visibleLabel, actions);
    // 사진 칸 오류 이름을 image로 맞춘다 (서버 필드는 imageFileId)
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors();
      const body = { title: title.value.trim(), caption: caption.value.trim(), linkUrl: link.value.trim(),
        imageFileId: imageFileId, visible: visible.checked };
      const r = editing
        ? await window.api.put('/api/admin/banners/' + encodeURIComponent(editing.id), body, { userAction: true })
        : await window.api.post('/api/admin/banners', body, { userAction: true });
      if (r.ok) {
        const done = editing ? '광고를 고쳤습니다.' : '광고를 추가했습니다.';
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
    const ads = data.ads || [];
    if (ads.length === 0) {
      return el('p', 'hint', '아직 광고가 없어요.');
    }
    let shown = 0;
    return helpers.table(['사진', '제목', '링크', '노출', '순서', ''], ads.map((ad, i) => {
      const live = ad.visible && shown < data.maxVisibleAds;
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
      const state = el('span', null, ad.visible ? (live ? '보임' : '대기 (' + data.maxVisibleAds + '개 넘음)') : '숨김');
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
          title: ad.title, caption: ad.caption, linkUrl: ad.linkUrl, imageFileId: ad.imageFileId, visible: !ad.visible
        }, { userAction: true });
        await ctx.reload();
        ctx.message(r.ok ? (ad.visible ? '광고를 숨겼습니다.' : '광고를 보이게 했습니다.') : '바꾸지 못했습니다.');
      }), helpers.button('수정', () => {
        editing = ad;
        render(data, helpers);
        const form = ctx.content.querySelector('.banner-admin-form');
        if (form) {
          form.scrollIntoView({ block: 'start' });
        }
      }), helpers.button('삭제', async () => {
        if (!await window.dialog.confirm('"' + ad.title + '" 광고를 삭제할까요?')) {
          return;
        }
        const r = await window.api.delete('/api/admin/banners/' + encodeURIComponent(ad.id), { userAction: true });
        if (editing && editing.id === ad.id) {
          editing = null;
        }
        await ctx.reload();
        ctx.message(r.ok ? '광고를 삭제했습니다.' : '삭제하지 못했습니다.');
      }, 'link-button danger'));
      return [img(ad.imageUrl, 'banner-admin-thumb'), titleCell, linkCell, state, order, actions];
    }));
  }

  function render(data, helpers) {
    const box = el('div', 'banner-admin');
    box.append(popularSection(data));
    const ads = el('section', 'banner-admin-section');
    ads.append(el('h2', null, '광고'));
    ads.append(el('p', 'hint', '노출을 켠 광고 중 위에서부터 ' + data.maxVisibleAds + '개가 인기 글과 번갈아 나가요. 배너에는 늘 "광고" 표시가 붙어요.'));
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
