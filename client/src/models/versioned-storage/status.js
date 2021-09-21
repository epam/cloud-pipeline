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

import VSRemote from './base/remote';
import VSConflictError from './conflict-error';

class VSTaskStatus extends VSRemote {
  constructor (runId, statusId) {
    super(runId);
    this.statusId = statusId;
    this.url = `status/${statusId}`;
  }

  abort = () => {
    if (typeof this.abortFn === 'function') {
      this.abortFn();
    }
  };

  fetchUntilDone = (intervalSec = 1) => {
    if (this.fetchUntilDonePromise) {
      return this.fetchUntilDonePromise;
    }
    this.fetchUntilDonePromise = new Promise((resolve, reject) => {
      const self = this;
      self.abortFn = () => {
        if (self.timeout) {
          clearTimeout(self.timeout);
          self.timeout = null;
        }
        resolve();
        self.abortFn = undefined;
      };
      function fetch () {
        if (self.timeout) {
          clearTimeout(self.timeout);
          self.timeout = null;
        }
        self.fetch()
          .then(() => {
            if (self.error) {
              self.abortFn = undefined;
              reject(new Error(self.error));
            } else {
              const {status, message} = self.value;
              if (/^success$/i.test(status)) {
                self.abortFn = undefined;
                resolve(self.value);
              } else if (/^failure$/i.test(status)) {
                self.abortFn = undefined;
                if (VSConflictError.isConflictError(self.value)) {
                  reject(new VSConflictError(self.value));
                } else {
                  reject(new Error(message));
                }
              } else {
                self.timeout = setTimeout(() => fetch(), intervalSec * 1000);
              }
            }
          })
          .catch(e => {
            self.abortFn = undefined;
            reject(e);
          });
      }
      fetch();
    });
    return this.fetchUntilDonePromise;
  };
}

export default VSTaskStatus;
