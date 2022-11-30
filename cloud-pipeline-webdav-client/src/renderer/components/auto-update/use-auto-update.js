import {
  useCallback, useEffect,
  useMemo,
  useReducer,
} from 'react';
import ipcEvent from '../../common/ipc-event';
import ipcResponse from '../../common/ipc-response';
import dispatchAbortable from '../../common/dispatch-abortable';

function init() {
  return {
    appName: undefined,
    pending: false,
    error: undefined,
    available: undefined,
    supported: false,
    ignored: [],
    checkPending: false,
    checkError: undefined,
  };
}

const ACTIONS = {
  app: 'app',
  pending: 'pending',
  checkStarted: 'check-pending',
  checkResult: 'check-result',
  checkError: 'check-error',
  error: 'error',
  ignore: 'ignore',
};

function reduce(state, action) {
  switch (action.type) {
    case ACTIONS.pending:
      return { ...state, pending: true, error: undefined };
    case ACTIONS.error:
      return { ...state, pending: false, error: action.error };
    case ACTIONS.app:
      return { ...state, appName: action.appName };
    case ACTIONS.checkStarted:
      return { ...state, checkPending: true, checkError: undefined };
    case ACTIONS.checkError:
      return { ...state, checkPending: false, checkError: action.error };
    case ACTIONS.checkResult:
      return {
        ...state,
        checkPending: false,
        checkError: undefined,
        available: action.available,
        supported: action.supported,
        appName: action.appName || state.appName,
      };
    case ACTIONS.ignore:
      return {
        ...state,
        ignored: [...state.ignored, state.available].filter(Boolean),
      };
    default:
      return state;
  }
}

async function initialize(dispatch, aborted, force = false) {
  try {
    dispatch({ type: ACTIONS.checkStarted });
    const {
      available,
      supported,
    } = await ipcResponse('getAutoUpdateVersion', force);
    const {
      appName = 'Cloud Data',
    } = await ipcResponse('getConfiguration');
    dispatch({
      type: ACTIONS.checkResult,
      available,
      supported,
      appName,
    });
  } catch (error) {
    dispatch({
      type: ACTIONS.checkError,
      error: `Error checking updates: ${error.message}`,
    });
  }
}

export default function useAutoUpdate() {
  const [state, dispatch] = useReducer(reduce, {}, init);
  const {
    appName,
    pending,
    error,
    available,
    supported,
    ignored,
    checkPending,
    checkError,
  } = state;
  const ignoreVersion = useCallback(() => {
    dispatch({ type: ACTIONS.ignore });
  }, [dispatch]);
  const update = useCallback(() => {
    dispatch({ type: ACTIONS.pending });
    ipcResponse('autoUpdate')
      .then(() => {})
      .catch((e) => dispatch({ type: ACTIONS.error, error: e.message }));
  }, []);
  useEffect(() => {
    const callback = (e, info) => dispatch({ type: ACTIONS.checkResult, ...info });
    ipcEvent('autoUpdateAvailableCallback', callback);
  }, []);
  useEffect(() => dispatchAbortable(dispatch, initialize), [dispatch]);
  const check = useCallback(() => {
    (initialize)(dispatch, undefined, true);
  }, [dispatch]);
  return useMemo(() => ({
    appName,
    pending,
    error,
    available: !!available && !ignored.includes(available),
    supported,
    ignore: ignoreVersion,
    update,
    check,
    checkPending,
    checkError,
  }), [
    appName,
    pending,
    error,
    available,
    supported,
    ignored,
    ignoreVersion,
    update,
    check,
    checkError,
    checkPending,
  ]);
}
