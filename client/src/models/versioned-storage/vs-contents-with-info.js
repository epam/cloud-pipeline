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

import Remote from '../basic/Remote';

export default class VersionedStorageListWithInfo extends Remote {
  constructor (id, options) {
    super();
    const {
      page = 0,
      pageSize = 20,
      path,
      version
    } = options;
    this.id = id;
    let query = [
      `page=${page}`,
      `page_size=${pageSize}`,
      version && `version=${version}`,
      path && `path=${encodeURIComponent(path)}`
    ].filter(Boolean).join('&');
    if (query) {
      query = '?'.concat(query);
    }
    this.url = `/pipeline/${id}/logs_tree${query}`;
  }
}
