import React, {useCallback, useState, useEffect} from 'react';
import PropTypes from 'prop-types';
import {ipcRenderer} from 'electron';
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
import {FileSystems, initializeFileSystem} from '../../models/file-systems';
import {Commands} from '../../models/commands';
import './file-system-tab.css';

function FileSystemTab (
  {
    active,
    becomeActive,
    className,
    fileSystem,
    oppositeFileSystemReady,
    onFileSystemStatusChanged,
    onCommand,
    onPathChanged,
  }
) {
  const [fileSystemImpl, setFileSystem] = useState(undefined);
  const [path, setNewPath] = useState(undefined);
  const [pending, setPending] = useState(true);
  const [error, setError] = useState(undefined);
  const [contents, setContents] = useState([]);
  const [hovered, setHovered] = useState(undefined);
  const [selection, setSelection] = useState([]);
  const [lastSelectionIndex, setLastSelectionIndex] = useState(-1);
  const [refreshRequest, setRefreshRequest] = useState(0);
  const onRefresh = () => setRefreshRequest(refreshRequest + 1);
  const setPath = useCallback((arg) => {
    setNewPath(arg);
    onPathChanged && onPathChanged(arg);
  }, [onPathChanged, setNewPath]);
  useEffect(() => {
    ipcRenderer.on('operation-end', onRefresh);
    return () => ipcRenderer.removeListener('operation-end', onRefresh);
  }, [onRefresh]);
  useEffect(() => {
    const impl = initializeFileSystem(fileSystem);
    setPath(undefined);
    setSelection([]);
    setLastSelectionIndex(-1);
    if (impl) {
      impl.initialize()
        .then(() => {
          setFileSystem(impl);
          setError(undefined);
          setPending(false);
          setPath(undefined);
          setSelection([]);
          setLastSelectionIndex(-1);
          onFileSystemStatusChanged(true);
        })
        .catch(e => {
          setError(e);
          setPending(false);
          onFileSystemStatusChanged(false);
        })
      return () => impl.close();
    }
    return undefined;
  }, [
    fileSystem,
    setFileSystem,
    setPending,
    setPath,
    setSelection,
    setLastSelectionIndex,
    onFileSystemStatusChanged,
  ]);
  useEffect(() => {
    if (fileSystemImpl) {
      setPending(true);
      fileSystemImpl
        .getDirectoryContents(path)
        .then(contents => {
          setContents(contents);
          setPending(false);
          setSelection([]);
          setLastSelectionIndex(-1);
          onFileSystemStatusChanged(true);
        })
        .catch(e => {
          setError(e)
          setPending(false);
          onFileSystemStatusChanged(false);
        });
    }
  }, [
    fileSystemImpl,
    path,
    setPending,
    setContents,
    setSelection,
    setLastSelectionIndex,
    onFileSystemStatusChanged,
    refreshRequest,
  ]);
  const onHover = useCallback((e) => {
    const {path} = e;
    setHovered(path);
  }, [setHovered]);
  const onUnHover = useCallback(() => {
    setHovered(undefined);
  }, [setHovered]);
  const onNavigate = useCallback((newPath) => {
    setSelection([]);
    setLastSelectionIndex(-1);
    setHovered(undefined);
    setPath(newPath);
    setError(undefined);
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
  const onCreateDirectory = useCallback(() => {
    onCommand && onCommand(Commands.createDirectory, path);
    setSelection([]);
  }, [onCommand, path, setSelection]);
  const onCopy = useCallback(() => {
    onCommand && onCommand(Commands.copy, path, selection);
    setSelection([]);
  }, [onCommand, path, selection, setSelection]);
  const onMove = useCallback(() => {
    onCommand && onCommand(Commands.move, path, selection);
    setSelection([]);
  }, [onCommand, path, selection, setSelection]);
  const onDelete = useCallback(() => {
    onCommand && onCommand(Commands.delete, path, selection);
    setSelection([]);
  }, [onCommand, path, selection, setSelection]);
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
              onClick={onCreateDirectory}
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
          fileSystem={fileSystemImpl}
        />
        {content}
      </div>
    </div>
  );
}

FileSystemTab.propTypes = {
  active: PropTypes.bool,
  becomeActive: PropTypes.func,
  className: PropTypes.string,
  fileSystem: PropTypes.oneOf(Object.values(FileSystems)),
  onFileSystemStatusChanged: PropTypes.func,
  oppositeFileSystemReady: PropTypes.bool,
  onCommand: PropTypes.func,
  onPathChanged: PropTypes.func,
};

export default FileSystemTab;
