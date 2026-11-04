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

// The nine suffixes the issue lists, lower case, each with its leading dot,
// longest first. The order only matters to a reader doing a longest-match
// scan by hand - `isArchive` below does not need one, because every compound
// suffix here also ends in a shorter suffix that is itself on the list
// (`data.tar.gz` ends in `.gz`, which is also here), so a plain "does the
// name end in any one of these" test gives the same answer either way. The
// compound forms stay on the list anyway, because it is also what the
// documentation and any later "which part is the name" logic read.
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
 * A case-insensitive suffix test. A name whose whole value is one of the
 * suffixes (`.zip`, `.tar.gz`) is not an archive - there has to be something
 * before the dot. That exact-match case has to be ruled out before the
 * `endsWith` scan, not folded into it: `.tar.gz` itself ends in `.gz`, a
 * shorter suffix that is also on the list, with `.tar` sitting in front of
 * it - so a check of "is the name longer than the suffix it matches" would
 * call it an archive through `.gz` even though the whole name is nothing but
 * a bare suffix.
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

// Both halves are needed: the S3 path (UploadButton's hideUploadInfoDelayedIfDone)
// sets `done: true` AND `status: 'done'` on a finished file, while the
// synchronous/NFS/Azure path (UploadButton's own uploadFile loop) sets only
// `status: 'done'` and never touches `done` at all. Checking only one of the
// two would silently drop the other upload path's finished archives.
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
 * The storage-relative paths of the archives that finished uploading
 * successfully in this batch. Takes `UploadButton`'s own `uploadingFiles`
 * state objects (`{name, status, error, aborted, done}`) and the component's
 * `path` prop - the same pair the component already holds once an upload
 * finishes.
 *
 * @param {UploadingFile[]} uploadingFiles
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

// Named so a test can read the delay instead of hard-coding it.
export const EXTRACT_ARCHIVES_MOCK_DELAY_MS = 1500;

/**
 * Stands in for `POST /datastorage/{id}/extract`, body `{paths: [...]}` with
 * storage-relative paths and no trailing slash. That endpoint is not
 * implemented on the server yet. Once it is, it queues an async task and
 * answers with it - the request does not wait for the extraction itself to
 * finish, the same way starting an archive download only queues one. This
 * mock resolves with nothing instead, because there is no real task to fake,
 * but it always resolves, since there is no real response to fail on either.
 * Swapping this mock for the real call is one small edit once the endpoint
 * exists: a `RemotePost` model.
 *
 * A missing storage id or an empty paths list rejects immediately, before
 * the delay - that is a caller bug either way, not something a real server
 * would need a round trip to tell us.
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
 * Queues the extraction and reports the outcome - the loading toast while
 * the request is in flight, then the queued-info toast on success or the
 * error toast on failure, exactly the shape `handleDownloadArchive`
 * (`download-storage-archive.js`) already uses for a download. The parallel
 * is deliberate: extraction is queued the same way a download is, so the
 * caller (`UploadButton`) does not wait on this either - it fires this and
 * moves on, and the toast is the whole of the user's feedback.
 *
 * An empty `paths` gets no toast of its own here: the only caller never
 * reaches this with one, and `extract`'s own rejection on a missing id or an
 * empty list is guard enough without a second one.
 *
 * @param {string|number} storageId
 * @param {string[]} paths
 * @param {*} [messageApi] - defaults to antd's `message`, taken as a
 *   parameter so this is testable without module mocking
 * @param {function} [extract] - defaults to this module's own
 *   `extractArchives`, taken as a parameter for the same reason - and the
 *   seam `UploadButton`'s own injectable `extractArchives` prop is passed
 *   through to
 * @param {function} [onShowDownloads] - opens the Downloads modal;
 *   `UploadButton`'s own `onShowDownloads` prop is passed through to this.
 *   With none given, the queued toast falls back to the plain string it has
 *   always been.
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
    // See the matching comment in `handleDownloadArchive`
    // (download-storage-archive.js) for why the holder below is
    // unavoidable: the toast's own content needs a function that dismisses
    // the toast before `messageApi.info` - the call that creates the toast -
    // has returned the `hide` to dismiss it with.
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
