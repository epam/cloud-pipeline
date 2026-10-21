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
 * Maps a data-storage selection to the `paths` array
 * `POST /datastorage/{id}/download/archive` expects. Unlike
 * `itemsAvailableForDownload` in the storage browser's selection dropdown, this
 * keeps BOTH files and folders — folder support is the entire point of this
 * feature — and does not filter on `downloadable`, which is false for every
 * folder.
 *
 * A folder's path comes back with a trailing `/`, a file's does not.
 * `DownloadArchiveVO`'s doc comment on the backend is explicit that the paths
 * are relative to the storage root "and a trailing slash marks a folder" — it
 * is the server's only signal of folder-ness, so getting this wrong silently
 * turns a folder into a (non-existent) file request.
 *
 * @param {SelectedStorageItem[]} selectedItems
 * @returns {string[]}
 */
export function archivePathsFromSelection (selectedItems) {
  const paths = (selectedItems || [])
    .filter(item => item && item.path && !item.deleteMarker)
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
 * A fresh, timestamped default archive name, with no extension: the server
 * appends `.zip` itself when the name does not already end in it, and treats a
 * blank name as `archive.zip`, so showing `.zip` here would only invite the
 * user to delete or duplicate it.
 *
 * @returns {string}
 */
export function defaultArchiveName () {
  return `archive-${moment.utc().format('YYYYMMDD-HHmmss')}`;
}

/**
 * Sends the confirmed archive-download request and reports the outcome.
 * Takes the already-computed `paths` and the user-confirmed `archiveName`, not
 * the raw selection: by the time this runs, the name-confirmation modal has
 * already been through.
 *
 * This is this feature's FIRST of two toasts — it fires as soon as
 * `startArchiveDownload` resolves, long before the task finishes. The second
 * is the completion toast fired elsewhere, from the background poll's own
 * SUCCESS/FAILED transition; it is not re-implemented here, and this toast is
 * not redundant with it.
 *
 * @param {{startArchiveDownload: function}} asyncTasks
 * @param {string|number} storageId
 * @param {string[]} paths
 * @param {string} archiveName
 * @returns {Promise<void>}
 */
export default async function handleDownloadArchive (asyncTasks, storageId, paths, archiveName) {
  if (!paths || paths.length === 0) {
    message.info('Nothing to download: select at least one file or folder.', 5);
    return;
  }
  const hide = message.loading('Starting archive download...', 0);
  try {
    await asyncTasks.startArchiveDownload(storageId, paths, archiveName);
    message.success(
      'Archive download has been queued. ' +
      'It will appear on the Downloads page once it is ready.',
      5
    );
  } catch (error) {
    message.error(error.message, 5);
  } finally {
    hide();
  }
}
