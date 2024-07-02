import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { FileOutlined, FolderFilled, InboxOutlined } from '@ant-design/icons';
import { SplitPanelColumn } from '../../common/split-panel';
import { usePathNavigation } from './hooks/use-file-system-path';
import {
  useFileSystemElementIsSelected,
  useFileSystemElementSelectionCallback,
} from './hooks/use-file-system-selection';
import './file-system-element.css';
import FSItemPropType from './utilities/file-system-item-prop-type';
import DragElement from './utilities/drag-n-drop/drag-element';
import DropTarget from './utilities/drag-n-drop/drop-target';
import { NAME, SIZE, CHANGED } from './utilities/columns';

function FileSystemElementIcon({ element }) {
  if (!element) {
    return null;
  }
  let Icon = FileOutlined;
  if (element.isDirectory) {
    Icon = FolderFilled;
    if (element.isObjectStorage) {
      Icon = InboxOutlined;
    }
  }
  return (
    <Icon className="icon" />
  );
}

FileSystemElementIcon.propTypes = {
  element: FSItemPropType.isRequired,
};

function FileSystemElement(
  {
    className,
    style,
    element,
    hovered,
    onHover: onHoverCallback,
    even,
  },
) {
  const {
    name,
    path,
    size: realSize,
    displaySize: size = realSize,
    displayChanged: changed,
  } = element || {};
  const onHover = useCallback(() => onHoverCallback(element), [onHoverCallback, element]);
  const selected = useFileSystemElementIsSelected(element);
  const onSelect = useFileSystemElementSelectionCallback(element);
  const onUnHover = useCallback(() => onHoverCallback(undefined), [onHoverCallback]);
  const onNavigate = usePathNavigation();
  const onDoubleClick = useCallback((event) => {
    event.stopPropagation();
    event.preventDefault();
    if (element && element.isDirectory) {
      onNavigate(element.path);
    }
  }, [element, onNavigate]);
  const onClick = useCallback((event) => {
    if (element && element.isBackLink) {
      event.stopPropagation();
      event.preventDefault();
      onNavigate(element.path);
      return;
    }
    onSelect(event);
  }, [element, onNavigate, onSelect]);
  return (
    <>
      <SplitPanelColumn spread resizable={false}>
        <DropTarget element={element} dropOverClassName="drop-target">
          <DragElement element={element}>
            {/* eslint-disable-next-line jsx-a11y/mouse-events-have-key-events */}
            <div
              id={path}
              className={
                classNames(
                  className,
                  'element',
                  {
                    even,
                    hovered,
                    selected,
                  },
                )
              }
              style={style}
              onMouseOver={onHover}
              onMouseLeave={onUnHover}
              onDoubleClick={onDoubleClick}
              onClick={onClick}
            >
              {'\u00A0'}
            </div>
          </DragElement>
        </DropTarget>
      </SplitPanelColumn>
      <SplitPanelColumn
        column={NAME}
        className={
          classNames(
            'column',
            {
              hovered,
              selected,
            },
          )
        }
        resizable={false}
      >
        <FileSystemElementIcon element={element} />
        <span>{name}</span>
      </SplitPanelColumn>
      <SplitPanelColumn
        column={SIZE}
        className={
          classNames(
            'column',
            'element-size',
            {
              hovered,
              selected,
            },
          )
        }
        resizable={false}
      >
        {size || '\u00A0'}
      </SplitPanelColumn>
      <SplitPanelColumn
        column={CHANGED}
        className={
          classNames(
            'column',
            'element-changed',
            {
              hovered,
              selected,
            },
          )
        }
        resizable={false}
      >
        {changed || '\u00A0'}
      </SplitPanelColumn>
    </>
  );
}

FileSystemElement.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  element: FSItemPropType.isRequired,
  hovered: PropTypes.bool,
  onHover: PropTypes.func.isRequired,
  even: PropTypes.bool,
};

FileSystemElement.defaultProps = {
  className: undefined,
  style: undefined,
  hovered: false,
  even: false,
};

export default FileSystemElement;
