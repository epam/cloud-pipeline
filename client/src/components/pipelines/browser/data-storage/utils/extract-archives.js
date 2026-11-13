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

import {message} from 'antd';
import {showQueuedTaskMessage} from '../../../../special/downloads/queued-task-message';

/**
 * @typedef {Object} UploadingFile
 * @property {string} [name]
 * @property {string} [status]
 * @property {string} [error]
 * @property {boolean} [aborted]
 * @property {boolean} [done]
 */

// Lower case, leading dot, longest first. The compound forms are redundant
// for `isArchive` - each also ends in a shorter suffix on the list - and stay
// for the docs and for any later "which part is the name" logic.
export const ARCHIVE_EXTENSIONS = [
  '.tar.gz',
  '.tar.bz2',
  '.tar.xz',
  '.tgz',
  '.zip',
  '.tar',
  '.gz',
  '.bz2',
  '.xz'
];

/**
 * A case-insensitive suffix test. A bare suffix is not an archive, ruled out
 * before the scan: `.tar.gz` is longer than the `.gz` it also ends with.
 *
 * @param {string} name
 * @returns {boolean}
 */
export function isArchive (name) {
  if (!name) {
    return false;
  }
  const lower = `${name}`.toLowerCase();
  if (ARCHIVE_EXTENSIONS.includes(lower)) {
    return false;
  }
  return ARCHIVE_EXTENSIONS.some(extension => lower.endsWith(extension));
}

/**
 * @param {{name: string}[]} files
 * @returns {boolean}
 */
export function hasArchives (files) {
  return (files || []).some(file => file && isArchive(file.name));
}

/**
 * One task per archive, so this is what a batch costs against the user's
 * archive-task allowance.
 *
 * @param {{name: string}[]} files
 * @returns {number}
 */
export function countArchives (files) {
  return (files || []).filter(file => file && isArchive(file.name)).length;
}

/**
 * The archives among a storage-browser selection - files only, since a folder
 * is never an archive, and no delete markers.
 *
 * @param {{path: string, name: string, type: string, deleteMarker: boolean}[]} selectedItems
 * @returns {string[]} storage-relative paths
 */
export function selectedArchivePaths (selectedItems) {
  return (selectedItems || [])
    .filter(item => item &&
      item.path &&
      !item.deleteMarker &&
      /^file$/i.test(item.type || '') &&
      isArchive(item.name || item.path))
    .map(item => item.path);
}

function slots (remaining) {
  return remaining === 1 ? '1 more archive task' : `${remaining} more archive tasks`;
}

/**
 * Why extraction cannot be queued, or `undefined` where it can. One task per
 * archive, so a batch needs that many free slots. An unknown allowance blocks
 * nothing.
 *
 * @param {number} archives
 * @param {{allowed: boolean, reason: string, remaining: number}} [check]
 * @returns {string|undefined}
 */
export function extractionBlockedBy (archives, check = {}) {
  const {allowed, reason, remaining} = check || {};
  if (allowed === false) {
    return reason || 'You have too many archive tasks in progress.';
  }
  if (archives < 1 || !Number.isFinite(remaining) || archives <= remaining) {
    return undefined;
  }
  return `${archives} archives are selected, and you may start only ` +
    `${slots(remaining)} right now. ` +
    'Wait for one of your archive tasks to finish, or cancel one.';
}

// Both halves are needed: UploadButton's S3 path sets `done` and `status`,
// its synchronous path only `status`.
function succeeded (file) {
  if (!file || file.error || file.aborted) {
    return false;
  }
  return file.done === true || /^done$/i.test(file.status || '');
}

function joinPath (prefix, name) {
  if (!prefix) {
    return name;
  }
  return prefix.endsWith('/') ? `${prefix}${name}` : `${prefix}/${name}`;
}

/**
 * The storage-relative paths of the archives that uploaded successfully in
 * this batch.
 *
 * @param {UploadingFile[]} uploadingFiles `UploadButton`'s own state objects
 * @param {string} [path]
 * @returns {string[]}
 */
export function uploadedArchivePaths (uploadingFiles, path) {
  const prefix = path || '';
  const paths = (uploadingFiles || [])
    .filter(file => file && isArchive(file.name) && succeeded(file))
    .map(file => joinPath(prefix, file.name));
  return [...new Set(paths)];
}

// The archive's name without its extension. Longest suffix first, so
// `data.tar.gz` loses all of `.tar.gz` rather than only the `.gz` it also
// ends with - the reason ARCHIVE_EXTENSIONS keeps the compound forms.
function baseNameOf (fileName) {
  const lower = `${fileName}`.toLowerCase();
  const suffix = ARCHIVE_EXTENSIONS.find(extension => lower.endsWith(extension));
  return suffix ? `${fileName}`.slice(0, -suffix.length) : `${fileName}`;
}

/**
 * A folder beside the archive, named after it: `a/b.tar.gz` gives `a/b/`. A
 * name that would not be a legal folder keeps the archive's own folder, since
 * `assertSafePath` rejects a `.` or `..` segment.
 *
 * @param {string} archivePath
 * @returns {string} always ends with `/`, or is `''` for the storage root
 */
export function extractionFolderOf (archivePath) {
  const path = `${archivePath || ''}`;
  const at = path.lastIndexOf('/');
  const prefix = at < 0 ? '' : path.slice(0, at + 1);
  const baseName = baseNameOf(at < 0 ? path : path.slice(at + 1));
  if (!baseName || baseName === '.' || baseName === '..') {
    return prefix;
  }
  return `${prefix}${baseName}/`;
}

/**
 * How many of `paths` were queued, and why the rest were not.
 *
 * @typedef {Object} ExtractionOutcome
 * @property {number} queued
 * @property {number} total
 * @property {Error} [error] what stopped the run
 */

/**
 * Queues one UNPACK_ARCHIVE task per archive, reported in one toast. Stops at
 * the first failure, since the cap is counted per task. The error is reported,
 * not re-thrown - the caller does not await this.
 *
 * @param {{startArchiveExtraction: function}} asyncTasks
 * @param {string|number} storageId
 * @param {string[]} paths the archives, each with no trailing slash
 * @param {*} [messageApi] antd's `message` by default, so this is testable
 * @param {function} [onShowDownloads] opens the Storage tasks modal. With
 *   none, the queued toast is a plain string rather than a link.
 * @returns {Promise<ExtractionOutcome>}
 */
export async function handleExtractArchives (
  asyncTasks,
  storageId,
  paths,
  messageApi = message,
  onShowDownloads
) {
  const total = (paths || []).length;
  if (!asyncTasks || typeof asyncTasks.startArchiveExtraction !== 'function' || total === 0) {
    return {queued: 0, total};
  }
  const hide = messageApi.loading('Starting archive extraction...', 0);
  let queued = 0;
  let failure;
  try {
    for (const path of paths) {
      await asyncTasks.startArchiveExtraction(storageId, path, extractionFolderOf(path));
      queued += 1;
    }
  } catch (error) {
    failure = error;
  } finally {
    hide();
  }
  if (queued === 0) {
    messageApi.error(errorMessageOf(failure), 5);
    return {queued, total, error: failure};
  }
  showQueuedTaskMessage(
    messageApi,
    queuedSentence(queued, total),
    onShowDownloads,
    queued > 1
  );
  if (failure) {
    // Both toasts: the queued one names what did go out, this one why the
    // rest did not.
    messageApi.error(errorMessageOf(failure), 5);
  }
  return {queued, total, error: failure};
}

// A rejection is an Error from both throw sites today, but an empty error
// toast is a bad way to find out otherwise.
function errorMessageOf (failure) {
  return (failure && failure.message) || 'Could not queue the archive extraction.';
}

function queuedSentence (queued, total) {
  if (queued === total) {
    return total === 1
      ? 'Archive extraction has been queued.'
      : `Extraction of ${total} archives has been queued.`;
  }
  return `Extraction of ${queued} of ${total} archives has been queued.`;
}
