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

import LoadToolVersionSettings from '../../../models/tools/LoadToolVersionSettings';

export default function getCommitAllowedForTool (toolId, version = 'latest') {
  if (!toolId) {
    return Promise.resolve(false);
  }
  return new Promise((resolve) => {
    const request = new LoadToolVersionSettings(toolId, version);
    request.fetch()
      .then(() => {
        if (request.error) {
          resolve(false);
        }
      })
      .then(() => {
        if (
          request.loaded &&
          request.value &&
          request.value[0]
        ) {
          const allowCommit = request.value[0].allowCommit === undefined
            ? true
            : request.value[0].allowCommit;
          resolve(allowCommit);
        } else {
          resolve(true);
        }
      })
      .catch(e => {
        resolve(false);
      });
  });
}
