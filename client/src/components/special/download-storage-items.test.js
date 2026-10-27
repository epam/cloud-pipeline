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

import {fn, spyOn, restoreMocks, stubApi} from '@test';
import handleDownloadItems, {
  downloadItemsActionAvailable,
  MIN_DOWNLOAD_ITEMS,
  MAX_DOWNLOAD_ITEMS
} from './download-storage-items';
import auditStorageAccessManager from '../../utils/audit-storage-access';

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
});

describe('MIN_DOWNLOAD_ITEMS / MAX_DOWNLOAD_ITEMS', () => {
  it('pins the selection bounds the plain Download item is offered for', () => {
    expect(MIN_DOWNLOAD_ITEMS).toBe(1);
    expect(MAX_DOWNLOAD_ITEMS).toBe(3);
  });
});

describe('handleDownloadItems', () => {
  afterEach(() => {
    restoreMocks();
  });

  // downloadFileUrl (utils/download-file-url.js) clicks a real anchor with a
  // real href, which makes jsdom attempt a navigation it does not implement
  // ("Not implemented: navigation") - noise on every run. document.createElement
  // is stubbed so the anchor it creates never actually navigates (its `click`
  // is replaced with a no-op); every other tag still gets a real element.
  function stubAnchorClick () {
    const realCreateElement = document.createElement.bind(document);
    return spyOn(document, 'createElement').mockImplementation((tagName) => {
      const element = realCreateElement(tagName);
      if (`${tagName}`.toLowerCase() === 'a') {
        element.click = () => {};
      }
      return element;
    });
  }

  // Pins the fix for the popup blocker suppressing every download after the
  // first: two files now trigger two anchor downloads, each with its own url
  // and name, rather than one window.open silently dropping the second link.
  it('triggers one download per file, each with its own url and name', async () => {
    stubAnchorClick();
    const appendSpy = spyOn(document.body, 'appendChild');
    const api = stubApi();
    api.reply('/datastorage/7/generateUrl', [
      {url: 'https://example.com/a'},
      {url: 'https://example.com/b'}
    ]);
    // handleDownloadItems also fires auditStorageAccessManager.reportReadAccess,
    // which debounces its own network call behind a real 1-second setTimeout -
    // unrelated to what this test pins (the audit entry is about the read,
    // not about how the bytes reach the browser - it is unchanged by this
    // phase). Left alone, that timer would fire after this test and its
    // stubApi are gone, logging a stray network-guard warning, so it is
    // stubbed to a no-op here.
    spyOn(auditStorageAccessManager, 'reportReadAccess').mockImplementation(() => {});
    const preferences = {
      fetchIfNeededOrWait: fn().mockResolvedValue(),
      facetedFilterDownload: {maximum: 10}
    };
    const items = [
      {storageId: 7, path: 'a.txt', name: 'a.txt'},
      {storageId: 7, path: 'b.txt', name: 'b.txt'}
    ];

    await handleDownloadItems(preferences, items);

    const anchors = appendSpy.mock.calls
      .map((call) => call[0])
      .filter((element) => element.tagName && element.tagName.toLowerCase() === 'a');
    expect(anchors).toHaveLength(2);
    expect(anchors.map((a) => a.href)).toEqual([
      'https://example.com/a',
      'https://example.com/b'
    ]);
    expect(anchors.map((a) => a.download)).toEqual(['a.txt', 'b.txt']);
  });
});
