import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
} from 'react';
import { FileSystemContext, useFileSystemPathChangeCallback } from './use-file-system';

function init() {
  return {
    identifier: undefined,
    path: undefined,
  };
}

const Actions = {
  init: 'init',
  change: 'change',
};

function reduce(state, action) {
  switch (action.type) {
    case Actions.init:
      return { ...state, identifier: action.identifier, path: action.path };
    case Actions.change:
      return { ...state, path: action.path };
    default:
      return state;
  }
}

function useDefaultPath(dispatch) {
  const {
    identifier,
    directory,
  } = useContext(FileSystemContext);
  useEffect(() => {
    if (identifier) {
      dispatch({ type: Actions.init, identifier, path: directory });
    }
  }, [
    identifier,
    directory,
    dispatch,
  ]);
}

const FileSystemPathContext = React.createContext({});

function usePathNavigation() {
  const { onChangePath } = useContext(FileSystemPathContext);
  return onChangePath;
}

function useCurrentPath() {
  const { path } = useContext(FileSystemPathContext);
  return path;
}

export { FileSystemPathContext, usePathNavigation, useCurrentPath };

export default function useFileSystemPath() {
  const [state, dispatch] = useReducer(reduce, {}, init);
  const reportChange = useFileSystemPathChangeCallback();
  useDefaultPath(dispatch);
  const {
    identifier,
    path,
  } = state;
  const onChangePath = useCallback(
    (newPath) => dispatch({ type: Actions.change, path: newPath }),
    [dispatch],
  );
  useEffect(() => {
    if (typeof reportChange === 'function') {
      reportChange(identifier, path);
    }
  }, [reportChange, path, identifier]);
  return useMemo(() => ({
    identifier,
    path,
    onChangePath,
  }), [
    identifier,
    path,
    onChangePath,
  ]);
}
