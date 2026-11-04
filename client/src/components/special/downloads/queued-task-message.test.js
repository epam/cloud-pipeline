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

import {render, screen, click, fn} from '@test';
import {queuedTaskMessage, SHOW_DOWNLOADS_LABEL} from './queued-task-message';

const SENTENCE = 'Archive download has been queued.';
const PLAIN_SENTENCE = `${SENTENCE} Follow its progress under "${SHOW_DOWNLOADS_LABEL}".`;

describe('queuedTaskMessage', () => {
  test('is the plain sentence, unchanged, with no onShowDownloads given', () => {
    expect(queuedTaskMessage(SENTENCE)).toBe(PLAIN_SENTENCE);
  });

  // Anything that is not a function falls back the same way - a caller
  // passing `undefined` through a chain of optional parameters is the
  // realistic case, not a deliberate non-function value, but either way the
  // existing handler tests (download-storage-archive.test.js,
  // extract-storage-archives.test.js) rely on getting this plain string back
  // when they pass nothing.
  test('stays the plain sentence when onShowDownloads is not a function', () => {
    expect(queuedTaskMessage(SENTENCE, 'not a function')).toBe(PLAIN_SENTENCE);
  });

  test(
    'renders the identical sentence as a node, with Show storage tasks as a clickable link',
    async () => {
      const onShowDownloads = fn();
      const {container} = render(queuedTaskMessage(SENTENCE, onShowDownloads));

      // Character-for-character the same sentence the plain-string form
      // reads, quotes included - the manual and the UI test cases quote it.
      expect(container.textContent).toBe(PLAIN_SENTENCE);

      // An `<a>` with no `href` carries no implicit `link` role, so the link
      // is queried by its own text rather than by role.
      const link = screen.getByText(SHOW_DOWNLOADS_LABEL);
      expect(link.tagName).toBe('A');

      await click(link);
      expect(onShowDownloads).toHaveBeenCalledTimes(1);
    }
  );
});
