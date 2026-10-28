/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {
  downloadItemsActionAvailable,
  MIN_DOWNLOAD_ITEMS,
  MAX_DOWNLOAD_ITEMS
} from './download-storage-items';

describe('downloadItemsActionAvailable', () => {
  it.each([
    [0, false],
    [1, true],
    [2, true],
    [3, true],
    [4, false],
    [10, false]
  ])('answers %s for a selection of %s items', (count, expected) => {
    expect(downloadItemsActionAvailable(count)).toBe(expected);
  });

  // On a storage the archive worker cannot read, "Download archive" is not
  // offered, so plain Download must cover every selection size.
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
