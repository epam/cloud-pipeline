import React, {useCallback, useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {FolderOutlined, FileOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import './file-system-element.css';

function FileSystemElement (
  {
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
  const Icon = element.isDirectory ? FolderOutlined : FileOutlined;
  return (
    <div
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
      <Icon className="icon" />
      <div className="element-name-container">
        <span className="element-name">{element.name}</span>
      </div>
    </div>
  );
}

FileSystemElement.propTypes = {
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
}

export default React.memo(FileSystemElement);
