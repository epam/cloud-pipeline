import { useEffect } from 'react';

function codeMatches(code, event) {
  if (!event || !code) {
    return false;
  }
  if (typeof code === 'string') {
    return code.toLowerCase() === (event.code || '').toLowerCase();
  }
  return false;
}

function generateHotKeyHandler(hotKeys) {
  return function handler(event) {
    if (event.target !== document.body) {
      return;
    }
    const codeHandler = Object
      .entries(hotKeys || {})
      .map(([key, fn]) => ({ key, fn }))
      .find(({ key }) => codeMatches(key, event));
    if (codeHandler && typeof codeHandler.fn === 'function') {
      event.stopPropagation();
      event.preventDefault();
      codeHandler.fn(event);
    }
  };
}

/**
 * @typedef {{[code: RegExp|string]: function}} HotKeyConfiguration
 */

/**
 * @param {HotKeyConfiguration} hotKeys
 * @param {boolean} [active=true]
 */
export default function useHotKeys(hotKeys, active = true) {
  useEffect(() => {
    if (active) {
      const handler = generateHotKeyHandler(hotKeys);
      window.addEventListener('keydown', handler);
      return () => {
        window.removeEventListener('keydown', handler);
      };
    }
    return () => {};
  }, [active, hotKeys]);
}
