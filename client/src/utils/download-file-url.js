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
 * Triggers a download of `url` through a hidden `<a download>` element -
 * created, clicked and removed synchronously - instead of `window.open`.
 * A sequence of anchor clicks is not a sequence of popups, so the browser's
 * popup blocker does not apply to it the way it does to `window.open`,
 * including when several of these run one after another after an `await`
 * (the case that motivated this: a user gesture's transient activation is
 * consumed by the first `window.open` of a loop, and a popup blocker
 * suppresses the rest).
 *
 * This is not the first copy of the technique in `src/` - it already exists,
 * inline, at `components/pipelines/browser/data-storage/components/
 * download-file-button.js:141-147`,
 * `components/special/data-storage-file-download-button/index.js:104-108`,
 * `components/settings/system-management/dts/components/dts-logs.js:117-119`
 * and `components/pipelines/launch/form/utilities/
 * parameter-utilities.js:2306-2308`. This is a fifth, giving the new caller
 * (`special/download-storage-items.js`) a home for it rather than a sixth
 * inline copy; migrating the other four onto this helper is a separate
 * change, not this one.
 *
 * @param {string} url
 * @param {string} [name] - best-effort only: a browser ignores `a.download`
 *   for a cross-origin URL, and a pre-signed cloud storage URL is always
 *   cross-origin, so in practice the filename comes from the server's own
 *   `Content-Disposition` header, exactly as it already does for the
 *   single-file download button this is copied from. Set anyway, for the
 *   same-origin case - but a filename bug here almost certainly does not
 *   live here.
 */
export default function downloadFileUrl (url, name) {
  const a = document.createElement('a');
  a.href = url;
  if (name) {
    a.download = name;
  }
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
