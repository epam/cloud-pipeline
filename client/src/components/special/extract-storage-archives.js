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
 * implemented on the server yet - this always resolves, because there is no
 * real response to fail on. Swapping this mock for the real call is one
 * small edit once the endpoint exists: a `RemotePost` model.
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
