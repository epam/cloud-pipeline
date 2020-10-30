import React, {useCallback, useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {FolderFilled, FileOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import displaySize from './display-size';
import displayDate from './display-date';
import {PANEL_PADDING, RESIZER_SIZE} from '../utilities/split-panel';
import './file-system-element.css';
import '../utilities/split-panel.css';

function FileSystemElementIcon ({element}) {
  const Icon = element.isDirectory ? FolderFilled : FileOutlined;
  return (
    <Icon className="icon" />
  );
}

function getElementIdentifier(tabIdentifier, element) {
  return `${tabIdentifier}-${element.path.replace(/[\/\\]/g, '-')}`;
}

function FileSystemElement (
  {
    tabIdentifier,
    element,
    onHover,
    onUnHover,
    hovered,
    selected,
    dropTargetHovered,
    onSelect,
    onNavigate,
    onDragStart,
    onDragOver,
    onDrop,
    columnSizes,
    resizerSize = RESIZER_SIZE
  },
) {
  const [elementIsHovered, setElementIsHovered] = useState(false);
  useEffect(() => {
    setElementIsHovered(hovered);
  }, [hovered]);
  const onElementHover = () => {
    if (!elementIsHovered) {
      setElementIsHovered(true);
      onHover && onHover(element);
    }
  }
  const onElementUnHover = () => {
    if (elementIsHovered) {
      setElementIsHovered(false);
      onUnHover && onUnHover(element);
    }
  }
  const navigate = useCallback(() => {
    if (element && element.isDirectory && onNavigate) {
      onNavigate(element.path);
    }
  }, [onNavigate]);
  const onMouseDown = (e) => {
    e && e.stopPropagation();
    e && e.stopPropagation();
    if (element && element.isBackLink) {
      onNavigate && onNavigate(element.path);
    } else {
      onSelect && onSelect(element, e ? (e.ctrlKey || e.metaKey) : false, e ? e.shiftKey : false);
    }
  };
  const onDragStartEvent = (e) => {
    onDragStart && onDragStart(e, element);
  };
  const onDragOverEvent = (e) => {
    onDragOver && onDragOver(e, element);
  };
  const onDragLeaveEvent = (e) => {
    onDragOver && onDragOver(e);
  };
  const onDropEvent = (e) => {
    onDrop && onDrop(e, element);
  };
  if (!element) {
    return null;
  }
  return (
    <div
      id={getElementIdentifier(tabIdentifier, element)}
      className={
        classNames(
          'element',
          {
            'folder': element.isDirectory,
            'file': element.isFile,
            'link': element.isSymbolicLink,
            'selected': selected,
            'hovered': elementIsHovered,
            'drop-target': dropTargetHovered,
          }
        )
      }
      onMouseOver={onElementHover}
      onMouseLeave={onElementUnHover}
      onClick={onMouseDown}
      onDoubleClick={navigate}
      draggable={!element.isBackLink}
      onDragStart={onDragStartEvent}
      onDragEnter={onDragOverEvent}
      onDragOver={onDragOverEvent}
      onDragLeave={onDragLeaveEvent}
      onDrop={onDropEvent}
    >
      <div
        className="column"
        style={{
          width: (columnSizes || [])[0] || 0,
          padding: PANEL_PADDING
        }}
      >
        <FileSystemElementIcon element={element} />
        <div className="element-name-container">
          <span className="element-name">{element.name}</span>
        </div>
        <div
          className="split-panel-fake-resizer"
          style={{
            cursor: 'initial',
            width: resizerSize + 4,
            right: -(resizerSize / 2.0 + 2)
          }}
        >
          <div
            className="border"
            style={{
              width: 2,
              borderWidth: resizerSize
            }}
          >
            {'\u00A0'}
          </div>
        </div>
      </div>
      <div
        className="column no-wrap-container"
        style={{
          width: (columnSizes || [])[1] || 0,
          padding: PANEL_PADDING
        }}
      >
        <span className="element-size">
          {displaySize(element.size) || '\u00A0'}
        </span>
        <div
          className="split-panel-fake-resizer"
          style={{
            cursor: 'initial',
            width: resizerSize + 4,
            right: -(resizerSize / 2.0 + 2)
          }}
        >
          <div
            className="border"
            style={{
              width: 2,
              borderWidth: resizerSize
            }}
          >
            {'\u00A0'}
          </div>
        </div>
      </div>
      <div
        className="column no-wrap-container"
        style={{
          width: (columnSizes || [])[2] || 0,
          padding: PANEL_PADDING
        }}
      >
        <span className="element-changed">
          {displayDate(element.changed) || '\u00A0'}
        </span>
      </div>
    </div>
  );
}

FileSystemElement.propTypes = {
  tabIdentifier: PropTypes.string,
  element: PropTypes.object,
  onHover: PropTypes.func,
  onUnHover: PropTypes.func,
  onSelect: PropTypes.func,
  onNavigate: PropTypes.func,
  onDragStart: PropTypes.func,
  onDragOver: PropTypes.func,
  onDrop: PropTypes.func,
  hovered: PropTypes.bool,
  selected: PropTypes.bool,
  dropTargetHovered: PropTypes.bool,
  columnSizes: PropTypes.array
}

export {getElementIdentifier}
export default React.memo(FileSystemElement);
