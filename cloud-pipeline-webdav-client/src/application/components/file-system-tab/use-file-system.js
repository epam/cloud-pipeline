import {useCallback, useEffect, useReducer} from 'react';
import {initializeFileSystem} from '../../models/file-systems';
import {Sorting, sort, nextSorter} from './sorting';

function init(type) {
  return {
    ready: false,
    type,
    fileSystem: undefined,
    path: undefined,
    contents: [],
    error: undefined,
    pending: true,
    refreshRequest: 0,
    initializeRequest: 0,
    selection: [],
    lastSelectionIndex: -1,
    sorting: Sorting.nameAsc,
  };
}

function reducer (state, action) {
  switch (action.type) {
    case 'set-ready': return {...state, ready: action.ready};
    case 'set-file-system': return {...state, fileSystem: action.fileSystem};
    case 'set-sorting': {
      const nextSorting = nextSorter(state.sorting, action.property);
      return {
        ...state,
        sorting: nextSorting,
        contents: sort(nextSorting, state.contents),
      };
    }
    case 'set-path': return {
      ...state,
      path: action.path,
      selection: [],
      lastSelectionIndex: -1,
      error: undefined,
    };
    case 'set-contents': return {
      ...state,
      contents: sort(state.sorting, action.contents),
      error: undefined,
      selection: [],
      lastSelectionIndex: -1,
      ready: true,
      pending: false,
    };
    case 'set-error': return {...state, error: action.error, ready: false, pending: false};
    case 'set-pending': return {...state, pending: action.pending, ready: false};
    case 'set-selection': return {...state, selection: (action.selection || []).slice()};
    case 'set-last-selection-index': return {...state, lastSelectionIndex: action.index};
    case 'clear': return {
      ...state,
      path: undefined,
      error: undefined,
      contents: [],
      pending: false,
      selection: [],
      lastSelectionIndex: -1,
    };
    case 'refresh': return {...state, refreshRequest: state.refreshRequest + 1};
    case 'initialize': return {...state, initializeRequest: state.initializeRequest + 1};
    case 'reset': return init(state.type);
  }
  return state;
}

function useFileSystem (type) {
  const [state, dispatch] = useReducer(reducer, type, init);
  const {
    ready,
    fileSystem,
    path,
    contents,
    error,
    pending,
    selection,
    lastSelectionIndex,
    refreshRequest,
    initializeRequest,
    sorting,
  } = state;
  const onRefresh = useCallback(() => {
    dispatch({type: 'refresh'});
  }, [dispatch]);
  const onInitialize = useCallback(() => {
    dispatch({type: 'initialize'});
  }, [dispatch]);
  const setSorting = useCallback((property) => {
    dispatch({type: 'set-sorting', property});
  }, [dispatch]);
  const setPath = useCallback((arg) => {
    dispatch({type: 'set-path', path: arg});
  }, [dispatch]);
  const setSelection = useCallback((newSelection) => {
    dispatch({type: 'set-selection', selection: newSelection});
  }, [dispatch]);
  const setLastSelectionIndex = useCallback((index) => {
    dispatch({type: 'set-last-selection-index', index});
  }, [dispatch]);
  useEffect(() => {
    dispatch({type: 'reset'});
    const impl = initializeFileSystem(type);
    if (impl) {
      impl.initialize()
        .then(() => {
          dispatch({type: 'set-file-system', fileSystem: impl});
        })
        .catch(e => {
          dispatch({type: 'set-error', error: e});
        })
      return () => impl.close();
    }
    return undefined;
  }, [
    type,
    dispatch,
    initializeRequest,
  ]);
  useEffect(() => {
    if (fileSystem) {
      dispatch({type: 'set-pending', pending: true});
      fileSystem
        .getDirectoryContents(path)
        .then(contents => {
          dispatch({type: 'set-contents', contents});
        })
        .catch(e => {
          dispatch({type: 'set-error', error: e});
        });
    }
  }, [
    fileSystem,
    path,
    dispatch,
    refreshRequest,
  ]);
  return {
    ready,
    fileSystem,
    path,
    contents,
    error,
    pending,
    selection,
    setSelection,
    lastSelectionIndex,
    setLastSelectionIndex,
    refreshRequest,
    initializeRequest,
    onRefresh,
    onInitialize,
    setPath,
    sorting,
    setSorting,
  };
}

export default useFileSystem;
