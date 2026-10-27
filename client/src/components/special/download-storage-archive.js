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
 * The filter every archive-related computation in this file starts from: an
 * item is only ever archived if it carries a `path` and is not a
 * delete-marker. Downloadability is deliberately not part of this - the
 * server builds the archive, not the browser, so an archived (not currently
 * downloadable) file is still archivable.
 *
 * @param {SelectedStorageItem[]} selectedItems
 * @returns {SelectedStorageItem[]}
 */
function archivableItems (selectedItems) {
  return (selectedItems || [])
    .filter(item => item && item.path && !item.deleteMarker);
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
 * Whether the "Download archive" selection item should be offered for this
 * selection. Hidden only when the selection is exactly one archivable file -
 * a single folder keeps it, because an archive is the only way to download a
 * folder. Downloadability is not consulted, for the same reason
 * `archivePathsFromSelection` does not consult it: the server builds the
 * archive, so a single archived (not currently downloadable) file hides this
 * item too, and that item's own row carries the restore/download controls for
 * that case.
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

// What the server accepts as an archive name: `^[\w\-. ]+$`
// (DataStorageDownloadArchiveManager). Anything else in a name built from a
// selected item's own file name - brackets, an ampersand, a non-latin
// letter - would be rejected on OK, so a generated default is folded through
// this first. A name the USER types is still passed through untouched and the
// server's own error surfaced; this is only about not offering an invalid
// default.
const ILLEGAL_NAME_CHARACTERS = /[^\w\-. ]+/g;

// `HH-mm-ss`, not `HH:mm:ss`, for the same reason: a colon is outside the
// pattern above (and illegal in a Windows file name). The date half matches
// `utils/displayDate`'s own `YYYY-MM-DD HH:mm:ss` as closely as the name
// allows, and is local time like that helper, not UTC - the timestamp is read
// by the person who just clicked.
const NAME_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH-mm-ss';

function sanitize (value) {
  return `${value}`.replace(ILLEGAL_NAME_CHARACTERS, '_').trim();
}

// Whether anything of the original survived sanitising. Judged on the part
// that carries the meaning, which is why the extension is split off first:
// `\w` is ASCII-only (no `u` flag, and adding one would not help - `\w` is
// defined as `[A-Za-z0-9_]`), so a Cyrillic or CJK name is replaced
// wholesale. `отчет.csv` would otherwise sanitise to `_.csv`, keep its word
// characters in `.csv` alone, and be offered as `_.csv.zip` - strictly less
// informative than the timestamp it refused to fall back to.
//
// A name sanitising to nothing but underscores is the failure this looks for,
// which is NOT the same as a name that legitimately contains them: `__init__`
// is a perfectly good archive name and the server accepts it, so an untouched
// name always counts, however few letters and digits it has.
function carriesMeaning (sanitizedBaseName, baseName) {
  if (sanitizedBaseName === `${baseName}`) {
    return true;
  }
  return /[a-z0-9]/i.test(sanitizedBaseName);
}

/**
 * A single item's own name, sanitised, with `.zip` on the end - or
 * `undefined` where sanitising leaves nothing worth offering.
 *
 * `.zip` is appended only where it is not already there, case-insensitively,
 * the same rule the server applies (`offeredArchiveName`) and the Archive
 * column displays (`special/downloads/columns.js`). Selecting `backup.zip` is
 * an ordinary thing to do, and `backup.zip.zip` is what the user would
 * otherwise receive.
 */
function archiveNameForItem (item) {
  const name = nameOf(item);
  // A folder's name is not split on its dots - `my.data` is a folder called
  // `my.data`, not a `data` extension - so the whole thing is what has to
  // survive sanitising. For a file, the extension comes along but does not
  // count towards that.
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

function nameOf (item) {
  if (item.name) {
    return item.name;
  }
  // A selection item always has a `path`; `name` is what the browser shows
  // and may be absent on an item built elsewhere. The leaf of the path is the
  // same string, once a folder's trailing slash is off.
  const segments = `${item.path}`.replace(/\/+$/, '').split('/');
  return segments[segments.length - 1];
}

/**
 * The name offered in the archive-name modal.
 *
 * One item selected: that file's or folder's own name with `.zip` on the end,
 * since there is nothing to disambiguate and a timestamp would only be noise
 * - `report.csv` gives `report.csv.zip`, keeping the original name intact
 * rather than guessing which part of it to drop.
 *
 * Anything else: `archive-<date> <time>`, with no extension. The server
 * appends `.zip` itself when the name does not already end in it, and treats a
 * blank name as `archive.zip`.
 *
 * @param {SelectedStorageItem[]} [selectedItems]
 * @returns {string}
 */
export function defaultArchiveName (selectedItems = []) {
  // The same filter `archivePathsFromSelection` applies, so "one item" here
  // means one item that would actually be archived.
  const items = archivableItems(selectedItems);
  if (items.length !== 1) {
    return timestampedArchiveName();
  }
  return archiveNameForItem(items[0]) || timestampedArchiveName();
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
 * @param {*} [messageApi] - defaults to antd's `message`, taken as a
 *   parameter so this is testable without module mocking
 * @returns {Promise<void>}
 */
export default async function handleDownloadArchive (
  asyncTasks,
  storageId,
  paths,
  archiveName,
  messageApi = message
) {
  if (!paths || paths.length === 0) {
    messageApi.info('Nothing to download: select at least one file or folder.', 5);
    return;
  }
  const hide = messageApi.loading('Starting archive download...', 0);
  try {
    await asyncTasks.startArchiveDownload(storageId, paths, archiveName);
    // Plain text, not a link: the route the page link once pointed at is gone,
    // and the downloads view is now a modal on the storage page. It names the
    // menu item that opens that modal ("Show downloads", in the storage view's
    // presentation dropdown - see renderPresentationConfiguration in
    // pipelines/browser/data-storage/index.js), because a user who has just
    // queued an archive otherwise has nothing to go looking for.
    messageApi.info(
      'Archive download has been queued. Follow its progress under "Show downloads".',
      5
    );
  } catch (error) {
    messageApi.error(error.message, 5);
  } finally {
    hide();
  }
}
