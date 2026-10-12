// 화면 설정 (D-115): 밝게·어둡게·기기 설정 따르기, 글자 크기. 이 기기에만 저장한다(localStorage).
// 화면이 그려지기 전에 적용하려고 <head>에서 불러온다. 저장소를 못 쓰면 기본값(밝게·보통)으로 둔다.
(function () {
  'use strict';

  const THEMES = ['light', 'dark', 'system'];
  const FONTS = ['normal', 'large', 'xlarge'];
  const root = document.documentElement;
  const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function read(key, allowed, fallback) {
    try {
      const value = window.localStorage.getItem(key);
      return allowed.includes(value) ? value : fallback;
    } catch (e) {
      return fallback;
    }
  }

  function write(key, value) {
    try {
      window.localStorage.setItem(key, value);
    } catch (e) {
      // 저장하지 못해도 지금 화면에는 적용한다
    }
  }

  function apply() {
    const theme = read('ob-theme', THEMES, 'light');
    const dark = theme === 'dark' || (theme === 'system' && media && media.matches);
    root.dataset.theme = dark ? 'dark' : 'light';
    root.dataset.font = read('ob-font', FONTS, 'normal');
  }

  apply();
  if (media && media.addEventListener) {
    media.addEventListener('change', apply);
  }

  window.theme = {
    get: () => ({ theme: read('ob-theme', THEMES, 'light'), font: read('ob-font', FONTS, 'normal') }),
    setTheme: (value) => { if (THEMES.includes(value)) { write('ob-theme', value); apply(); } },
    setFont: (value) => { if (FONTS.includes(value)) { write('ob-font', value); apply(); } }
  };
})();
