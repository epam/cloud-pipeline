/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const STORAGE_OPERATIONS_METADATA_KEYS = [
  'Storage operations',
  'StorageOperations',
  'storage_operations'
];

const FALSY_STORAGE_OPERATION_VALUES = new Set([
  'none',
  "'none'",
  '"none"',
  'false',
  "'false'",
  '"false"'
]);

function getMetadataEntryValueString (entry) {
  if (entry == null) {
    return undefined;
  }
  const raw = typeof entry === 'object' && entry !== null && 'value' in entry
    ? entry.value
    : entry;
  if (!raw) {
    return undefined;
  }
  return String(raw).trim().toLowerCase();
}

/**
 * Whether a row can be selected: at least one selection action works on it.
 *
 * @param {{selectable: boolean, downloadable: boolean, editable: boolean,
 *   shareAvailable: boolean, archiveSelectable: boolean}} item
 * @returns {boolean}
 */
export function storageItemSelectionAvailable (item) {
  if (!item || !item.selectable) {
    return false;
  }
  return !!(
    item.downloadable ||
    item.editable ||
    item.shareAvailable ||
    item.archiveSelectable
  );
}

/**
 * Whether "Select page" has anything to do: a row that can be selected, and
 * is not selected yet.
 *
 * @param {Object[]} items the rows as rendered, the ".." row included
 * @param {function(Object): boolean} isSelected
 * @returns {boolean}
 */
export function selectPageAvailable (items, isSelected) {
  return (items || []).some(
    (item) => storageItemSelectionAvailable(item) && !isSelected(item)
  );
}

/**
 * Whether the user may download from the storage: it is not sensitive, and
 * its download attribute allows this user.
 *
 * @param {{sensitive: boolean}} info
 * @param {boolean} downloadEnabled
 * @returns {boolean}
 */
export function checkStorageDownloadAllowed (info, downloadEnabled) {
  return !!info && !info.sensitive && !!downloadEnabled;
}

export const MIN_DOWNLOAD_ITEMS = 1;
export const MAX_DOWNLOAD_ITEMS = 3;

/**
 * Whether the plain "Download" selection item is offered for `count` files:
 * at least `MIN_DOWNLOAD_ITEMS`, and, where "Download archive" is offered, at
 * most `MAX_DOWNLOAD_ITEMS`.
 * @param {number} count
 * @param {{archiveSupported: boolean}} [options] whether "Download archive"
 * is offered on this storage
 * @returns {boolean}
 */
export function downloadItemsActionAvailable (count, options = {}) {
  const {archiveSupported = true} = options;
  if (count < MIN_DOWNLOAD_ITEMS) {
    return false;
  }
  return !archiveSupported || count <= MAX_DOWNLOAD_ITEMS;
}

/**
 * The selected items "Remove" deletes: the ones the user may write to.
 *
 * @param {{editable: boolean}[]} selectedItems
 * @returns {{editable: boolean}[]}
 */
export function removableStorageItems (selectedItems) {
  return (selectedItems || []).filter((item) => item && item.editable);
}

function isPartialSelection (selectedCount, count) {
  return count > 0 && count < selectedCount;
}

/**
 * The hint for "Remove" when only some of the selected items will be removed.
 *
 * @param {number} selectedCount
 * @param {number} removableCount
 * @returns {string|undefined} nothing when all of them, or none, will be removed
 */
export function partialRemoveHint (selectedCount, removableCount) {
  if (!isPartialSelection(selectedCount, removableCount)) {
    return undefined;
  }
  return 'Only items you have write access to will be removed ' +
    `(${removableCount} of ${selectedCount}).`;
}

/**
 * The selected items "Generate URL" works on.
 *
 * @param {{downloadable: boolean}[]} selectedItems
 * @returns {{downloadable: boolean}[]}
 */
export function downloadableStorageItems (selectedItems) {
  return (selectedItems || []).filter((item) => item && item.downloadable);
}

/**
 * The hint for "Generate URL" when only some of the selected items get a URL.
 *
 * @param {number} selectedCount
 * @param {number} downloadableCount
 * @returns {string|undefined} nothing when all of them, or none, get a URL
 */
export function partialGenerateUrlHint (selectedCount, downloadableCount) {
  if (!isPartialSelection(selectedCount, downloadableCount)) {
    return undefined;
  }
  return 'URLs are generated only for downloadable files ' +
    `(${downloadableCount} of ${selectedCount}).`;
}

/**
 * The selected items "Restore transferred items" works on: files in an
 * archive class, and folders the user may write to.
 *
 * @param {{type: string, editable: boolean}[]} selectedItems
 * @param {function(Object): boolean} isArchivedFile
 * @returns {{type: string, editable: boolean}[]}
 */
export function restorableStorageItems (selectedItems, isArchivedFile) {
  return (selectedItems || []).filter((item) => item && (
    isArchivedFile(item) ||
    (item.type === 'Folder' && item.editable)
  ));
}

/**
 * The confirm title for "Remove".
 *
 * @param {number} selectedCount
 * @param {number} removableCount
 * @returns {string}
 */
export function removeConfirmTitle (selectedCount, removableCount) {
  if (removableCount < selectedCount) {
    return `Remove ${removableCount} of ${selectedCount} selected items?`;
  }
  return 'Remove all selected items?';
}

export function checkStorageOperationsEnabled (metadata = {}) {
  if (!metadata?.data) {
    return true;
  }
  const {data} = metadata;
  for (const key of STORAGE_OPERATIONS_METADATA_KEYS) {
    if (!Object.prototype.hasOwnProperty.call(data, key)) {
      continue;
    }
    const value = getMetadataEntryValueString(data[key]);
    if (!value) {
      continue;
    }
    if (FALSY_STORAGE_OPERATION_VALUES.has(value)) {
      return false;
    }
    return true;
  }
  return true;
}
