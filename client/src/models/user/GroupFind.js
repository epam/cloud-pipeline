/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import Remote from '../basic/Remote';

export default class GroupFind extends Remote {
  static findGroups (prefix) {
    if (!prefix) {
      return Promise.resolve([]);
    }
    return new Promise((resolve) => {
      const request = new GroupFind(prefix);
      request
        .fetch()
        .then(() => {
          if (request.loaded) {
            resolve((request.value || []).slice());
          } else {
            resolve([]);
          }
        })
        .catch(() => resolve([]));
    });
  }

  constructor (prefix) {
    super();
    this.url = `/group/find?prefix=${prefix}`;
  }
}
