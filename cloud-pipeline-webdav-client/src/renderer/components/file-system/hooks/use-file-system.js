import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useState,
} from 'react';
import ipcResponse from '../../../common/ipc-response';
import dispatchAbortable from '../../../common/dispatch-abortable';
import { useSimpleUniqueKey } from '../../../common/simple-unique-key';
import { FileSystemsContext, useOtherFileSystemsPaths } from './use-file-systems';
import ipcEvent from '../../../common/ipc-event';

const Actions = {
  loading: 'loading',
  loaded: 'loaded',
  initialized: 'initialized',
  error: 'error',
};

function init() {
  return {
    pending: false,
    error: undefined,
  };
}

function reduce(state, action) {
  switch (action.type) {
    case Actions.loading:
      return { ...state, pending: true, error: undefined };
    case Actions.initialized: {
      const newState = {
        ...state,
        pending: false,
        error: undefined,
      };
      if (state.identifier !== action.identifier) {
        newState.identifier = action.identifier;
        newState.directory = action.directory;
      }
      newState.available = action.available;
      console.log('initialized', action);
      newState.restricted = !!action.restricted;
      return newState;
    }
    case Actions.loaded: {
      if (state.identifier !== action.identifier) {
        return {
          ...state,
          restricted: !!action.restricted,
          pending: false,
          error: undefined,
          identifier: action.identifier,
          directory: action.directory,
        };
      }
      return state;
    }
    case Actions.error:
      return { ...state, pending: false, error: action.error };
    default:
      return state;
  }
}

async function initialize(dispatch, getAborted, options = {}) {
  const {
    index = 0,
    identifier,
  } = options;
  try {
    dispatch({ type: Actions.loading });
    const info = await ipcResponse('getAdapter', { index, identifier });
    if (getAborted()) {
      return;
    }
    const available = await ipcResponse('getAvailableAdapters');
    console.log('~~~available', available);
    if (getAborted()) {
      return;
    }
    dispatch({ type: Actions.initialized, ...info, available });
  } catch (error) {
    dispatch({ type: Actions.error, error: error.message });
  }
}

async function change(index, identifier, dispatch) {
  try {
    dispatch({ type: Actions.loading, identifier });
    const info = await ipcResponse('setAdapter', index, identifier);
    dispatch({ type: Actions.loaded, ...info });
  } catch (error) {
    dispatch({ type: Actions.error, error: error.message });
  }
}

/**
 * @typedef {Object} FileSystemState
 * @property {string} identifier
 * @property {string} directory
 * @property {function} onChangeAdapter,
 * @property {boolean} pending
 * @property {string} error
 * @property {boolean} initialized
 */

const NOOP = () => {};

const FileSystemContext = React.createContext({
  directory: '',
  pending: true,
  available: [],
  identifier: '',
  onChangeAdapter: NOOP,
  initialized: false,
  restricted: false,
  active: true,
  key: '',
  setActive: NOOP,
});

function useAvailableAdapters() {
  const {
    available = [],
  } = useContext(FileSystemContext);
  return available;
}

function useFileSystemIdentifier() {
  const {
    identifier,
  } = useContext(FileSystemContext);
  return identifier;
}

function useFileSystemInitialized() {
  const {
    initialized,
  } = useContext(FileSystemContext);
  return !!initialized;
}

function useFileSystemRestricted() {
  const {
    restricted,
  } = useContext(FileSystemContext);
  return !!restricted;
}

/**
 * @returns {function}
 */
function useChangeAdapter() {
  const {
    onChangeAdapter = NOOP,
  } = useContext(FileSystemContext);
  return onChangeAdapter;
}

function useFileSystemSetActive() {
  const { setActive } = useContext(FileSystemContext);
  return setActive;
}

function useFileSystemActive() {
  const { active } = useContext(FileSystemContext);
  return active;
}

function useFileSystemKey() {
  const { key } = useContext(FileSystemContext);
  return key;
}

function useFileSystemPathChangeCallback() {
  const { key } = useContext(FileSystemContext);
  const { onPathChanged } = useContext(FileSystemsContext);
  return useCallback(
    (identifier, path) => onPathChanged(key, identifier, path),
    [onPathChanged, key],
  );
}

function useOtherFileSystemPath() {
  const { key } = useContext(FileSystemContext);
  const {
    path,
    identifier,
  } = useOtherFileSystemsPaths(key).pop() || {};
  return useMemo(
    () => (path && identifier ? ({ path, identifier }) : undefined),
    [path, identifier],
  );
}

export {
  FileSystemContext,
  useAvailableAdapters,
  useChangeAdapter,
  useFileSystemIdentifier,
  useFileSystemSetActive,
  useFileSystemInitialized,
  useFileSystemRestricted,
  useFileSystemKey,
  useFileSystemActive,
  useFileSystemPathChangeCallback,
  useOtherFileSystemPath,
};

/**
 * @param {number} index
 * @returns {FileSystemState}
 */
export default function useCreateFileSystem(index = 0) {
  const {
    active,
    setActive: setActiveKey,
    registerKey,
    unRegisterKey,
  } = useContext(FileSystemsContext);
  const key = useSimpleUniqueKey();
  useEffect(() => {
    registerKey(key);
    return () => unRegisterKey(key);
  }, [key, registerKey, unRegisterKey]);
  const setActive = useCallback(() => setActiveKey(key), [key, setActiveKey]);

  const [state, dispatch] = useReducer(reduce, {}, init);
  const [reloadToken, setReloadToken] = useState(0);
  const reload = useCallback(() => setReloadToken((t) => t + 1), [setReloadToken]);
  useEffect(
    () => dispatchAbortable(dispatch, initialize, { index, dispatch }),
    [index, dispatch, reloadToken],
  );
  ipcEvent('reloadFileSystemsCallback', reload);
  const onChangeAdapter = useCallback(
    (newIdentifier) => change(index, newIdentifier, dispatch),
    [dispatch, index],
  );
  const {
    pending,
    error,
    available,
    identifier,
    directory,
    restricted,
  } = state;
  return useMemo(() => ({
    directory,
    pending,
    error,
    available,
    restricted,
    identifier,
    onChangeAdapter,
    initialized: !error && !pending && !!identifier,
    active: active === key,
    key,
    setActive,
  }), [
    key,
    pending,
    error,
    available,
    restricted,
    identifier,
    directory,
    onChangeAdapter,
    active,
    setActive,
  ]);
}
