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

// The presentation dropdown's own action label (renderPresentationConfiguration,
// pipelines/browser/data-storage/index.js) - read from here rather than
// repeated there, so the menu item and the sentence below cannot drift apart.
// The constant keeps this name; the user reads its value as "Show storage
// tasks" - the modal it opens (downloads-modal.js) is named "Storage tasks"
// to the user for the same reason: it is an async-task table, not only
// downloads, now that an extraction task can land in it too.
export const SHOW_DOWNLOADS_LABEL = 'Show storage tasks';

/**
 * The content a queued-task toast shares between the two callers that queue
 * one - an archive download (pipelines/browser/data-storage/utils/
 * download-archive.js) and an archive extraction
 * (extract-storage-archives.js). `sentence` is the toast's own
 * first half ("Archive download has been queued.", "Archive extraction has
 * been queued."); this appends the second half, naming where the task is
 * followed.
 *
 * With no `onShowDownloads` (not a function) the result is the plain string
 * this already read before a toast could open the Downloads modal itself -
 * keeping that fallback matters: it is what a caller with no modal to open
 * still gets, and it is what the existing handler tests still assert on.
 *
 * With one, the result is a React node carrying the identical wording, only
 * the label itself turned into a link that calls `onShowDownloads` on click.
 * Dismissing the toast the link sits in - so it does not sit over the modal
 * it just opened - is the caller's job, not this function's: see the comment
 * next to where `handleDownloadArchive`/`handleExtractArchives` build the
 * function they pass in here.
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
        No `href`: the click opens the Downloads modal, there is nowhere to
        navigate to, and this codebase does not add `href="javascript:;"`
        for that (life-cycle-counter.js's own "Restore files" link does the
        same). An `<a>` with no `href` carries no implicit `link` ARIA role,
        so a test has to query it by its own text, not by role.
      */}
      <a className="cp-link" onClick={onShowDownloads}>{SHOW_DOWNLOADS_LABEL}</a>
      {'".'}
    </span>
  );
}
