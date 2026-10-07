// 신고 창 (SOC-06): 사유를 골라 신고한다. 같은 대상은 2주에 한 번 (6.6).
// window.report.open({ targetType, targetId, nickname, blogSlug }) — 다른 화면(블로그·프로필)도 쓴다.
// 글 화면에서는 글·댓글 신고 버튼을 켠다 (본인 글·댓글, 비회원, 관리자는 제외).
(function () {
  'use strict';

  const REASONS = [['SPAM', '스팸·광고'], ['ABUSE', '욕설·괴롭힘'], ['OBSCENE', '음란·선정적인 내용'],
    ['PRIVACY', '개인정보 노출'], ['ILLEGAL', '불법 정보'], ['ETC', '기타']];
  let overlay = null;

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = text;
    }
    return node;
  }

  function keyParam() {
    return new URLSearchParams(window.location.search).get('key');
  }

  function close() {
    if (overlay) {
      overlay.remove();
      overlay = null;
    }
  }

  function open(target) {
    close();
    overlay = el('div', 'modal-overlay');
    const box = el('div', 'modal card');
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-modal', 'true');
    box.setAttribute('aria-label', '신고하기');
    box.append(el('h2', null, '신고하기'));
    const fieldset = el('fieldset');
    fieldset.append(el('legend', null, '사유'));
    REASONS.forEach(([value, label], i) => {
      const wrap = el('label', 'checkbox');
      const input = el('input');
      input.type = 'radio';
      input.name = 'report-reason';
      input.value = value;
      input.checked = i === 0;
      wrap.append(input, document.createTextNode(' ' + label));
      fieldset.append(wrap);
    });
    const detailLabel = el('label', null, '자세한 내용 (선택, 500자까지)');
    const detail = el('textarea');
    detail.rows = 3;
    detail.maxLength = 500;
    detailLabel.append(detail);
    const message = el('p', 'message error');
    message.setAttribute('role', 'alert');
    const send = el('button', 'primary inline-button', '신고');
    send.type = 'button';
    const cancel = el('button', 'secondary', '취소');
    cancel.type = 'button';
    cancel.addEventListener('click', close);
    send.addEventListener('click', async () => {
      const reason = box.querySelector('input[name="report-reason"]:checked');
      const body = Object.assign({ key: keyParam(), reason: reason ? reason.value : null, detail: detail.value }, target);
      send.disabled = true;
      const result = await window.api.post('/api/reports', body, { userAction: true });
      send.disabled = false;
      if (result.status === 401) {
        window.location.href = '/login.html';
        return;
      }
      if (!result.ok) {
        message.textContent = (result.data && result.data.message) || '신고하지 못했어요.';
        return;
      }
      box.replaceChildren(el('p', 'message ok', '신고를 접수했어요. 처리 결과는 알림으로 알려 드려요.'));
      window.setTimeout(close, 1500);
    });
    const row = el('div', 'row');
    row.append(send, cancel);
    box.append(fieldset, detailLabel, message, row);
    overlay.append(box);
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        close();
      }
    });
    document.body.append(overlay);
  }

  window.report = { open: open };

  let canReport = false;

  function enableCommentButtons() {
    if (!canReport) {
      return;
    }
    document.querySelectorAll('.report-comment').forEach(button => {
      if (button.dataset.mine === 'true' || button.dataset.bound === 'true') {
        return;
      }
      button.dataset.bound = 'true';
      button.classList.remove('hidden');
      button.addEventListener('click', () => open({ targetType: 'COMMENT', targetId: Number(button.dataset.commentId) }));
    });
  }

  document.addEventListener('post:loaded', (event) => {
    const post = event.detail;
    canReport = !!post.canComment;
    const button = document.getElementById('report-button');
    if (button && canReport && !post.canEdit) {
      button.classList.remove('hidden');
      button.addEventListener('click', () => open({ targetType: 'POST', targetId: post.id }));
    }
    enableCommentButtons();
  });
  document.addEventListener('comments:rendered', enableCommentButtons);
})();
