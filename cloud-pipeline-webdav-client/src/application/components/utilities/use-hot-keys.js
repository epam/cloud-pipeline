import {useCallback, useEffect} from 'react';

export default function useHotKeys (callbacks, blocked) {
  const {
    changeTab,
    clearSelection,
    moveCursor,
    moveToSelection,
    refresh,
    copy,
    move,
    remove,
    createDirectory,
  } = callbacks || {};
  const keyPress = useCallback((event) => {
    if (blocked) {
      return true;
    }
    let handled = false;
    if (event.code === 'Tab') {
      changeTab();
      handled = true;
    } else if (event.code === 'Escape') {
      clearSelection();
      handled = true;
    } else if (event.code === 'ArrowDown') {
      moveCursor(true, {ctrlKey: (event.ctrlKey || event.metaKey), shiftKey: event.shiftKey});
      handled = true;
    } else if (event.code === 'ArrowUp') {
      moveCursor(false, {ctrlKey: (event.ctrlKey || event.metaKey), shiftKey: event.shiftKey});
      handled = true;
    } else if (event.code === 'Enter') {
      moveToSelection();
      handled = true;
    } else if (event.code === 'F5') {
      copy();
      handled = true;
    } else if (event.code === 'F6') {
      move();
      handled = true;
    } else if (event.code === 'F8') {
      remove();
      handled = true;
    } else if (event.code === 'F2') {
      refresh();
      handled = true;
    } else if (event.code === 'F7') {
      createDirectory();
      handled = true;
    }
    if (handled) {
      event.stopPropagation();
      event.preventDefault();
      return false;
    }
    return true;
  }, [
    changeTab,
    clearSelection,
    moveCursor,
    moveToSelection,
    copy,
    move,
    remove,
    refresh,
    createDirectory,
    blocked,
  ]);
  useEffect(() => {
    document.addEventListener('keydown', keyPress);
    return () => {
      document.removeEventListener('keydown', keyPress);
    };
  }, [keyPress]);
}
