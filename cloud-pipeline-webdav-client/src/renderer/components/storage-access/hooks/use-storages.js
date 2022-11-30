import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useState,
} from 'react';
import dispatchAbortable from '../../../common/dispatch-abortable';
import ipcResponse from '../../../common/ipc-response';

function init() {
  return {
    pending: true,
    error: undefined,
    storages: [],
  };
}

const ACTIONS = {
  loading: 'loading',
  loaded: 'loaded',
  error: 'error',
};

function reduce(state, action) {
  switch (action.type) {
    case ACTIONS.loading:
      return { ...state, pending: true, error: undefined };
    case ACTIONS.error:
      return { ...state, pending: false, error: action.error };
    case ACTIONS.loaded:
      return {
        ...state,
        pending: false,
        storages: action.storages,
        error: undefined,
      };
    default:
      return state;
  }
}

async function fetchStorages(dispatch) {
  try {
    dispatch({ type: ACTIONS.loading });
    const storages = await ipcResponse('getStoragesWithDavAccessInfo');
    const filtered = (storages || [])
      .filter((o) => !/^nfs$/i.test(o.type) && !o.hidden)
      .sort((a, b) => {
        const aName = (a.name || '').toLowerCase();
        const bName = (b.name || '').toLowerCase();
        if (aName < bName) {
          return -1;
        }
        if (aName > bName) {
          return 1;
        }
        return 0;
      });
    dispatch({ type: ACTIONS.loaded, storages: filtered });
  } catch (error) {
    dispatch({ type: ACTIONS.error, error: error.message });
  }
}

export default function useStorages() {
  const [state, dispatch] = useReducer(reduce, {}, init);
  const [reloadToken, setReloadToken] = useState(0);
  const reload = useCallback(() => setReloadToken((t) => t + 1), [setReloadToken]);
  useEffect(() => dispatchAbortable(dispatch, fetchStorages), [dispatch, reloadToken]);
  const {
    pending,
    error,
    storages,
  } = state;
  return useMemo(() => ({
    pending,
    error,
    storages: storages || [],
    reload,
  }), [
    pending,
    error,
    storages,
    reload,
  ]);
}
