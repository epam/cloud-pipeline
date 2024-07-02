import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import { message } from 'antd';
import FSItemPropType from '../file-system-item-prop-type';
import { useFileSystemIdentifier, useFileSystemKey } from '../../hooks/use-file-system';
import { useCurrentPath } from '../../hooks/use-file-system-path';
import { copy } from '../../../../operations';

function addClass(target, className) {
  if (
    !className
    || !target
    || target.classList.contains(className)
  ) {
    return;
  }
  target.classList.add(className);
}

function removeClass(target, className) {
  if (
    !className
    || !target
    || !target.classList.contains(className)
  ) {
    return;
  }
  target.classList.remove(className);
}

async function doCopy(sourceAdapter, elements, destinationAdapter, path) {
  try {
    await copy(sourceAdapter, elements, destinationAdapter, path);
    return true;
  } catch (error) {
    message.error(error.message, 5);
    return false;
  }
}

function DropTarget(
  {
    children,
    element,
    dropOverClassName,
    ...rest
  },
) {
  const key = useFileSystemKey();
  const identifier = useFileSystemIdentifier();
  const currentPath = useCurrentPath();
  const dropPath = (element && element.isDirectory) ? element.path : currentPath;
  const onDragOver = useCallback((event) => {
    const [sourceKey] = event.dataTransfer.types;
    if (sourceKey !== key) {
      addClass(event.currentTarget, dropOverClassName);
      // drop allowed
      event.stopPropagation();
      event.preventDefault();
      return false;
    }
    return true;
  }, [
    key,
    dropOverClassName,
  ]);
  const onDragLeave = useCallback((event) => {
    removeClass(event.currentTarget, dropOverClassName);
  }, [
    dropOverClassName,
  ]);
  const onDrop = useCallback((event) => {
    const [sourceKey] = event.dataTransfer.types;
    if (sourceKey !== key) {
      removeClass(event.currentTarget, dropOverClassName);
      try {
        const {
          identifier: source,
          elements,
        } = JSON.parse(event.dataTransfer.getData(sourceKey));
        (doCopy)(source, elements, identifier, dropPath);
        // eslint-disable-next-line no-empty
      } catch (_) {}
      // drop allowed
      event.stopPropagation();
      event.preventDefault();
      return false;
    }
    return true;
  }, [
    key,
    dropOverClassName,
    dropPath,
    identifier,
  ]);
  if (element && (element.isFile || element.isBackLink)) {
    // File / back link is not a drop target
    return children;
  }
  return React.cloneElement(
    children,
    {
      ...rest,
      ...(children.props || {}),
      onDragOver,
      onDragLeave,
      onDragEnter: onDragOver,
      onDragEnd: onDragLeave,
      onDrop,
    },
  );
}

DropTarget.propTypes = {
  children: PropTypes.element.isRequired,
  element: FSItemPropType,
  dropOverClassName: PropTypes.string,
};

DropTarget.defaultProps = {
  element: undefined,
  dropOverClassName: undefined,
};

export default DropTarget;
