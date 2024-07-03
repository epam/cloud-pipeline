/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

// https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API

// Set the name of the hidden property and the change event for visibility
let hidden, visibilityChange;
if (typeof document.hidden !== 'undefined') { // Opera 12.10 and Firefox 18 and later support
  hidden = 'hidden';
  visibilityChange = 'visibilitychange';
} else if (typeof document.msHidden !== 'undefined') {
  hidden = 'msHidden';
  visibilityChange = 'msvisibilitychange';
} else if (typeof document.webkitHidden !== 'undefined') {
  hidden = 'webkitHidden';
  visibilityChange = 'webkitvisibilitychange';
}

function handleVisibilityChange (onHidden = () => {}, onVisible = () => {}) {
  if (document[hidden]) {
    onHidden();
  } else {
    onVisible();
  }
}

export default function attachDocumentVisibilityHandlers (onHidden, onVisible) {
  // Warn if the browser doesn't support addEventListener or the Page Visibility API
  if (typeof document.addEventListener === 'undefined' || hidden === undefined) {
    return () => {};
  } else {
    const handler = () => handleVisibilityChange(onHidden, onVisible);
    // Handle page visibility change
    document.addEventListener(visibilityChange, handler, false);
    return () => document.removeEventListener(visibilityChange, handler);
  }
}
