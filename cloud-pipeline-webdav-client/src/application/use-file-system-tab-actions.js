import {useCallback, useReducer, useState} from 'react';
import submit from './models/commands/submit-command';
import commands from './models/commands/commands';
import showConfirmationDialog from './components/file-system-tab/show-confirmation-dialog';

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
  }, [
    onCommand,
    rightTab?.fileSystem,
    rightTab?.path,
    leftTab?.fileSystem
  ]);
  const onRightFSCommand = useCallback((...args) => {
    onCommand(
      rightTab.fileSystem,
      leftTab.fileSystem,
      leftTab.path,
      ...args
    );
  }, [
    onCommand,
    leftTab?.fileSystem,
    leftTab?.path,
    rightTab?.fileSystem
  ]);
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
  }, [onCommand, rightTab?.fileSystem, leftTab?.fileSystem]);
  const reInitialize = useCallback(() => {
    rightTab.onInitialize();
    leftTab.onInitialize();
  }, [rightTab?.onInitialize, leftTab?.onInitialize]);
  const changeTab = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      setRightTabActive();
    } else {
      setLeftTabActive();
    }
  }, [setLeftTabActive, setRightTabActive, active]);
  const clearSelection = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      leftTab?.clearSelection();
    } else {
      rightTab?.clearSelection();
    }
  }, [leftTab?.clearSelection, rightTab?.clearSelection, active]);
  const moveCursor = useCallback((down, modifiers) => {
    if (active === LEFT_TAB_ID) {
      leftTab?.moveCursor(down, modifiers);
    } else {
      rightTab?.moveCursor(down, modifiers);
    }
  }, [leftTab?.moveCursor, rightTab?.moveCursor, active]);
  const moveToSelection = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      leftTab?.moveToSelection();
    } else {
      rightTab?.moveToSelection();
    }
  }, [leftTab?.moveToSelection, rightTab?.moveToSelection, active]);
  const onCopyLeft = useCallback(() => {
    onLeftFSCommand(
      commands.copy,
      leftTab?.path,
      leftTab?.activeSelection,
    )
  }, [
    onLeftFSCommand,
    leftTab?.path,
    leftTab?.activeSelection,
    leftTab?.contents,
  ]);
  const onCopyRight = useCallback(() => {
    onRightFSCommand(
      commands.copy,
      rightTab?.path,
      rightTab?.activeSelection,
    )
  }, [
    onRightFSCommand,
    rightTab?.path,
    rightTab?.activeSelection,
    rightTab?.contents,
  ]);
  const onMoveLeft = useCallback(() => {
    onLeftFSCommand(
      commands.move,
      leftTab?.path,
      leftTab?.activeSelection,
    )
  }, [
    onLeftFSCommand,
    leftTab?.path,
    leftTab?.activeSelection,
    leftTab?.contents,
  ]);
  const onMoveRight = useCallback(() => {
    onRightFSCommand(
      commands.move,
      rightTab?.path,
      rightTab?.activeSelection,
    )
  }, [
    onRightFSCommand,
    rightTab?.path,
    rightTab?.activeSelection,
    rightTab?.contents,
  ]);
  const [hotKeysBlocked, setHotKeysBlocked] = useState(false);
  const onTabRemoveCommand = useCallback((path, contents, selection, onTabCommand) => {
    return new Promise((resolve) => {
      if (selection.length > 0) {
        const description = selection.length > 1
          ? `${selection.length} items`
          : selection[0];
        setHotKeysBlocked(true);
        showConfirmationDialog(`Are you sure you want to delete ${description}?`)
          .then(confirmed => {
            if (confirmed) {
              onTabCommand && onTabCommand(
                commands.delete,
                path,
                selection
              );
            }
            setHotKeysBlocked(false);
            resolve();
          });
      }
    });
  }, []);
  const onRemoveLeft = useCallback(() => {
    onTabRemoveCommand(
      leftTab?.path,
      leftTab?.contents,
      leftTab?.activeSelection,
      onLeftFSCommand,
    )
      .then(() => {
        leftTab?.clearSelection();
      });
  }, [
    onTabRemoveCommand,
    leftTab?.path,
    leftTab?.contents,
    leftTab?.activeSelection,
    leftTab?.clearSelection,
    onLeftFSCommand,
  ]);
  const onRemoveRight = useCallback(() => {
    onTabRemoveCommand(
      rightTab?.path,
      rightTab?.contents,
      rightTab?.activeSelection,
      onRightFSCommand,
    )
      .then(() => {
        rightTab?.clearSelection();
      });
  }, [
    onTabRemoveCommand,
    rightTab?.path,
    rightTab?.contents,
    rightTab?.activeSelection,
    rightTab?.clearSelection,
    onRightFSCommand,
  ]);
  const refresh = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      leftTab?.onRefresh();
    } else {
      rightTab?.onRefresh();
    }
  }, [leftTab?.onRefresh, rightTab?.onRefresh, active]);
  const copy = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      onCopyLeft();
    } else {
      onCopyRight();
    }
  }, [onCopyLeft, onCopyRight, active]);
  const move = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      onMoveLeft();
    } else {
      onMoveRight();
    }
  }, [onMoveLeft, onMoveRight, active]);
  const remove = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      onRemoveLeft();
    } else {
      onRemoveRight();
    }
  }, [onRemoveLeft, onRemoveRight, active]);

  const [
    createDirectoryHandler,
    setCreateDirectoryHandler
  ] = useState(null);
  const onCancelCreateDirectory = useCallback(() => {
    setCreateDirectoryHandler(null);
    setHotKeysBlocked(false);
  }, [setCreateDirectoryHandler, setHotKeysBlocked]);

  const onCreateDirectoryCommand = useCallback((
    tabCommand,
    fileSystem,
    path,
    tabClearSelection,
    directoryName
  ) => {
    tabCommand(commands.createDirectory, fileSystem.joinPath(path, directoryName));
    tabClearSelection();
    onCancelCreateDirectory();
  }, [onCancelCreateDirectory]);
  const onCreateDirectoryLeft = useCallback((directory) => {
    onCreateDirectoryCommand(
      onLeftFSCommand,
      leftTab?.fileSystem,
      leftTab?.path,
      leftTab?.clearSelection,
      directory
    )
  }, [
    onLeftFSCommand,
    leftTab?.fileSystem,
    leftTab?.path,
    leftTab?.clearSelection,
  ]);
  const onCreateDirectoryRight = useCallback((directory) => {
    onCreateDirectoryCommand(
      onRightFSCommand,
      rightTab?.fileSystem,
      rightTab?.path,
      rightTab?.clearSelection,
      directory
    )
  }, [
    onRightFSCommand,
    rightTab?.fileSystem,
    rightTab?.path,
    rightTab?.clearSelection,
  ]);

  const onCreateDirectoryLeftRequest = useCallback(() => {
    setHotKeysBlocked(true);
    setCreateDirectoryHandler(() => onCreateDirectoryLeft);
  }, [onCreateDirectoryLeft, setCreateDirectoryHandler]);
  const onCreateDirectoryRightRequest = useCallback(() => {
    setHotKeysBlocked(true);
    setCreateDirectoryHandler(() => onCreateDirectoryRight);
  }, [onCreateDirectoryRight, setCreateDirectoryHandler]);
  const onCreateDirectoryRequest = useCallback(() => {
    if (active === LEFT_TAB_ID) {
      onCreateDirectoryLeftRequest();
    } else {
      onCreateDirectoryRightRequest();
    }
  }, [active, onCreateDirectoryLeftRequest, onCreateDirectoryRightRequest]);
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
    onDropCommand,
    reInitialize,
    changeTab,
    clearSelection,
    moveCursor,
    moveToSelection,
    copy,
    onCopyLeft,
    onCopyRight,
    move,
    onMoveLeft,
    onMoveRight,
    remove,
    onRemoveLeft,
    onRemoveRight,
    refresh,
    createDirectory: {
      createDirectoryHandler,
      onCreateDirectoryRequest,
      onCreateDirectoryLeft: onCreateDirectoryLeftRequest,
      onCreateDirectoryRight: onCreateDirectoryRightRequest,
      onCancelCreateDirectory,
    },
    hotKeysBlocked
  };
}

export default useFileSystemTabActions;
