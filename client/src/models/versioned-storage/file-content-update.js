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

import VSFileContent from './file-content';
import VSRemote from './base/remote';

export default class VSFileContentUpdate extends VSRemote {
  constructor (runId, storageId, file, contents) {
    super(runId);
    this.contents = contents;
    this.url = VSFileContent.getUrl(runId, storageId, file);
  }

  async doRegularFetch () {
    const {headers = {}} = this.constructor.fetchOptions || {};
    return new Promise((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.withCredentials = false;
      request.onreadystatechange = function () {
        if (request.readyState !== 4) return;
        if (request.status !== 200) {
          reject(new Error(request.statusText));
        } else {
          try {
            const response = JSON.parse(request.responseText);
            if (response.status && response.status.toLowerCase() === 'error') {
              reject(new Error(response.message));
            } else {
              resolve(response.payload);
            }
          } catch (e) {
            reject(new Error(`Error parsing response: ${e.toString()}`));
          }
        }
      };
      request.open('POST', `${this.constructor.prefix}${this.url}`);
      Object.entries(headers)
        .forEach(([header, value]) => {
          request.setRequestHeader(header, value);
        });
      request.send(this.contents);
    });
  }
}
