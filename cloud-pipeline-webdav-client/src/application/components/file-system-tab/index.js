import React, {useCallback, useState} from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  ConfigProvider,
  Divider,
  Spin,
} from 'antd';
import classNames from 'classnames';
import FileSystemElement from './file-system-element';
import PathNavigation from './path-navigation';
import CreateDirectoryDialog from './create-directory-dialog';
import showConfirmationDialog from './show-confirmation-dialog';
import {Commands} from '../../models/commands';
import './file-system-tab.css';

function FileSystemTab (
  {
    fileSystem,
    active,
    becomeActive,
    error,
    contents,
    pending,
    path,
    setPath,
    selection,
    setSelection,
    lastSelectionIndex,
    setLastSelectionIndex,
    onRefresh,
    className,
    oppositeFileSystemReady,
    onCommand,
    dragging,
    setDragging,
    onDropCommand,
  }
) {
  const [hovered, setHovered] = useState(undefined);
  const [dropTarget, setDropTarget] = useState(undefined);
  const [createDirectoryDialogVisible, setCreateDirectoryDialogVisible] = useState(false);
  const becomeActiveCallback = useCallback(() => {
    if (!active) {
      becomeActive();
    }
  }, [active, becomeActive]);
  const setDraggingCallback = useCallback(() => {
    if (!dragging && fileSystem) {
      setDragging(fileSystem.identifier);
    }
  }, [dragging, setDragging, fileSystem]);
  const onHover = useCallback((e) => {
    const {path} = e;
    setHovered(path);
  }, [setHovered]);
  const onUnHover = useCallback(() => {
    setHovered(undefined);
  }, [setHovered]);
  const onNavigate = useCallback((newPath) => {
    setHovered(undefined);
    setPath(newPath);
    becomeActiveCallback();
  }, [becomeActiveCallback, setHovered, setPath]);
  const onSelect = useCallback((element, ctrlKey, shiftKey) => {
    const {path: selectedPath} = element;
    const currentSelectionIndex = contents.indexOf(element);
    if (currentSelectionIndex === -1) {
      return;
    }
    if (shiftKey && lastSelectionIndex >= 0) {
      const range = {
        from: Math.min(currentSelectionIndex, lastSelectionIndex),
        to: Math.max(currentSelectionIndex, lastSelectionIndex),
      }
      const selectItemIfNotSelectedYet = (itemIndex) => {
        const idx = selection.indexOf(contents[itemIndex].path);
        if (idx === -1) {
          selection.push(contents[itemIndex].path);
        }
      }
      for (let i = range.from; i <= range.to; i++) {
        selectItemIfNotSelectedYet(i);
      }
      setSelection(selection);
      setLastSelectionIndex(currentSelectionIndex);
    } else {
      const index = selection.indexOf(selectedPath);
      if (index === -1) {
        if (ctrlKey) {
          setSelection([...selection, selectedPath]);
        } else {
          setSelection([selectedPath]);
        }
      } else if (ctrlKey) {
        selection.splice(index, 1)
        setSelection(selection);
      } else {
        setSelection([selectedPath]);
      }
      setLastSelectionIndex(currentSelectionIndex);
    }
    becomeActiveCallback();
  }, [becomeActiveCallback, selection, setSelection, lastSelectionIndex, setLastSelectionIndex]);
  const onDragStart = useCallback((event, element) => {
    if (element && !element.isBackLink && fileSystem) {
      const {path: elementPath} = element;
      const selected = (selection || []).indexOf(elementPath) >= 0;
      let itemsToDrag = [];
      if (selected) {
        itemsToDrag = selection.slice();
      } else {
        setSelection([elementPath]);
        itemsToDrag = [elementPath];
      }
      const data = [fileSystem.identifier, ...itemsToDrag].join('|');
      event.dataTransfer.setData('text/plain', data);
      event.dataTransfer.effectAllowed = 'copy';
      setDraggingCallback();
      becomeActiveCallback();
    } else {
      event.preventDefault();
    }
  }, [becomeActiveCallback, fileSystem, selection, setSelection, setDraggingCallback]);
  const onDragOver = useCallback((event, element) => {
    const data = event.dataTransfer.getData('text/plain');
    const [identifier, ...sources] = (data || '').split('|');
    setDraggingCallback();
    becomeActiveCallback();
    event.preventDefault();
    event.stopPropagation();
    if (
      element &&
      element.isDirectory &&
      !element.isBackLink &&
      identifier !== undefined &&
      fileSystem &&
      +identifier !== fileSystem.identifier &&
      sources.indexOf(element.path) === -1
    ) {
      if (dropTarget !== element.path) {
        setDropTarget(element.path);
      }
    } else {
      setDropTarget(undefined);
    }
  }, [fileSystem, becomeActiveCallback, dropTarget, setDropTarget, setDraggingCallback]);
  const onDragOverParent = useCallback((event) => {
    const data = event.dataTransfer.getData('text/plain');
    const [identifier] = (data || '').split('|');
    setDraggingCallback();
    becomeActiveCallback();
    if (
      identifier !== undefined &&
      fileSystem &&
      +identifier !== fileSystem.identifier
    ) {
      event.preventDefault();
      if (dropTarget !== path) {
        setDropTarget(path);
      }
    } else {
      setDropTarget(undefined);
    }
  }, [fileSystem, becomeActiveCallback, path, dropTarget, setDropTarget, setDraggingCallback]);
  const onDragLeaveParent = useCallback(() => {
    setDropTarget(undefined);
    setDragging(undefined);
  }, [setDropTarget, setDragging]);
  const onDropEvent = useCallback((event, target) => {
    const data = event.dataTransfer.getData('text/plain');
    const [identifier, ...sources] = (data || '').split('|');
    setDragging(false);
    setDropTarget(undefined);
    becomeActiveCallback();
    event.preventDefault();
    event.stopPropagation();
    if (
      identifier !== undefined &&
      fileSystem &&
      +identifier !== fileSystem.identifier &&
      sources.indexOf(target) === -1 &&
      onDropCommand
    ) {
      onDropCommand(fileSystem, target, +identifier, ...sources);
    }
  }, [setDragging, setDropTarget, onDropCommand]);
  const onDrop = useCallback((event, element) => {
    event.preventDefault();
    event.stopPropagation();
    if (element) {
      onDropEvent(event, element.path);
    }
  }, [onDropEvent]);
  const onDropParent = useCallback((event) => {
    onDropEvent(event, path);
  }, [onDropEvent]);
  const onCreateDirectoryRequest = useCallback(() => {
    setCreateDirectoryDialogVisible(true);
  }, [setCreateDirectoryDialogVisible]);
  const onCancelCreateDirectory = useCallback(() => {
    setCreateDirectoryDialogVisible(false);
  }, [setCreateDirectoryDialogVisible]);
  const onCreateDirectory = useCallback((directoryName) => {
    onCommand && onCommand(Commands.createDirectory, fileSystem.joinPath(path, directoryName));
    setSelection([]);
    setCreateDirectoryDialogVisible(false);
  }, [onCommand, path, setSelection, setCreateDirectoryDialogVisible]);
  const onCopy = useCallback(() => {
    onCommand && onCommand(Commands.copy, path, selection);
    setSelection([]);
  }, [onCommand, path, selection, setSelection]);
  const onMove = useCallback(() => {
    onCommand && onCommand(Commands.move, path, selection);
    setSelection([]);
  }, [onCommand, path, selection, setSelection]);
  const onDelete = useCallback(() => {
    const description = selection.length > 1 ? `${selection.length} items` : selection[0];
    showConfirmationDialog(`Are you sure you want to delete ${description}?`)
      .then(confirmed => {
        if (confirmed) {
          onCommand && onCommand(Commands.delete, path, selection);
          setSelection([]);
          setCreateDirectoryDialogVisible(false);
        }
      });
  }, [
    onCommand,
    path,
    selection,
    setSelection,
    showConfirmationDialog,
    setCreateDirectoryDialogVisible,
  ]);
  let content;
  if (pending) {
    content = (
      <div className="spin-container">
        <Spin />
      </div>
    );
  } else if (error) {
    content = (
      <Alert type="error" showIcon message={error} />
    );
  } else {
    content = (
      <div
        className="directory-contents"
        onDragEnter={onDragOverParent}
        onDragOver={onDragOverParent}
        onDragLeave={onDragLeaveParent}
        onDrop={onDropParent}
      >
        {
          contents.map((item, index) => (
            <FileSystemElement
              key={item.path}
              element={item}
              onHover={onHover}
              onUnHover={onUnHover}
              onSelect={onSelect}
              onNavigate={onNavigate}
              onDragStart={onDragStart}
              onDragOver={onDragOver}
              onDrop={onDrop}
              hovered={hovered === item.path}
              selected={selection.indexOf(item.path) >= 0}
              dropTargetHovered={dragging && dropTarget === item.path}
            />
          ))
        }
      </div>
    );
  }
  return (
    <div
      className={className}
    >
      <div
        className={
          classNames(
            'file-system-tab-container',
            {
              active,
              'drop-target': dragging && dropTarget === path
            }
          )
        }
      >
        <div className="file-system-tab-header">
          <ConfigProvider componentSize="small">
            <Button
              disabled={!!error}
              type="primary"
              onClick={onCreateDirectoryRequest}
            >
              Create directory
            </Button>
            <Divider type="vertical" />
            <Button
              disabled={!!error || !oppositeFileSystemReady || selection.length === 0}
              onClick={onCopy}
            >
              Copy
            </Button>
            <Button
              disabled={!!error || !oppositeFileSystemReady || selection.length === 0}
              onClick={onMove}
            >
              Move
            </Button>
            <Divider type="vertical" />
            <Button
              disabled={!!error || selection.length === 0} type="danger"
              onClick={onDelete}
            >
              Delete
            </Button>
            <Divider type="vertical" />
            <Button disabled={pending} onClick={onRefresh}>
              Reload
            </Button>
          </ConfigProvider>
        </div>
        <PathNavigation
          onNavigate={onNavigate}
          path={path}
          fileSystem={fileSystem}
        />
        {content}
        <CreateDirectoryDialog
          visible={createDirectoryDialogVisible}
          onClose={onCancelCreateDirectory}
          onCreate={onCreateDirectory}
        />
      </div>
    </div>
  );
}

FileSystemTab.propTypes = {
  fileSystem: PropTypes.object,
  active: PropTypes.bool,
  becomeActive: PropTypes.func,
  error: PropTypes.string,
  contents: PropTypes.array,
  pending: PropTypes.bool,
  path: PropTypes.string,
  setPath: PropTypes.func,
  selection: PropTypes.array,
  setSelection: PropTypes.func,
  lastSelectionIndex: PropTypes.number,
  setLastSelectionIndex: PropTypes.func,
  onRefresh: PropTypes.func,
  className: PropTypes.string,
  oppositeFileSystemReady: PropTypes.bool,
  onCommand: PropTypes.func,
  dragging: PropTypes.bool,
  setDragging: PropTypes.func,
  onDropCommand: PropTypes.func,
};

export default FileSystemTab;
