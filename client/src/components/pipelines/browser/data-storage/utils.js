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
 * Whether a row of the storage listing carries a selection checkbox, and
 * whether "Select page" picks it up - the two places that must agree, because
 * a row selected in bulk but with no checkbox of its own cannot be unselected.
 *
 * `selectable` is the row's own veto (the `..` navigation row, a version child
 * row, a delete-marker). Past it, a row is only worth selecting when at least
 * one of the selection actions could act on it:
 *
 * - `downloadable` - "Download"; false for every folder on a non-Omics storage
 * - `editable` - "Remove all" and the rest of the write actions
 * - `shareAvailable` - "Share"
 * - `archiveSelectable` - "Download archive" (`archiveSelectionAvailable` in
 *   `components/special/download-storage-archive.js`). Folders only, and the
 *   one flag that makes a folder selectable for a user who cannot write to the
 *   storage.
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
