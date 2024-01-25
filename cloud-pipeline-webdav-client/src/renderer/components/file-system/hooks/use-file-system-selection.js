import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { FileSystemPathContext, usePathNavigation } from './use-file-system-path';
import { useFileSystemContents } from './use-file-system-contents';
import { useFileSystemSetActive } from './use-file-system';
import useFileSystemHotKeys from './use-file-system-hot-keys';

const FileSystemSelectionContext = React.createContext({});

/**
 * Takes `last` element from the `currentSelection` and returns the elements from
 * `items` withing [`last` - `element`] range; `last`
 * (or current `element` if `currentSelection` is empty)
 * will be put at the end of the resulted list for "continuous" spread selection support.
 * @param {FSItem} element
 * @param {string[]} currentSelection
 * @param {FSItem[]} items
 * @returns {string[]}
 */
function spreadSelection(element, currentSelection = [], items = []) {
  if (!element) {
    return currentSelection;
  }
  const lastSelection = currentSelection.slice().pop();
  const previousIndex = items.findIndex((o) => o.path === lastSelection);
  const currentIndex = items.findIndex((o) => o.path === element.path);
  const from = Math.min(previousIndex, currentIndex);
  const to = Math.max(previousIndex, currentIndex);
  const newSelection = items.slice(
    Math.max(0, from),
    Math.min(items.length, to + 1),
  ).map((o) => o.path);
  const lastElement = lastSelection || element.path;
  return [
    ...newSelection.filter((o) => o !== lastElement),
    lastElement,
  ];
}

function modifySelection(
  element,
  currentSelection = [],
  items = [],
  append = false,
  spread = false,
) {
  if (!element || !element.path) {
    return [];
  }
  const exists = currentSelection.some((o) => o === element.path);
  const currentSelectionWithoutElement = currentSelection.filter((o) => o !== element.path);
  if (append && exists) {
    // remove
    return currentSelectionWithoutElement;
  }
  if (append && !exists) {
    // append
    return [...currentSelectionWithoutElement, element.path];
  }
  if (spread) {
    return spreadSelection(
      element,
      currentSelection,
      items,
    );
  }
  if (exists) {
    // !append && !spread && exists
    return [];
  }
  // !append && !spread && !exists
  return [element.path];
}

function moveSelection(
  direction,
  currentSelection = [],
  items = [],
  spread = false,
) {
  if (!direction || items.length === 0) {
    return currentSelection;
  }
  const lastSelection = currentSelection.slice().pop();
  let lastIndex = items.findIndex((o) => o.path === lastSelection);
  if (lastIndex === -1) {
    lastIndex = (direction > 0 ? -1 : 0);
  }
  const newIndex = (lastIndex + direction + items.length) % items.length;
  const element = items[newIndex];
  if (spread) {
    const [
      initialSelectionElement,
      ...other
    ] = spreadSelection(
      element,
      currentSelection.slice().reverse(),
      items,
    ).reverse();
    return [
      ...[initialSelectionElement, ...other].filter((o) => o !== element.path),
      element.path,
    ].filter(Boolean);
  }
  return [element.path];
}

function useFileSystemElementSelectionCallback(element) {
  const {
    onSelect,
  } = useContext(FileSystemSelectionContext);
  const items = useFileSystemContents();
  return useCallback((event) => {
    onSelect((selection) => modifySelection(
      element,
      selection,
      items,
      (event?.ctrlKey || event?.metaKey),
      event?.shiftKey,
    ));
  }, [onSelect, element, items]);
}

function useMoveEvent(direction, onSelect) {
  const items = useFileSystemContents();
  return useCallback((event) => {
    onSelect((selection) => moveSelection(
      direction,
      selection,
      items,
      event?.shiftKey,
    ));
  }, [onSelect, direction, items]);
}

function useEnterEvent(selection) {
  const items = useFileSystemContents();
  const onChangePath = usePathNavigation();
  return useCallback((event) => {
    const lastPath = (selection || []).slice().pop();
    const item = (items || []).find((o) => o.path === lastPath);
    if (item && item.isDirectory) {
      event.stopPropagation();
      onChangePath(item.path);
    }
  }, [
    selection,
    items,
    onChangePath,
  ]);
}

function useFileSystemElementIsSelected(element) {
  const {
    selection = [],
  } = useContext(FileSystemSelectionContext);
  if (!element) {
    return false;
  }
  return selection.some((o) => o === element.path);
}

function useFileSystemSelection() {
  const {
    selection,
  } = useContext(FileSystemSelectionContext);
  const items = useFileSystemContents();
  return useMemo(
    () => (items || [])
      .filter((item) => !item.isBackLink && selection.includes(item.path))
      .map((item) => item.path),
    [items, selection],
  );
}

function useFileSystemClearSelection() {
  const {
    clearSelection,
  } = useContext(FileSystemSelectionContext);
  return clearSelection;
}

export {
  FileSystemSelectionContext,
  useFileSystemElementSelectionCallback,
  useFileSystemElementIsSelected,
  useFileSystemSelection,
  useFileSystemClearSelection,
};

export default function useFileSystemSelectionStore(onChange) {
  const {
    identifier,
    path,
  } = useContext(FileSystemPathContext);
  const [selection, setSelection] = useState([]);
  const setActive = useFileSystemSetActive();
  const onChangeSelection = useCallback((newSelection = []) => {
    if (newSelection.length > 0) {
      setActive();
    }
    setSelection(newSelection);
  }, [setActive, setSelection]);
  const clearSelection = useCallback(() => setSelection([]), [setSelection]);
  const moveDown = useMoveEvent(1, setSelection);
  const moveUp = useMoveEvent(-1, setSelection);
  const enter = useEnterEvent(selection);
  const hotKeys = useMemo(() => ({
    escape: clearSelection,
    arrowDown: moveDown,
    arrowUp: moveUp,
    enter,
  }), [
    clearSelection,
    moveDown,
    moveUp,
    enter,
  ]);
  useFileSystemHotKeys(hotKeys);
  useEffect(() => {
    setSelection([]);
  }, [path, identifier, setSelection]);
  useEffect(() => {
    if (typeof onChange === 'function') {
      onChange(selection);
    }
  }, [
    onChange,
    selection,
    clearSelection,
  ]);
  return useMemo(() => ({
    selection,
    onSelect: onChangeSelection,
    clearSelection,
  }), [selection, onChangeSelection, clearSelection]);
}
