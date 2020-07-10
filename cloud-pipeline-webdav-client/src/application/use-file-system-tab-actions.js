import {useCallback, useReducer} from 'react';
import submit from './models/commands/submit-command';
import commands from './models/commands/commands';

const LEFT_TAB_ID = 0;
const RIGHT_TAB_ID = 1;

function init() {
  return {
    operations: [],
    active: LEFT_TAB_ID
  };
}

function reducer(state, action) {
  switch (action.type) {
    case 'operation': {
      const {operations, ...rest} = state;
      const operation = action.operation;
      if (operation) {
        const [existing] = operations.filter(o => o.identifier === operation.identifier);
        if (existing) {
          operations.splice(operations.indexOf(existing), 1, operation);
        } else {
          operations.push(operation);
        }
        return {
          ...rest,
          operations: operations.slice()
        }
      }
      break;
    }
    case 'tab-active': {
      return {...state, active: action.id};
    }
    case 'reset': return init();
  }
  return state;
}

function useFileSystemTabActions (leftTab, rightTab) {
  const [state, dispatch] = useReducer(reducer, undefined, init);
  const {
    operations,
    active,
  } = state;
  const setLeftTabActive = useCallback(() => {
    dispatch({type: 'tab-active', id: LEFT_TAB_ID});
  }, [dispatch]);
  const setRightTabActive = useCallback(() => {
    dispatch({type: 'tab-active', id: RIGHT_TAB_ID});
  }, [dispatch]);
  const onOperationProgress = useCallback((operation) => {
    dispatch({type: 'operation', operation})
  }, [dispatch]);
  const onCommand = useCallback((
    sourceFS,
    destinationFS,
    destinationPath,
    command,
    sourcePath,
    sources,
  ) => {
    const operation = submit(
      command,
      {
        fs: sourceFS,
        path: sourcePath,
        elements: sources,
      },
      {
        fs: destinationFS,
        path: destinationPath,
      },
      onOperationProgress,
    );
    if (operation) {
      dispatch({type: 'operation', operation});
      operation.run()
        .then(() => {
          leftTab.onRefresh();
          rightTab.onRefresh();
        });
    }
  }, [dispatch, onOperationProgress]);
  const onLeftFSCommand = useCallback((...args) => {
    onCommand(
      leftTab.fileSystem,
      rightTab.fileSystem,
      rightTab.path,
      ...args
    );
  }, [onCommand, rightTab, leftTab]);
  const onRightFSCommand = useCallback((...args) => {
    onCommand(
      rightTab.fileSystem,
      leftTab.fileSystem,
      leftTab.path,
      ...args
    );
  }, [onCommand, rightTab, leftTab]);
  const onDropCommand = useCallback((dropFS, dropTarget, sourceFSIdentifier, ...sources) => {
    const [sourceFS] = [leftTab.fileSystem, rightTab.fileSystem]
      .filter(fs => fs.identifier === sourceFSIdentifier);
    onCommand(
      sourceFS,
      dropFS,
      dropTarget,
      commands.copy,
      undefined,
      sources
    );
  }, [onCommand, rightTab, leftTab]);
  return {
    operations,
    leftTabActive: active === LEFT_TAB_ID,
    rightTabActive: active === RIGHT_TAB_ID,
    leftPath: leftTab.path,
    rightPath: rightTab.path,
    leftTabReady: leftTab.ready,
    rightTabReady: rightTab.ready,
    setLeftPath: leftTab.setPath,
    setRightPath: rightTab.setPath,
    setLeftTabActive,
    setRightTabActive,
    onLeftFSCommand,
    onRightFSCommand,
    onDropCommand,
  };
}

export default useFileSystemTabActions;
