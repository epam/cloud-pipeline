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

/**
 * @typedef {Object} SelectedStorageItem
 * @property {string} [path]
 * @property {string} [type]
 * @property {boolean} [deleteMarker]
 */

import moment from 'moment-timezone';
import {message} from 'antd';

function isFolder (item) {
  return /^folder$/i.test((item && item.type) || '');
}

/**
 * Whether an item can go into an archive: it has a `path` and is not a
 * delete marker.
 *
 * @param {SelectedStorageItem} item
 * @returns {boolean}
 */
function isArchivableItem (item) {
  return !!item && !!item.path && !item.deleteMarker;
}

/**
 * @param {SelectedStorageItem[]} selectedItems
 * @returns {SelectedStorageItem[]}
 */
function archivableItems (selectedItems) {
  return (selectedItems || []).filter(isArchivableItem);
}

/**
 * Whether "Download archive" may be offered on this storage, to this user.
 *
 * @param {{archiveSupported: boolean, downloadAllowed: boolean}} [options]
 *   `archiveSupported` - archives are supported for this storage;
 *   `downloadAllowed` - the user may download from it
 * @returns {boolean}
 */
export function archiveDownloadAvailable (options = {}) {
  const {
    archiveSupported = false,
    downloadAllowed = false
  } = options;
  return archiveSupported && downloadAllowed;
}

/**
 * Whether an item can be selected for an archive: it is an archivable folder,
 * and "Download archive" is offered on this storage, to this user.
 *
 * @param {SelectedStorageItem} item
 * @param {{archiveSupported: boolean, downloadAllowed: boolean}} [options]
 *   the same as for `archiveDownloadAvailable`
 * @returns {boolean}
 */
export function archiveSelectionAvailable (item, options = {}) {
  return archiveDownloadAvailable(options) &&
    isFolder(item) &&
    isArchivableItem(item);
}

/**
 * The `paths` of `POST /datastorage/{id}/download/archive` for the selected
 * files and folders. A folder's path ends with `/`.
 *
 * @param {SelectedStorageItem[]} selectedItems
 * @returns {string[]}
 */
export function archivePathsFromSelection (selectedItems) {
  const paths = archivableItems(selectedItems)
    .map(item => {
      const {path} = item;
      if (isFolder(item)) {
        return path.endsWith('/') ? path : `${path}/`;
      }
      return path;
    });
  return [...new Set(paths)];
}

/**
 * Whether archives are supported for this storage: an S3 storage, or an NFS
 * storage on a share mounted as NFS.
 *
 * @param {{storageType: string, type: string}} storageInfo
 * @param {{mountType: string}} [fileShareMount] the share an NFS storage is on
 * @returns {boolean}
 */
export function archiveStorageSupported (storageInfo, fileShareMount) {
  if (!storageInfo) {
    return false;
  }
  const type = storageInfo.storageType || storageInfo.type || '';
  if (/^s3$/i.test(type)) {
    return true;
  }
  return /^nfs$/i.test(type) &&
    !!fileShareMount &&
    /^nfs$/i.test(fileShareMount.mountType || '');
}

/**
 * Whether the "Download archive" selection item is offered for this
 * selection: two or more archivable items, or one archivable folder.
 *
 * @param {SelectedStorageItem[]} selectedItems
 * @returns {boolean}
 */
export function archiveActionAvailable (selectedItems) {
  const items = archivableItems(selectedItems);
  if (items.length === 0) {
    return false;
  }
  return items.length > 1 || isFolder(items[0]);
}

// The characters allowed in an archive name.
const NAME_CHARACTERS = '\\w\\-. ';
const ILLEGAL_NAME_CHARACTERS = new RegExp(`[^${NAME_CHARACTERS}]+`, 'g');
const SAFE_ARCHIVE_NAME = new RegExp(`^[${NAME_CHARACTERS}]+$`);

// Local time, with `-` between the time parts: `NAME_CHARACTERS` has no colon.
const NAME_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH-mm-ss';

function sanitize (value) {
  return `${value}`.replace(ILLEGAL_NAME_CHARACTERS, '_').trim();
}

// Whether the sanitised name still says something: it is the name unchanged,
// or it has a latin letter or a digit. `\w` is ASCII only, so a Cyrillic or
// CJK name sanitises to underscores alone.
function carriesMeaning (sanitizedBaseName, baseName) {
  if (sanitizedBaseName === `${baseName}`) {
    return true;
  }
  return /[a-z0-9]/i.test(sanitizedBaseName);
}

/**
 * The item's own name, sanitised and ending in `.zip`, or `undefined` when
 * the sanitised name says nothing. `.zip` is added when the name has no `.zip`
 * at the end, in any case.
 */
function archiveNameForItem (item) {
  const name = nameOf(item);
  // A folder's whole name is checked. A file's extension is kept, and the
  // part before it is checked.
  const extensionAt = isFolder(item) ? -1 : `${name}`.lastIndexOf('.');
  const baseName = extensionAt > 0 ? `${name}`.slice(0, extensionAt) : `${name}`;
  const extension = extensionAt > 0 ? `${name}`.slice(extensionAt) : '';
  const sanitizedBaseName = sanitize(baseName);
  if (!carriesMeaning(sanitizedBaseName, baseName)) {
    return undefined;
  }
  const sanitized = `${sanitizedBaseName}${sanitize(extension)}`;
  return /\.zip$/i.test(sanitized) ? sanitized : `${sanitized}.zip`;
}

function timestampedArchiveName () {
  return `archive-${moment().format(NAME_TIMESTAMP_FORMAT)}`;
}

// The item's `name`, or the last segment of its path.
function nameOf (item) {
  if (item.name) {
    return item.name;
  }
  const segments = `${item.path}`.replace(/\/+$/, '').split('/');
  return segments[segments.length - 1];
}

/**
 * The archive name offered for a selection. For one archivable item, its own
 * name, sanitised, with `.zip` - `report.csv` gives `report.csv.zip`. For
 * several items, or a name that says nothing once sanitised,
 * `archive-<date> <time>`.
 *
 * @param {SelectedStorageItem[]} [selectedItems]
 * @returns {string}
 */
export function defaultArchiveName (selectedItems = []) {
  const items = archivableItems(selectedItems);
  if (items.length !== 1) {
    return timestampedArchiveName();
  }
  return archiveNameForItem(items[0]) || timestampedArchiveName();
}

/**
 * Checks an archive name, after trimming it: the name is required, and it may
 * have only `NAME_CHARACTERS`.
 *
 * @param {string} name
 * @returns {string|undefined} the message to show, or `undefined` when the
 *   trimmed name is acceptable
 */
export function archiveNameError (name) {
  const trimmed = `${name || ''}`.trim();
  if (!trimmed) {
    return 'Archive name is required.';
  }
  // SAFE_ARCHIVE_NAME has no `g` flag, so `.test` answers the same on every
  // call.
  if (!SAFE_ARCHIVE_NAME.test(trimmed)) {
    return 'Only letters, digits, spaces, dot, dash and underscore are allowed.';
  }
  return undefined;
}

/**
 * Asks the server whether the user may start one more archive download.
 * Toasts the reason and answers `false` when the user is already at the limit.
 * Answers `true` when the check cannot run.
 *
 * @param {{checkActiveTasksLimit: function}} asyncTasks
 * @returns {Promise<boolean>}
 */
export async function archiveDownloadAllowed (asyncTasks) {
  if (!asyncTasks || typeof asyncTasks.checkActiveTasksLimit !== 'function') {
    return true;
  }
  const hide = message.loading('Checking active downloads...', 0);
  try {
    const {allowed, reason} = await asyncTasks.checkActiveTasksLimit();
    if (!allowed) {
      message.error(reason, 5);
    }
    return allowed;
  } catch (error) {
    console.warn('Could not check the active-download limit', error);
    return true;
  } finally {
    hide();
  }
}

/**
 * Sends the archive-download request and toasts that the task is queued.
 * On failure, toasts the error and throws it again. With no `paths`, toasts
 * that there is nothing to download, and sends nothing.
 *
 * @param {{startArchiveDownload: function}} asyncTasks
 * @param {string|number} storageId
 * @param {string[]} paths
 * @param {string} archiveName
 * @returns {Promise<void>}
 */
export default async function handleDownloadArchive (
  asyncTasks,
  storageId,
  paths,
  archiveName
) {
  if (!paths || paths.length === 0) {
    message.info('Nothing to download: select at least one file or folder.', 5);
    return;
  }
  const hide = message.loading('Starting archive download...', 0);
  try {
    await asyncTasks.startArchiveDownload(storageId, paths, archiveName);
    message.info(
      'Archive download has been queued. Follow its progress under "Show downloads".',
      5
    );
  } catch (error) {
    message.error(error.message, 5);
    throw error;
  } finally {
    hide();
  }
}
