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
