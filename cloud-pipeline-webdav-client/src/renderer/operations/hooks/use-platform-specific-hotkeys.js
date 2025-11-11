import { useEffect, useState } from 'react';
import getHotKeysConfiguration from '../get-hotkeys-configuration';

function usePlatformSpecificHotKeys() {
  const [hotKeys, setHotKeys] = useState({});
  useEffect(() => {
    getHotKeysConfiguration()
      .then(setHotKeys)
      .catch(() => {});
  }, [setHotKeys]);
  return hotKeys;
}

/**
 * @param {string} operationType
 * @returns {{code: string, name: string}}
 */
export function usePlatformSpecificHotKey(operationType) {
  const config = usePlatformSpecificHotKeys();
  const hotkey = config[operationType];
  if (!hotkey) {
    return undefined;
  }
  if (typeof hotkey === 'string') {
    return {
      name: hotkey.slice(0, 1).toUpperCase().concat(hotkey.slice(1).toLowerCase()),
      code: hotkey,
    };
  }
  return hotkey;
}

/**
 * @param {string} operationType
 * @returns {string}
 */
export function usePlatformSpecificHotKeyCode(operationType) {
  const hotKey = usePlatformSpecificHotKey(operationType);
  return hotKey?.code;
}
