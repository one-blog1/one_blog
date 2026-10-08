// 화면 가운데 창 (D-112). 전체 화면이 아니라 적당한 크기로 띄우고, 닫기 아이콘·창 밖 누르기·Esc로 닫는다.
// window.modal.open({ title, wide }) → { body, setTitle, close }. 내용은 body에 직접 넣는다(textContent로, SEC-06).
(function () {
  'use strict';

  function open(options) {
    const opts = options || {};
    const previous = document.activeElement;
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    const box = document.createElement('div');
    box.className = 'modal card' + (opts.wide ? ' modal-wide' : '');
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-modal', 'true');
    const head = document.createElement('div');
    head.className = 'modal-head';
    const title = document.createElement('h2');
    title.id = 'modal-title-' + Date.now();
    title.textContent = opts.title || '';
    box.setAttribute('aria-labelledby', title.id);
    const close = window.icons.button('close', '닫기', 'tip-end');
    head.append(title, close);
    const body = document.createElement('div');
    body.className = 'modal-body';
    box.append(head, body);
    overlay.append(box);
    document.body.append(overlay);
    document.body.classList.add('modal-open');

    function onKey(event) {
      if (event.key === 'Escape') {
        done();
      }
    }

    function done() {
      document.removeEventListener('keydown', onKey);
      overlay.remove();
      document.body.classList.remove('modal-open');
      if (previous && typeof previous.focus === 'function') {
        previous.focus();
      }
      if (typeof opts.onClose === 'function') {
        opts.onClose();
      }
    }

    close.addEventListener('click', done);
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay) {
        done();
      }
    });
    document.addEventListener('keydown', onKey);
    close.focus();
    return {
      body: body,
      setTitle: (text) => { title.textContent = text; },
      close: done
    };
  }

  window.modal = { open: open };
})();

// 확인·입력·알림 창 (D-112). 브라우저 기본 창(confirm·prompt·alert)은 막혀 있으면 눌러도 아무 반응이 없어서,
// 같은 모양의 가운데 창으로 묻는다. 모두 Promise를 돌려준다.
(function () {
  'use strict';

  const el = window.ui.el;

  function ask(message, options, withInput) {
    const opts = options || {};
    return new Promise((resolve) => {
      let answered = false;
      const m = window.modal.open({
        title: opts.title || (withInput ? '입력' : '확인'),
        onClose: () => {
          if (!answered) {
            resolve(withInput ? null : false);
          }
        }
      });
      const text = el('p', 'dialog-message', message);
      m.body.append(text);
      let input = null;
      const error = el('p', 'message error');
      if (withInput) {
        input = el('input');
        input.type = 'text';
        input.maxLength = opts.maxLength || 300;
        input.value = opts.defaultValue || '';
        input.setAttribute('aria-label', message);
        m.body.append(input, error);
      }
      const row = el('div', 'row dialog-actions');
      const finish = (value) => {
        answered = true;
        resolve(value);
        m.close();
      };
      if (!opts.alertOnly) {
        const cancel = el('button', 'secondary', opts.cancelLabel || '취소');
        cancel.type = 'button';
        cancel.addEventListener('click', () => finish(withInput ? null : false));
        row.append(cancel);
      }
      const ok = el('button', 'primary inline-button' + (opts.danger ? ' danger' : ''), opts.okLabel || '확인');
      ok.type = 'button';
      ok.addEventListener('click', () => {
        if (withInput) {
          const value = input.value.trim();
          if (opts.required && !value) {
            error.textContent = '내용을 적어 주세요.';
            input.focus();
            return;
          }
          finish(value);
          return;
        }
        finish(true);
      });
      row.append(ok);
      m.body.append(row);
      if (input) {
        input.focus();
        input.addEventListener('keydown', (event) => {
          if (event.key === 'Enter') {
            ok.click();
          }
        });
      } else {
        ok.focus();
      }
    });
  }

  window.dialog = {
    confirm: (message, options) => ask(message, options, false),
    prompt: (message, options) => ask(message, Object.assign({ required: false }, options), true),
    alert: (message, options) => ask(message, Object.assign({ alertOnly: true, title: '알림' }, options), false)
  };
})();
