import {
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react';
import { message } from 'antd';
import {
  useFileSystemIdentifier,
  useFileSystemInitialized,
  useFileSystemRestricted,
  useOtherFileSystemPath
} from './use-file-system';
import { useCurrentPath } from './use-file-system-path';
import { useFileSystemClearSelection, useFileSystemSelection } from './use-file-system-selection';
import useFileSystemHotKeys from './use-file-system-hot-keys';
import { FileSystemContentsContext, useFileSystemContents } from './use-file-system-contents';
import {
  createDirectory,
  copy,
  move,
  remove,
} from '../../../operations';

function useCopyMoveSource() {
  const otherFileSystem = useOtherFileSystemPath();
  const identifier = useFileSystemIdentifier();
  const selection = useFileSystemSelection();
  const {
    identifier: destinationFileSystem,
    path: destination,
  } = otherFileSystem || {};
  return useMemo(() => ({
    sourceFileSystem: identifier,
    sources: selection,
    destinationFileSystem,
    destination,
  }), [
    identifier,
    selection,
    destinationFileSystem,
    destination,
  ]);
}

/**
 * @typedef {Object} OperationInfo
 * @property {boolean} allowed
 * @property {function} operation
 */

/**
 * @param {function: Promise<*>} operation
 * @param {boolean} allowed
 * @param {string} [hotKey]
 */
function useOperation(operation, allowed, hotKey = undefined) {
  const [pending, setPending] = useState(false);
  const fn = useCallback(() => {
    if (typeof operation === 'function') {
      setPending(true);
      const promiseLike = operation();
      if (promiseLike && typeof promiseLike.then === 'function') {
        promiseLike.then(() => setPending(false));
      } else {
        setPending(false);
      }
    }
  }, [setPending, operation]);
  const hotKeys = useMemo(() => {
    if (!hotKey || !allowed || !fn) {
      return undefined;
    }
    return {
      [hotKey]: fn,
    };
  }, [
    hotKey,
    allowed,
    fn,
  ]);
  useFileSystemHotKeys(hotKeys);
  return useMemo(() => ({
    operation: fn,
    allowed: allowed && !pending,
  }), [fn, allowed, pending]);
}

async function invokeOperation(asyncFn, onSuccess) {
  try {
    if (typeof asyncFn === 'function') {
      await asyncFn();
    }
    if (typeof onSuccess === 'function') {
      onSuccess();
    }
  } catch (error) {
    // submit operation error
    message.error(error.message, 5);
  }
}

/**
 * @param {string} [hotKey]
 * @returns {OperationInfo}
 */
export function useCreateDirectoryOperation(hotKey = undefined) {
  const initialized = useFileSystemInitialized();
  const identifier = useFileSystemIdentifier();
  const path = useCurrentPath();
  const restricted = useFileSystemRestricted();
  const operation = useCallback(
    () => invokeOperation(
      () => createDirectory(identifier, path),
    ),
    [identifier, path],
  );
  return useOperation(
    operation,
    !!path && identifier && initialized && !restricted,
    hotKey,
  );
}

/**
 * @param {string} [hotKey]
 * @returns {OperationInfo}
 */
export function useCopyOperation(hotKey = undefined) {
  const copyMoveSource = useCopyMoveSource();
  const clearSelection = useFileSystemClearSelection();
  const initialized = useFileSystemInitialized();
  const restricted = useFileSystemRestricted();
  const {
    sourceFileSystem,
    sources,
    destinationFileSystem,
    destination,
  } = copyMoveSource;
  const operation = useCallback(() => invokeOperation(
    () => copy(sourceFileSystem, sources, destinationFileSystem, destination),
    clearSelection,
  ), [
    sourceFileSystem,
    sources,
    destinationFileSystem,
    destination,
    clearSelection,
  ]);
  return useOperation(
    operation,
    initialized
    && !restricted
    && !!sourceFileSystem
    && !!destinationFileSystem
    && (sources || []).length > 0
    && !!destination,
    hotKey,
  );
}

function useRemovableItems(sources) {
  const initialized = useFileSystemInitialized();
  const items = useFileSystemContents();
  return useMemo(() => {
    if (!initialized || (sources || []).length === 0) {
      return [];
    }
    return items
      .filter((anItem) => anItem.removable === undefined || anItem.removable)
      .filter((anItem) => sources.includes(anItem.path))
      .map((anItem) => anItem.path);
  }, [items, sources, initialized]);
}

/**
 * @param {string} [hotKey]
 * @returns {OperationInfo}
 */
export function useMoveOperation(hotKey = undefined) {
  const copyMoveSource = useCopyMoveSource();
  const clearSelection = useFileSystemClearSelection();
  const initialized = useFileSystemInitialized();
  const restricted = useFileSystemRestricted();
  const {
    sourceFileSystem,
    sources,
    destinationFileSystem,
    destination,
  } = copyMoveSource;
  const movableItems = useRemovableItems(sources);
  const operation = useCallback(() => invokeOperation(
    () => move(sourceFileSystem, movableItems, destinationFileSystem, destination),
    clearSelection,
  ), [
    sourceFileSystem,
    movableItems,
    destinationFileSystem,
    destination,
    clearSelection,
  ]);
  return useOperation(
    operation,
    initialized
    && !restricted
    && !!sourceFileSystem
    && !!destinationFileSystem
    && (movableItems || []).length > 0
    && !!destination,
    hotKey,
  );
}

/**
 * @param {string} [hotKey]
 * @returns {OperationInfo}
 */
export function useRemoveOperation(hotKey = undefined) {
  const identifier = useFileSystemIdentifier();
  const selection = useFileSystemSelection();
  const restricted = useFileSystemRestricted();
  const removableItems = useRemovableItems(selection);
  const operation = useCallback(
    () => invokeOperation(() => remove(identifier, removableItems)),
    [identifier, removableItems],
  );
  return useOperation(
    operation,
    removableItems.length > 0 && !restricted,
    hotKey,
  );
}

/**
 * @param {string} [hotKey]
 * @returns {OperationInfo}
 */
export function useRefreshOperation(hotKey = undefined) {
  const {
    pending,
    onRefresh,
  } = useContext(FileSystemContentsContext);
  const clearSelection = useFileSystemClearSelection();
  const operation = useCallback(() => {
    onRefresh();
    clearSelection();
  }, [
    onRefresh,
    clearSelection,
  ]);
  return useOperation(
    operation,
    !pending,
    hotKey,
  );
}
