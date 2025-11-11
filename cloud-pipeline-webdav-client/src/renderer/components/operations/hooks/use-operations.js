import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
} from 'react';
import fileSystemEvents, { Events } from '../../../common/file-system-events';

function init() {
  return {
    operations: [],
    closedOperations: [],
  };
}

const ACTIONS = {
  report: 'report',
  close: 'close',
};

function reducer(state, action) {
  switch (action.type) {
    case ACTIONS.report: {
      const { operation } = action;
      if (!operation || !operation.id) {
        return state;
      }
      const operations = state.operations
        .filter((o) => o.id !== operation.id)
        .concat(operation)
        .sort((a, b) => a.id - b.id);
      return {
        ...state,
        operations: [...operations],
      };
    }
    case ACTIONS.close: {
      const { operation } = action;
      if (!operation || !operation.id) {
        return state;
      }
      const { closedOperations = [] } = state;
      if (closedOperations.includes(operation.id)) {
        return state;
      }
      return {
        ...state,
        closedOperations: [...closedOperations, operation.id],
      };
    }
    default:
      return state;
  }
}

export default function useOperations() {
  const [state, dispatch] = useReducer(reducer, {}, init);
  const reportOperation = useCallback(
    (event, operation) => dispatch({ type: ACTIONS.report, operation }),
    [dispatch],
  );
  const closeOperation = useCallback(
    (operation) => dispatch({ type: ACTIONS.close, operation }),
    [dispatch],
  );
  useEffect(() => {
    fileSystemEvents.addEventListener(Events.operation, reportOperation);
    return () => fileSystemEvents.removeEventListener(Events.operation, reportOperation);
  }, [reportOperation]);
  const {
    operations,
    closedOperations,
  } = state;
  return useMemo(
    () => ({
      operations: operations
        .filter((operation) => !/^(done)$/i.test(operation.status)
          && !(closedOperations || []).includes(operation.id)),
      closeOperation,
    }),
    [
      operations,
      closedOperations,
      closeOperation,
    ],
  );
}
