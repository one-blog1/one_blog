// 모든 화면이 쓰는 API 호출 도우미 (research R4, D-62, SEC-10)
// - 상태를 바꾸는 요청에 X-XSRF-TOKEN 헤더를 붙인다
// - 사용자가 직접 한 행동(userAction: true)일 때만 X-User-Activity: 1 을 붙인다
// - 401 TOKEN_EXPIRED 이면 refresh 후 한 번 다시 보낸다
(function () {
  'use strict';

  function readCookie(name) {
    const prefix = name + '=';
    const found = document.cookie.split(';').map(c => c.trim()).find(c => c.startsWith(prefix));
    return found ? decodeURIComponent(found.substring(prefix.length)) : null;
  }

  async function send(method, url, body, userAction) {
    const headers = { 'Accept': 'application/json' };
    const isForm = body instanceof FormData;
    if (body !== undefined && !isForm) {
      headers['Content-Type'] = 'application/json';
    }
    if (method !== 'GET') {
      const csrf = readCookie('XSRF-TOKEN');
      if (csrf) {
        headers['X-XSRF-TOKEN'] = csrf;
      }
    }
    if (userAction) {
      headers['X-User-Activity'] = '1';
    }
    return fetch(url, {
      method: method,
      headers: headers,
      // 파일은 FormData 그대로 보낸다. Content-Type(경계 값 포함)은 브라우저가 붙인다
      body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body)),
      credentials: 'same-origin'
    });
  }

  async function parse(response) {
    if (response.status === 204) {
      return null;
    }
    const text = await response.text();
    if (!text) {
      return null;
    }
    try {
      return JSON.parse(text);
    } catch (e) {
      return null;
    }
  }

  /**
   * @param {string} method
   * @param {string} url
   * @param {object} [options] { body, userAction, redirectOnLogout }
   * @returns {Promise<{ok: boolean, status: number, data: any}>}
   */
  async function request(method, url, options) {
    const opts = options || {};
    let response = await send(method, url, opts.body, opts.userAction);
    let data = await parse(response);

    if (response.status === 401 && data && data.code === 'TOKEN_EXPIRED') {
      const refreshed = await send('POST', '/api/auth/refresh', undefined, false);
      if (refreshed.ok) {
        response = await send(method, url, opts.body, opts.userAction);
        data = await parse(response);
      } else if (opts.redirectOnLogout) {
        window.location.href = '/login.html';
      }
    }
    return { ok: response.ok, status: response.status, data: data };
  }

  window.api = {
    get: (url, options) => request('GET', url, options),
    post: (url, body, options) => request('POST', url, Object.assign({}, options, { body: body })),
    put: (url, body, options) => request('PUT', url, Object.assign({}, options, { body: body })),
    delete: (url, options) => request('DELETE', url, options),
    /** 메서드를 직접 고를 때 */
    request: (method, url, options) => request(method, url, options),
    /** 파일 하나를 multipart로 올린다 (필드 이름 file). */
    upload: (url, file, options) => {
      const form = new FormData();
      form.append('file', file);
      return request('POST', url, Object.assign({}, options, { body: form }));
    }
  };
})();
