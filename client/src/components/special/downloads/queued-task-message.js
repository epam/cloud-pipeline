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
 * @returns {string|React.ReactNode}
 */
export function queuedTaskMessage (sentence, onShowDownloads) {
  if (typeof onShowDownloads !== 'function') {
    return `${sentence} Follow its progress under "${SHOW_DOWNLOADS_LABEL}".`;
  }
  return (
    <span>
      {`${sentence} Follow its progress under "`}
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
