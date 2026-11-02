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

import {storageItemSelectionAvailable} from './utils';

describe('storageItemSelectionAvailable', () => {
  const row = (flags = {}) => ({
    selectable: true,
    downloadable: false,
    editable: false,
    shareAvailable: false,
    archiveSelectable: false,
    ...flags
  });

  // The regression this predicate exists for. A folder is never
  // `downloadable`, and a user with READ only on the storage - or a
  // ROLE_STORAGE_READER - gets neither `editable` nor `shareAvailable`, so
  // before `archiveSelectable` the row had no checkbox and no folder could be
  // put into an archive.
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

  // The flags arrive from the listing as whatever the item carried, so the
  // answer has to be a boolean rather than a truthy value - it is read both
  // as a condition and compared against.
  test('answers a boolean, not the flag it found', () => {
    expect(storageItemSelectionAvailable(row({archiveSelectable: 'yes'}))).toBe(true);
    expect(storageItemSelectionAvailable(row({archiveSelectable: undefined}))).toBe(false);
  });
});
