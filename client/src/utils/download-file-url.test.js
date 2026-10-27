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

import {spyOn, restoreMocks} from '@test';
import downloadFileUrl from './download-file-url';

// Clicking a real anchor with a real `href` makes jsdom attempt a navigation
// it does not implement, logging "Not implemented: navigation" on every run.
// `document.createElement` is stubbed so the anchor this helper creates never
// actually navigates - its `click` is replaced with a no-op - while every
// other tag still gets the real DOM element, in case something else in the
// test environment needs one.
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

describe('downloadFileUrl', () => {
  afterEach(() => {
    restoreMocks();
  });

  test('creates an anchor with the given href, appended then removed', () => {
    stubAnchorClick();
    const appendSpy = spyOn(document.body, 'appendChild');
    const removeSpy = spyOn(document.body, 'removeChild');

    downloadFileUrl('https://example.com/report.csv', 'report.csv');

    expect(appendSpy).toHaveBeenCalledTimes(1);
    const anchor = appendSpy.mock.calls[0][0];
    expect(anchor.tagName.toLowerCase()).toBe('a');
    expect(anchor.href).toBe('https://example.com/report.csv');
    expect(anchor.download).toBe('report.csv');
    expect(removeSpy).toHaveBeenCalledWith(anchor);
  });

  test('sets download when a name is given', () => {
    stubAnchorClick();
    const appendSpy = spyOn(document.body, 'appendChild');

    downloadFileUrl('https://example.com/a', 'a.txt');

    const anchor = appendSpy.mock.calls[0][0];
    expect(anchor.download).toBe('a.txt');
  });

  test('leaves download unset when no name is given', () => {
    stubAnchorClick();
    const appendSpy = spyOn(document.body, 'appendChild');

    downloadFileUrl('https://example.com/a');

    const anchor = appendSpy.mock.calls[0][0];
    expect(anchor.download).toBe('');
  });

  test('removes the element again after clicking it', () => {
    stubAnchorClick();
    const appendSpy = spyOn(document.body, 'appendChild');
    const removeSpy = spyOn(document.body, 'removeChild');

    downloadFileUrl('https://example.com/a', 'a.txt');

    const anchor = appendSpy.mock.calls[0][0];
    expect(removeSpy).toHaveBeenCalledTimes(1);
    expect(removeSpy).toHaveBeenCalledWith(anchor);
  });
});
