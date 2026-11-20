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

import RemotePost from '../basic/RemotePost';

// `POST /async-task/filter` answers a paged result - `Result<PagedResult<
// List<AsyncTask>>>` server-side - so the payload is
// `{elements, totalCount}`, where `totalCount` is how many tasks the FILTER
// matched rather than how many this page carries. The two are the same number
// for a request that does not page (`AsyncTaskManager.loadByFilter`).
export default class AsyncTaskFilter extends RemotePost {
  constructor () {
    super();
    this.url = '/async-task/filter';
  }

  /**
   * Always `{elements, totalCount}`, whatever the server sent, so no caller
   * has to guess which of the two shapes it is holding.
   *
   * A bare array is still accepted: that is what this endpoint answered
   * before the paged result landed, and a GUI pointed at an older API would
   * otherwise show an empty table rather than a page of tasks. Its total can
   * only be what it carries.
   */
  postprocess (value) {
    const payload = value.payload || {};
    if (Array.isArray(payload)) {
      return {elements: payload, totalCount: payload.length};
    }
    const elements = Array.isArray(payload.elements) ? payload.elements : [];
    const {totalCount} = payload;
    return {
      elements,
      totalCount: typeof totalCount === 'number' ? totalCount : elements.length
    };
  }
}
