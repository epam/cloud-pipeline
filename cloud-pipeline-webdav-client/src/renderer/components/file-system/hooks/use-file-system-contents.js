import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useState,
} from 'react';
import dispatchAbortable from '../../../common/dispatch-abortable';
import ipcResponse from '../../../common/ipc-response';
import { sort, nextSorter, SortingProperty } from '../utilities/sorting';
import fileSystemEvents, { Events } from '../../../common/file-system-events';
import { FileSystemPathContext } from './use-file-system-path';

function init() {
  return {
    pending: false,
    error: false,
    items: [],
  };
}

const Actions = {
  loading: 'loading',
  loaded: 'loaded',
  error: 'error',
  change: 'change',
};

function reduce(state, action) {
  switch (action.type) {
    case Actions.loading:
      return { ...state, pending: true, error: undefined };
    case Actions.loaded:
      return {
        ...state, pending: false, error: undefined, items: action.items || [],
      };
    case Actions.error:
      return { ...state, pending: false, error: action.error };
    default:
      return state;
  }
}

async function fetchContents(dispatch, getAborted, adapter, path) {
  if (!adapter) {
    return;
  }
  try {
    dispatch({ type: Actions.loading });
    const result = await ipcResponse('getAdapterContentsOnPath', adapter, path);
    dispatch({ type: Actions.loaded, items: result });
  } catch (error) {
    dispatch({ type: Actions.error, error: error.message });
  }
}

const FileSystemContentsContext = React.createContext({});

function useFileSystemContents() {
  const { items } = useContext(FileSystemContentsContext);
  return items;
}

function useCreateFileSystemContentsStore() {
  const [state, dispatch] = useReducer(reduce, {}, init);
  const [sorting, setSorting] = useState(undefined);
  const {
    items,
    pending,
    error,
  } = state;
  const sorted = useMemo(() => sort(sorting, items), [sorting, items]);
  const onChangeSortingProperty = useCallback(
    (property) => setSorting(nextSorter(sorting, property)),
    [setSorting, sorting],
  );
  const onSortByName = useCallback(
    () => onChangeSortingProperty(SortingProperty.name),
    [onChangeSortingProperty],
  );
  const onSortByChangedDate = useCallback(
    () => onChangeSortingProperty(SortingProperty.changed),
    [onChangeSortingProperty],
  );
  const onSortBySize = useCallback(
    () => onChangeSortingProperty(SortingProperty.size),
    [onChangeSortingProperty],
  );
  const {
    identifier,
    path,
  } = useContext(FileSystemPathContext);
  const [token, setToken] = useState(0);
  useEffect(() => dispatchAbortable(
    dispatch,
    fetchContents,
    identifier,
    path,
  ), [
    identifier,
    path,
    dispatch,
    token,
  ]);
  const onRefresh = useCallback(
    () => setToken((o) => o + 1),
    [setToken],
  );
  useEffect(() => {
    const handler = (e, fileSystemPaths = []) => {
      const current = fileSystemPaths.find((o) => o.identifier === identifier);
      if (current) {
        const { path: reloadAtPath } = current;
        if (
          !reloadAtPath
          || reloadAtPath === path
          || reloadAtPath.startsWith(path)
        ) {
          onRefresh();
        }
      }
    };
    fileSystemEvents.addEventListener(Events.reload, handler);
    return () => fileSystemEvents.removeEventListener(Events.reload, handler);
  }, [identifier, path, onRefresh]);
  return useMemo(() => ({
    path,
    items: sorted,
    pending,
    error,
    sorting,
    onRefresh,
    onSortByName,
    onSortByChangedDate,
    onSortBySize,
  }), [
    path,
    sorted,
    pending,
    error,
    onRefresh,
    sorting,
    onSortByName,
    onSortByChangedDate,
    onSortBySize,
  ]);
}

export {
  useFileSystemContents,
  useCreateFileSystemContentsStore,
  FileSystemContentsContext,
};
