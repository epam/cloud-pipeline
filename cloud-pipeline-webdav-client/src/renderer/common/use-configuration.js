import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
} from 'react';
import ipcResponse from './ipc-response';

function init() {
  return {
    pending: false,
    error: undefined,
    configuration: {},
  };
}

const ACTIONS = {
  init: 'init',
  loading: 'loading',
  error: 'error',
  change: 'change',
};

function reduce(state, action) {
  switch (action.type) {
    case ACTIONS.init:
      return {
        ...state,
        pending: false,
        error: undefined,
        configuration: action.configuration,
      };
    case ACTIONS.error:
      return {
        ...state,
        pending: false,
        error: action.error,
      };
    case ACTIONS.loading:
      return {
        ...state,
        pending: true,
        error: undefined,
      };
    case ACTIONS.change:
      return {
        ...state,
        configuration: {
          ...(state.configuration || {}),
          [action.property]: action.value,
        },
      };
    default:
      return state;
  }
}

export default function useConfiguration() {
  const [state, dispatch] = useReducer(reduce, {}, init);
  const reload = useCallback(() => {
    dispatch({ type: ACTIONS.loading });
    ipcResponse('getConfiguration')
      .then((config) => {
        dispatch({ type: ACTIONS.init, configuration: config });
      })
      .catch((error) => {
        dispatch({ type: ACTIONS.error, error: error.message });
      });
  }, [dispatch]);
  useEffect(() => reload(), [reload]);
  const onChangeProperty = useCallback((property, value) => {
    if (property) {
      dispatch({ type: ACTIONS.change, property, value });
    }
  }, [dispatch]);
  const createOnChangeCallback = useCallback(
    (property) => (value) => onChangeProperty(property, value),
    [onChangeProperty],
  );
  const {
    pending,
    error,
    configuration,
  } = state;
  const onAddFTPServer = useCallback(() => {
    onChangeProperty('ftp', [...(configuration?.ftp || []), { useDefaultUser: true }]);
  }, [configuration?.ftp, onChangeProperty]);
  const onChangeFTPServer = useCallback((index, ftpServer) => {
    const ftpServers = (configuration?.ftp || []).slice();
    ftpServers.splice(index, 1, ftpServer);
    onChangeProperty('ftp', ftpServers);
  }, [configuration?.ftp, onChangeProperty]);
  const onRemoveFTPServer = useCallback((index) => {
    const ftpServers = (configuration?.ftp || []).slice();
    ftpServers.splice(index, 1);
    onChangeProperty('ftp', ftpServers);
  }, [configuration?.ftp, onChangeProperty]);
  return useMemo(() => ({
    configuration,
    reload,
    pending,
    error,
    onChangeProperty,
    createOnChangeCallback,
    onAddFTPServer,
    onChangeFTPServer,
    onRemoveFTPServer,
  }), [
    configuration,
    reload,
    pending,
    error,
    onChangeProperty,
    createOnChangeCallback,
    onAddFTPServer,
    onChangeFTPServer,
    onRemoveFTPServer,
  ]);
}
