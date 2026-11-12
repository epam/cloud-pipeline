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
import {queuedTaskMessage} from './downloads/queued-task-message';

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
 * A case-insensitive suffix test. A bare suffix (`.zip`, `.tar.gz`) is not an
 * archive, and is ruled out before the scan rather than by a length check:
 * `.tar.gz` also ends in the shorter `.gz`, so it is longer than a suffix it
 * matches.
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

export const EXTRACT_ARCHIVES_MOCK_DELAY_MS = 1500;

/**
 * A mock: the server call that queues the extraction is not wired up yet, so
 * this only waits and resolves. A missing id or an empty paths list is a
 * caller bug and rejects without waiting.
 *
 * @param {string|number} storageId
 * @param {string[]} paths
 * @returns {Promise<void>}
 */
export function extractArchives (storageId, paths) {
  if (!storageId || !paths || paths.length === 0) {
    return Promise.reject(
      new Error('extractArchives: a storage id and at least one path are required')
    );
  }
  return new Promise(resolve => {
    setTimeout(resolve, EXTRACT_ARCHIVES_MOCK_DELAY_MS);
  });
}

/**
 * Queues the extraction and reports it - a loading toast, then the queued
 * toast or the error toast. The error is reported and not re-thrown: the
 * caller (`UploadButton`) fires this and moves on, so nobody would catch it.
 *
 * @param {string|number} storageId
 * @param {string[]} paths
 * @param {*} [messageApi] antd's `message` by default; a parameter so this is
 *   testable without module mocking
 * @param {function} [extract] this module's `extractArchives` by default;
 *   `UploadButton`'s own `extractArchives` prop is threaded through it
 * @param {function} [onShowDownloads] opens the Storage tasks modal. With
 *   none, the queued toast is a plain string rather than a link.
 * @returns {Promise<void>}
 */
export async function handleExtractArchives (
  storageId,
  paths,
  messageApi = message,
  extract = extractArchives,
  onShowDownloads
) {
  const hide = messageApi.loading('Starting archive extraction...', 0);
  try {
    await extract(storageId, paths);
    // The link in the toast's content dismisses that same toast, whose `hide`
    // only exists once `messageApi.info` has returned. The holder is read at
    // click time, long after the assignment below.
    const queuedHide = {current: undefined};
    const showDownloads = typeof onShowDownloads === 'function'
      ? () => {
        if (queuedHide.current) {
          queuedHide.current();
        }
        onShowDownloads();
      }
      : undefined;
    queuedHide.current = messageApi.info(
      queuedTaskMessage('Archive extraction has been queued.', showDownloads),
      5
    );
  } catch (error) {
    messageApi.error(error.message, 5);
  } finally {
    hide();
  }
}
