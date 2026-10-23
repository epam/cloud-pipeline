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
 * The cap check that runs when the user clicks "Download archive", before the
 * archive-name modal opens. Toasts the reason and answers `false` when the
 * user is already at the limit, so the caller can decline to open it.
 *
 * This is the only thing standing between the user and the cap: the menu item
 * is never greyed out and nothing counts their tasks in the background, so a
 * count that is anything other than the server's own would be a guess.
 * `checkActiveTasksLimit` (models/asyncTask/CurrentUserAsyncTasks.js) asks for
 * it on each click - including why a check that cannot run lets the download
 * through rather than blocking it - and the same check runs again when the
 * name modal is confirmed, since that modal can sit open for a long time.
 *
 * @param {{checkActiveTasksLimit: function}} asyncTasks
 * @returns {Promise<boolean>}
 */
export async function archiveDownloadAllowed (asyncTasks) {
  // Same reasoning as a failed check inside the store: anything that stops
  // this check from running must not stop the download, because the server
  // enforces the quota itself. So a store that cannot answer at all answers
  // "allowed" here, before the loading toast is even shown.
  if (!asyncTasks || typeof asyncTasks.checkActiveTasksLimit !== 'function') {
    return true;
  }
  // The dropdown closes the moment the item is clicked, so without this the
  // page looks like it ignored the click for as long as the request takes.
  const hide = message.loading('Checking active downloads...', 0);
  try {
    const {allowed, reason} = await asyncTasks.checkActiveTasksLimit();
    if (!allowed) {
      message.error(reason, 5);
    }
    return allowed;
  } catch (error) {
    // `checkActiveTasksLimit` turns a failed count into `{allowed: true}`
    // itself, so reaching this means something unforeseen - still not a
    // reason to refuse the download, and still not a reason to let the
    // rejection escape into an unhandled one.
    console.warn('Could not check the active-download limit', error);
    return true;
  } finally {
    hide();
  }
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
