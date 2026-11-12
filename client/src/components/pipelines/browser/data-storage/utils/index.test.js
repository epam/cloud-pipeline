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

import {
  checkStorageDownloadAllowed,
  downloadableStorageItems,
  downloadItemsActionAvailable,
  MAX_DOWNLOAD_ITEMS,
  MIN_DOWNLOAD_ITEMS,
  partialGenerateUrlHint,
  partialRemoveHint,
  removableStorageItems,
  removeConfirmTitle,
  restorableStorageItems,
  storageItemSelectionAvailable
} from './index';

describe('storageItemSelectionAvailable', () => {
  const row = (flags = {}) => ({
    selectable: true,
    downloadable: false,
    editable: false,
    shareAvailable: false,
    archiveSelectable: false,
    ...flags
  });

  // A read-only user's folder has no other flag set.
  test('an archive-selectable folder is selectable for a read-only user', () => {
    expect(storageItemSelectionAvailable(row({archiveSelectable: true}))).toBe(true);
  });

  test.each([
    ['downloadable', {downloadable: true}],
    ['editable', {editable: true}],
    ['shareAvailable', {shareAvailable: true}],
    ['archiveSelectable', {archiveSelectable: true}]
  ])('%s alone is enough', (_, flags) => {
    expect(storageItemSelectionAvailable(row(flags))).toBe(true);
  });

  test('a row no action can touch is not selectable', () => {
    expect(storageItemSelectionAvailable(row())).toBe(false);
  });

  // `selectable` is the row's own veto: the ".." navigation row, a version
  // child row, a delete-marker.
  test('selectable: false vetoes every other flag', () => {
    const item = row({
      selectable: false,
      downloadable: true,
      editable: true,
      shareAvailable: true,
      archiveSelectable: true
    });
    expect(storageItemSelectionAvailable(item)).toBe(false);
  });

  test('answers false rather than throwing on no item', () => {
    expect(storageItemSelectionAvailable(undefined)).toBe(false);
  });

  test('answers a boolean, not the flag it found', () => {
    expect(storageItemSelectionAvailable(row({archiveSelectable: 'yes'}))).toBe(true);
    expect(storageItemSelectionAvailable(row({archiveSelectable: undefined}))).toBe(false);
  });
});

describe('checkStorageDownloadAllowed', () => {
  test('a regular storage with downloads enabled allows download', () => {
    expect(checkStorageDownloadAllowed({sensitive: false}, true)).toBe(true);
  });

  test('a sensitive storage allows no download', () => {
    expect(checkStorageDownloadAllowed({sensitive: true}, true)).toBe(false);
  });

  test('a storage whose download attribute denies this user allows no download', () => {
    expect(checkStorageDownloadAllowed({sensitive: false}, false)).toBe(false);
  });

  // `downloadEnabled` is false until the storage's metadata has loaded.
  test('an unknown storage or download state allows no download', () => {
    expect(checkStorageDownloadAllowed(undefined, true)).toBe(false);
    expect(checkStorageDownloadAllowed({}, undefined)).toBe(false);
  });
});

describe('downloadItemsActionAvailable', () => {
  it.each([
    [0, false],
    [1, true],
    [2, true],
    [3, true],
    [4, false],
    [10, false]
  ])('%s items answer %s', (count, expected) => {
    expect(downloadItemsActionAvailable(count)).toBe(expected);
  });

  // Where "Download archive" is offered nowhere, plain Download covers every
  // selection size.
  it.each([
    [0, false],
    [1, true],
    [4, true],
    [10, true]
  ])('%s items answer %s when archives are not supported', (count, expected) => {
    expect(downloadItemsActionAvailable(count, {archiveSupported: false})).toBe(expected);
  });

  it('keeps the upper bound when archives are supported', () => {
    expect(downloadItemsActionAvailable(4, {archiveSupported: true})).toBe(false);
  });
});

describe('MIN_DOWNLOAD_ITEMS / MAX_DOWNLOAD_ITEMS', () => {
  it('pins the selection bounds the plain Download item is offered for', () => {
    expect(MIN_DOWNLOAD_ITEMS).toBe(1);
    expect(MAX_DOWNLOAD_ITEMS).toBe(3);
  });
});

describe('removableStorageItems', () => {
  test('keeps only the items the user may write to', () => {
    const writable = {path: 'a', editable: true};
    const readOnly = {path: 'b', editable: false};
    expect(removableStorageItems([writable, readOnly])).toEqual([writable]);
  });

  test('answers an empty list on no selection', () => {
    expect(removableStorageItems(undefined)).toEqual([]);
    expect(removableStorageItems([undefined])).toEqual([]);
  });
});

describe('partialRemoveHint', () => {
  test('names both counts when only some items will be removed', () => {
    expect(partialRemoveHint(5, 2)).toBe(
      'Only items you have write access to will be removed (2 of 5).'
    );
  });

  test('gives no hint when every selected item will be removed', () => {
    expect(partialRemoveHint(3, 3)).toBeUndefined();
  });

  // "Remove" is not offered at all then.
  test('gives no hint when no selected item will be removed', () => {
    expect(partialRemoveHint(3, 0)).toBeUndefined();
  });
});

describe('removeConfirmTitle', () => {
  test('names both counts when only some items will be removed', () => {
    expect(removeConfirmTitle(5, 2)).toBe('Remove 2 of 5 selected items?');
  });

  test('keeps the old title when every selected item will be removed', () => {
    expect(removeConfirmTitle(3, 3)).toBe('Remove all selected items?');
  });
});

describe('downloadableStorageItems', () => {
  test('keeps only the items the user may download', () => {
    const file = {path: 'a.txt', type: 'File', downloadable: true};
    const folder = {path: 'sub', type: 'Folder', downloadable: false};
    expect(downloadableStorageItems([file, folder])).toEqual([file]);
  });

  test('answers an empty list on no selection', () => {
    expect(downloadableStorageItems(undefined)).toEqual([]);
    expect(downloadableStorageItems([undefined])).toEqual([]);
  });
});

describe('partialGenerateUrlHint', () => {
  test('names both counts when only some items get a URL', () => {
    expect(partialGenerateUrlHint(4, 3)).toBe(
      'URLs are generated only for downloadable files (3 of 4).'
    );
  });

  test('gives no hint when every selected item gets a URL', () => {
    expect(partialGenerateUrlHint(2, 2)).toBeUndefined();
  });

  test('gives no hint when no selected item gets a URL', () => {
    expect(partialGenerateUrlHint(2, 0)).toBeUndefined();
  });
});

describe('restorableStorageItems', () => {
  const isArchivedFile = (item) => !!item.archived;

  test('keeps a folder the user may write to', () => {
    const folder = {path: 'sub', type: 'Folder', editable: true};
    expect(restorableStorageItems([folder], isArchivedFile)).toEqual([folder]);
  });

  // With fine-grained permissions a user may only read some folders.
  test('skips a folder the user may only read', () => {
    const folder = {path: 'sub', type: 'Folder', editable: false};
    expect(restorableStorageItems([folder], isArchivedFile)).toEqual([]);
  });

  test('keeps an archived file, and skips a file that is not archived', () => {
    const archived = {path: 'cold.txt', type: 'File', archived: true};
    const regular = {path: 'a.txt', type: 'File', editable: true};
    expect(restorableStorageItems([archived, regular], isArchivedFile)).toEqual([archived]);
  });

  test('answers an empty list on no selection', () => {
    expect(restorableStorageItems(undefined, isArchivedFile)).toEqual([]);
  });
});
