import {useCallback, useEffect, useReducer} from 'react';
import {initializeFileSystem} from '../../models/file-systems';
import {Sorting, sort, nextSorter} from './sorting';

function init({type, initialDirectory}) {
  return {
    ready: false,
    type,
    initialDirectory,
    fileSystem: undefined,
    path: initialDirectory,
    contents: [],
    error: undefined,
    pending: true,
    refreshRequest: 0,
    initializeRequest: 0,
    selection: [],
    activeSelection: [], // selection allowed to copy/move/remove
    cursor: -1,
    lastSelectionIndex: -1,
    sorting: Sorting.nameAsc,
  };
}

function reducer (state, action) {
  const getActiveSelection = (selection) => {
    const {contents} = state;
    const back = (contents || []).find(c => c.isBackLink);
    return (selection || [])
      .filter(s => back ? s !== back.path : true)
      .slice();
  };
  const selectItem = (element, ctrlKey, shiftKey) => {
    const {
      contents,
      selection,
      lastSelectionIndex,
    } = state;
    let newSelection = selection.slice();
    const currentSelectionIndex = contents.findIndex(c => c.path === element);
    if (currentSelectionIndex === -1) {
      return state;
    }
    if (shiftKey && lastSelectionIndex >= 0) {
      newSelection = [];
      const range = {
        from: Math.min(currentSelectionIndex, lastSelectionIndex),
        to: Math.max(currentSelectionIndex, lastSelectionIndex),
      }
      const selectItemIfNotSelectedYet = (itemIndex) => {
        const idx = newSelection.indexOf(contents[itemIndex].path);
        if (idx === -1) {
          newSelection.push(contents[itemIndex].path);
        }
      }
      for (let i = range.from; i <= range.to; i++) {
        selectItemIfNotSelectedYet(i);
      }
      return {
        ...state,
        selection: newSelection,
        cursor: currentSelectionIndex,
        activeSelection: getActiveSelection(newSelection),
      }
    } else {
      const index = newSelection.indexOf(element);
      if (index === -1) {
        if (ctrlKey) {
          newSelection.push(element);
        } else {
          newSelection = [element];
        }
      } else if (ctrlKey) {
        newSelection.splice(index, 1)
      } else {
        newSelection = [element];
      }
      return {
        ...state,
        selection: newSelection,
        activeSelection: getActiveSelection(newSelection),
        lastSelectionIndex: currentSelectionIndex,
        cursor: currentSelectionIndex,
      }
    }
  };
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
      activeSelection: [],
      lastSelectionIndex: -1,
      cursor: -1,
      error: undefined,
    };
    case 'set-contents': return {
      ...state,
      contents: sort(state.sorting, action.contents),
      error: undefined,
      selection: [],
      activeSelection: [],
      lastSelectionIndex: -1,
      cursor: -1,
      ready: true,
      pending: false,
    };
    case 'set-error': return {...state, error: action.error, ready: false, pending: false};
    case 'set-pending': return {...state, pending: action.pending, ready: false};
    case 'set-selection': return selectItem(action.element, action.ctrlKey, action.shiftKey);
    case 'next-selection': {
      const {
        cursor,
        contents,
      } = state;
      let index = (cursor + 1);
      if (index >= contents.length && !action.shiftKey) {
        index = 0;
      }
      if (index < contents.length) {
        return selectItem(contents[index].path, false, action.shiftKey);
      }
      return state;
    }
    case 'previous-selection': {
      const {
        cursor,
        contents,
      } = state;
      let index = (cursor - 1);
      if (
        cursor === -1 || (index < 0 && !action.shiftKey)
      ) {
        index = contents.length - 1;
      }
      if (index >= 0) {
        return selectItem(contents[index].path, false, action.shiftKey);
      }
      return state;
    }
    case 'clear-selection': return {
      ...state,
      selection: [],
      activeSelection: [],
      lastSelectionIndex: -1,
      cursor: -1,
    };
    case 'set-last-selection-index': return {
      ...state,
      lastSelectionIndex: action.index,
      cursor: -1,
    };
    case 'clear': return {
      ...state,
      path: undefined,
      error: undefined,
      contents: [],
      pending: false,
      selection: [],
      activeSelection: [],
      lastSelectionIndex: -1,
      cursor: -1,
    };
    case 'refresh': return {...state, refreshRequest: state.refreshRequest + 1};
    case 'initialize': return {...state, initializeRequest: state.initializeRequest + 1};
    case 'reset': return init(state);
  }
  return state;
}

function useFileSystem (type, initialDirectory) {
  const [state, dispatch] = useReducer(reducer, {type, initialDirectory}, init);
  const {
    ready,
    fileSystem,
    path,
    contents,
    error,
    pending,
    selection,
    activeSelection,
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
  const moveToSelection = useCallback((arg) => {
    if (selection.length === 1) {
      const item = contents.find(c => c.path === selection[0]);
      if (item.isDirectory) {
        dispatch({type: 'set-path', path: item.path});
      }
    }
  }, [dispatch, selection, contents]);
  const selectItem = useCallback((element, modifiers = {}) => {
    dispatch({
      type: 'set-selection',
      element,
      ctrlKey: !!modifiers.ctrlKey,
      shiftKey: !!modifiers.shiftKey
    });
  }, [dispatch]);
  const moveCursor = useCallback((down, modifiers = {}) => {
    if (down) {
      dispatch({
        type: 'next-selection',
        ctrlKey: !!modifiers.ctrlKey,
        shiftKey: !!modifiers.shiftKey
      });
    } else {
      dispatch({
        type: 'previous-selection',
        ctrlKey: !!modifiers.ctrlKey,
        shiftKey: !!modifiers.shiftKey
      });
    }
  }, [dispatch]);
  const clearSelection = useCallback(() => {
    dispatch({type: 'clear-selection'});
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
    activeSelection,
    selectItem,
    moveCursor,
    clearSelection,
    moveToSelection,
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
