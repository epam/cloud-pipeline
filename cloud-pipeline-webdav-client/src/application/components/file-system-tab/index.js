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
  }
) {
  const [hovered, setHovered] = useState(undefined);
  const [createDirectoryDialogVisible, setCreateDirectoryDialogVisible] = useState(false);
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
    becomeActive();
  }, [becomeActive, setHovered, setPath]);
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
    becomeActive();
  }, [becomeActive, selection, setSelection, lastSelectionIndex, setLastSelectionIndex]);
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
      <div className="directory-contents">
        {
          contents.map((item, index) => (
            <FileSystemElement
              key={item.path}
              element={item}
              onHover={onHover}
              onUnHover={onUnHover}
              onSelect={onSelect}
              onNavigate={onNavigate}
              hovered={hovered === item.path}
              selected={selection.indexOf(item.path) >= 0}
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
      <div className={classNames('file-system-tab-container', {active})}>
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
};

export default FileSystemTab;
