import { useContext, useEffect, useReducer } from 'react';
import { FileSystemPathContext } from './use-file-system-path';
import dispatchAbortable from '../../../common/dispatch-abortable';
import ipcResponse from '../../../common/ipc-response';

function init() {
  return {
    pending: true,
    items: [],
    separator: '/',
  };
}

const Actions = {
  loading: 'loading',
  loaded: 'loaded',
};

function reducer(state, action) {
  switch (action.type) {
    case Actions.loading:
      return { ...state, pending: true };
    case Actions.loaded:
      return {
        ...state,
        pending: false,
        items: action.items,
        separator: action.separator,
      };
    default:
      return state;
  }
}

async function fetchBreadcrumbs(dispatch, getAborted, identifier, path) {
  if (!identifier) {
    return;
  }
  dispatch({ type: Actions.loading });
  try {
    dispatch({ type: Actions.loading });
    const {
      components: parts,
      separator,
    } = await ipcResponse('getAdapterPathComponents', identifier, path);
    dispatch({ type: Actions.loaded, items: parts, separator });
  } catch (error) {
    dispatch({ type: Actions.loaded, items: [], separator: '/' });
  }
}

export default function usePathBreadcrumbs() {
  const {
    identifier,
    path,
  } = useContext(FileSystemPathContext);
  const [state, dispatch] = useReducer(reducer, {}, init);
  useEffect(() => dispatchAbortable(
    dispatch,
    fetchBreadcrumbs,
    identifier,
    path,
  ), [dispatch, identifier, path]);
  return state;
}
