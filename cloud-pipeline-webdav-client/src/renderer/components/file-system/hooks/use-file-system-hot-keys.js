import { useFileSystemActive } from './use-file-system';
import useHotKeys from '../../../common/use-hotkeys';

/**
 * @param {HotKeyConfiguration} hotKeys
 */
export default function useFileSystemHotKeys(hotKeys) {
  const active = useFileSystemActive();
  return useHotKeys(hotKeys, active);
}
