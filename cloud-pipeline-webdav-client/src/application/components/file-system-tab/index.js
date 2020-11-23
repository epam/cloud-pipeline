import React, {
  useCallback,
  useLayoutEffect,
  useState,
} from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  ConfigProvider,
  Divider,
  Spin,
} from 'antd';
import {CaretUpOutlined, CaretDownOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import SplitPanel, {useSplitPanel}  from '../utilities/split-panel';
import FileSystemElement, {getElementIdentifier} from './file-system-element';
import PathNavigation from './path-navigation';
import {SortingProperty, PropertySorters} from './sorting';
import './file-system-tab.css';
import './file-system-element.css';

function SortingIcon ({sorting, property}) {
  const index = PropertySorters[property].indexOf(sorting);
  if (index === -1) {
    return (
      <span>{'\u00A0'}</span>
    );
  }
  if (index === 0) {
    return (
      <CaretDownOutlined />
    );
  }
  return (
    <CaretUpOutlined />
  );
}

function FileSystemTab (
  {
    identifier,
    fileSystem,
    active,
    becomeActive,
    error,
    contents,
    pending,
    path,
    setPath,
    selection,
    selectItem,
    onRefresh,
    className,
    oppositeFileSystemReady,
    copyAllowed,
    moveAllowed,
    removeAllowed,
    onRemove,
    onCopy,
    onMove,
    onCreateDirectory,
    dragging,
    setDragging,
    onDropCommand,
    sorting,
    setSorting,
    lastSelectionIndex,
  }
) {
  const [columnSizes, onSetColumnSizes] = useSplitPanel([undefined, 100, 150]);
  const [hovered, setHovered] = useState(undefined);
  const [dropTarget, setDropTarget] = useState(undefined);
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
    selectItem(selectedPath, {ctrlKey, shiftKey});
    becomeActiveCallback();
  }, [becomeActiveCallback, selectItem,]);
  const onDragStart = useCallback((event, element) => {
    if (element && !element.isBackLink && fileSystem) {
      const {path: elementPath} = element;
      const selected = (selection || []).indexOf(elementPath) >= 0;
      let itemsToDrag = [];
      if (selected) {
        itemsToDrag = selection.slice();
      } else {
        selectItem(elementPath);
        itemsToDrag = [elementPath];
      }
      const data = [fileSystem.identifier, ...itemsToDrag].join('|');
      event.dataTransfer.setData('text/plain', data);
      event.dataTransfer.effectAllowed = 'copy';
      const image = document.getElementById('drag-and-drop');
      if (itemsToDrag.length > 1) {
        image.innerText = `Copy ${itemsToDrag.length} elements`;
      } else {
        image.innerText = `Copy ${itemsToDrag[0]}`;
      }
      event.dataTransfer.setDragImage(image, 0, 0);
      setDraggingCallback();
      becomeActiveCallback();
    } else {
      event.preventDefault();
    }
  }, [becomeActiveCallback, fileSystem, selection, selectItem, setDraggingCallback]);
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
  }, [onDropEvent, path]);
  const onNameClicked = useCallback((e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    setSorting(SortingProperty.name);
    return false;
  }, [setSorting]);
  const onSizeClicked = useCallback((e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    setSorting(SortingProperty.size);
    return false;
  }, [setSorting]);
  const onChangedClicked = useCallback((e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    setSorting(SortingProperty.changed);
    return false;
  }, [setSorting]);
  useLayoutEffect(() => {
    if (lastSelectionIndex >= 0) {
      const item = contents[lastSelectionIndex];
      if (item) {
        const element = document.getElementById(getElementIdentifier(identifier, item));
        if (element) {
          element.scrollIntoView({
            behavior: 'auto',
            block: 'nearest',
            inline: 'start',
          });
        }
      }
    }
  }, [lastSelectionIndex, contents])
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
              tabIdentifier={identifier}
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
              columnSizes={columnSizes}
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
              onClick={onCreateDirectory}
            >
              Create directory (F7)
            </Button>
            <Divider type="vertical" />
            <Button
              disabled={!!error || !oppositeFileSystemReady || !copyAllowed}
              onClick={onCopy}
            >
              Copy (F5)
            </Button>
            <Button
              disabled={!!error || !oppositeFileSystemReady || !moveAllowed}
              onClick={onMove}
            >
              Move (F6)
            </Button>
            <Divider type="vertical" />
            <Button
              disabled={!!error || !removeAllowed}
              type="danger"
              onClick={onRemove}
            >
              Delete (F8)
            </Button>
            <Divider type="vertical" />
            <Button disabled={pending} onClick={onRefresh}>
              Reload (F2)
            </Button>
          </ConfigProvider>
        </div>
        <PathNavigation
          onNavigate={onNavigate}
          path={path}
          fileSystem={fileSystem}
        />
        <div className="directory-contents-container">
          <SplitPanel
            className="directory-contents-header"
            sizes={columnSizes}
            onChange={onSetColumnSizes}
            main
            fadeOnResize
          >
            <div
              className="element-header"
              onClick={onNameClicked}
            >
              <span>Name</span>
              <SortingIcon
                sorting={sorting}
                property={SortingProperty.name}
              />
            </div>
            <div
              className="element-header"
              onClick={onSizeClicked}
            >
              <span>Size</span>
              <SortingIcon
                sorting={sorting}
                property={SortingProperty.size}
              />
            </div>
            <div
              className="element-header"
              onClick={onChangedClicked}
            >
              <span>Modified</span>
              <SortingIcon
                sorting={sorting}
                property={SortingProperty.changed}
              />
            </div>
          </SplitPanel>
          {content}
        </div>
      </div>
    </div>
  );
}

FileSystemTab.propTypes = {
  identifier: PropTypes.string,
  fileSystem: PropTypes.object,
  active: PropTypes.bool,
  becomeActive: PropTypes.func,
  error: PropTypes.string,
  contents: PropTypes.array,
  pending: PropTypes.bool,
  path: PropTypes.string,
  setPath: PropTypes.func,
  selection: PropTypes.array,
  selectItem: PropTypes.func,
  lastSelectionIndex: PropTypes.number,
  setLastSelectionIndex: PropTypes.func,
  onRefresh: PropTypes.func,
  className: PropTypes.string,
  oppositeFileSystemReady: PropTypes.bool,
  copyAllowed: PropTypes.bool,
  moveAllowed: PropTypes.bool,
  removeAllowed: PropTypes.bool,
  onRemove: PropTypes.func,
  onCopy: PropTypes.func,
  onMove: PropTypes.func,
  onCreateDirectory: PropTypes.func,
  dragging: PropTypes.bool,
  setDragging: PropTypes.func,
  onDropCommand: PropTypes.func,
  sorting: PropTypes.string,
  setSorting: PropTypes.func,
};

export default React.memo(FileSystemTab);
