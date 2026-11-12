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

import React from 'react';

// The presentation dropdown's own action label, read from here rather than
// repeated in data-storage/index.js so the two cannot drift apart.
export const SHOW_DOWNLOADS_LABEL = 'Show storage tasks';

/**
 * The second half of a queued-task toast, shared by the archive-download and
 * archive-extraction handlers. `sentence` is their own first half.
 *
 * Without a function, the result is a plain string - what a caller with no
 * modal to open gets. With one, the same wording as a node, the label turned
 * into a link. Dismissing the toast the link sits in is the caller's job.
 *
 * @param {string} sentence
 * @param {function} [onShowDownloads]
 * @param {boolean} [plural] several tasks were queued, so the pronoun below
 *   is "their" rather than "its"
 * @returns {string|React.ReactNode}
 */
export function queuedTaskMessage (sentence, onShowDownloads, plural = false) {
  const lead = `${sentence} Follow ${plural ? 'their' : 'its'} progress under "`;
  if (typeof onShowDownloads !== 'function') {
    return `${lead}${SHOW_DOWNLOADS_LABEL}".`;
  }
  return (
    <span>
      {lead}
      {/*
        No `href`: the click opens a modal, there is nowhere to navigate to -
        life-cycle-counter.js's "Restore files" link does the same. With no
        `href` there is no implicit `link` role, so a test queries by text.
      */}
      <a className="cp-link" onClick={onShowDownloads}>{SHOW_DOWNLOADS_LABEL}</a>
      {'".'}
    </span>
  );
}

/**
 * Shows the queued toast, whose link dismisses that same toast before opening
 * the modal - so it does not sit over what the click just opened.
 *
 * The toast's `hide` only exists once `messageApi.info` has returned, while
 * the content handed to that call already needs it, hence the holder: it is
 * read at click time, long after the assignment below. A `hide` called after
 * the toast has timed out on its own is antd's `removeNotice`, a no-op.
 *
 * @param {*} messageApi
 * @param {string} sentence
 * @param {function} [onShowDownloads]
 * @param {boolean} [plural]
 */
export function showQueuedTaskMessage (messageApi, sentence, onShowDownloads, plural = false) {
  const queuedHide = {current: undefined};
  const showDownloads = typeof onShowDownloads === 'function'
    ? () => {
      if (queuedHide.current) {
        queuedHide.current();
      }
      onShowDownloads();
    }
    : undefined;
  queuedHide.current = messageApi.info(
    queuedTaskMessage(sentence, showDownloads, plural),
    5
  );
}
