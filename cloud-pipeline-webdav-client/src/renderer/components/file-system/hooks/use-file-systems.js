import React, {
  useCallback, useContext,
  useMemo,
  useState,
} from 'react';
import useHotKeys from '../../../common/use-hotkeys';

const NOOP = () => {};

const FileSystemsContext = React.createContext({
  active: '',
  keys: [],
  currentPaths: {},
  registerKey: NOOP,
  unRegisterKey: NOOP,
  setActive: NOOP,
  onPathChanged: NOOP,
});

/**
 * @param {string} currentFileSystemKey
 * @returns {({key: string, identifier: string?, path: string?})[]}
 */
function useOtherFileSystemsPaths(currentFileSystemKey) {
  const {
    currentPaths,
  } = useContext(FileSystemsContext);
  const {
    [currentFileSystemKey]: currentPath,
    ...other
  } = currentPaths || {};
  return Object
    .entries(other)
    .map(([key, info]) => ({
      key,
      ...info,
    }));
}

/**
 * @param {string} currentFileSystemKey
 * @returns {string[]}
 */
function useOtherFileSystemsKeys(currentFileSystemKey) {
  const {
    currentPaths,
  } = useContext(FileSystemsContext);
  const {
    [currentFileSystemKey]: currentPath,
    ...other
  } = currentPaths || {};
  return Object.keys(other);
}

export {
  FileSystemsContext,
  useOtherFileSystemsPaths,
  useOtherFileSystemsKeys,
};

export default function useCreateFileSystems() {
  const [activeKey, setActiveKey] = useState();
  const [keys, setKeys] = useState([]);
  const [currentPaths, setCurrentPaths] = useState({});
  const registerKey = useCallback((aKey) => {
    setKeys((oldKeys = []) => (oldKeys.includes(aKey) ? oldKeys : [...oldKeys, aKey]));
    setCurrentPaths((paths) => ({ ...paths, [aKey]: {} }));
  }, [setKeys, setCurrentPaths]);
  const unRegisterKey = useCallback((aKey) => {
    setKeys((oldKeys = []) => oldKeys.filter((o) => o !== aKey));
    setCurrentPaths(({ [aKey]: _, ...rest }) => ({ ...rest }));
  }, [setKeys, setCurrentPaths]);
  const onReportPathChanged = useCallback(
    (key, identifier, path) => {
      setCurrentPaths((paths) => ({ ...paths, [key]: { identifier, path } }));
    },
    [setCurrentPaths],
  );
  const changeActive = useCallback(() => {
    if (keys.length) {
      const nextIdx = ((keys || []).indexOf(activeKey) + 1) % keys.length;
      setActiveKey(keys[nextIdx]);
    }
  }, [setActiveKey, activeKey, keys]);
  const tabHotKey = useMemo(() => ({ tab: changeActive }), [changeActive]);
  useHotKeys(tabHotKey);
  return useMemo(() => ({
    active: activeKey,
    keys,
    registerKey,
    unRegisterKey,
    setActive: setActiveKey,
    onPathChanged: onReportPathChanged,
    currentPaths,
  }), [
    activeKey,
    keys,
    registerKey,
    unRegisterKey,
    setActiveKey,
    onReportPathChanged,
    currentPaths,
  ]);
}
