import React, { useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import { useFileSystemIdentifier, useFileSystemKey, useFileSystemRestricted } from '../../hooks/use-file-system';
import { useFileSystemElementIsSelected, useFileSystemSelection } from '../../hooks/use-file-system-selection';
import FSItemPropType from '../file-system-item-prop-type';
import './drag-n-drop.css';

function useDragData(element) {
  const key = useFileSystemKey();
  const identifier = useFileSystemIdentifier();
  const restricted = useFileSystemRestricted();
  const isSelected = useFileSystemElementIsSelected(element);
  const selection = useFileSystemSelection();
  const dragItems = useMemo(
    () => (isSelected ? selection : [element.path]),
    [isSelected, selection, element],
  );
  return useMemo(() => (restricted ? undefined : {
    key,
    identifier,
    elements: dragItems,
  }), [key, identifier, dragItems, restricted]);
}

const image = document.createElement('div');
image.classList.add('drag-and-drop');
document.body.appendChild(image);

function DragElement(
  {
    children,
    element,
    ...rest
  },
) {
  const dragData = useDragData(element);
  const restricted = useFileSystemRestricted();
  const onDragStart = useCallback((event) => {
    if (dragData) {
      const { key } = dragData;
      event.dataTransfer.setData(key, JSON.stringify(dragData));
      // eslint-disable-next-line no-param-reassign
      event.dataTransfer.effectAllowed = 'copy';
      if (image) {
        if (dragData.elements.length > 1) {
          image.innerText = `Copy ${dragData.elements.length} elements`;
        } else {
          image.innerText = `Copy ${dragData.elements[0]}`;
        }
      }
      event.dataTransfer.setDragImage(image, 0, 0);
    }
  }, [dragData]);
  if (!element) {
    return children;
  }
  const {
    isBackLink,
  } = element;
  if (isBackLink) {
    return children;
  }
  return React.cloneElement(
    children,
    {
      ...rest,
      ...(children.props || {}),
      draggable: !restricted,
      onDragStart: restricted ? undefined : onDragStart,
    },
  );
}

DragElement.propTypes = {
  children: PropTypes.element.isRequired,
  element: FSItemPropType.isRequired,
};

export default DragElement;
