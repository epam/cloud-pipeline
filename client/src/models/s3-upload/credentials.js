/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import dayjs from '../../utils/dayjs';

const REFRESH_WINDOW_MS = 5 * 60 * 1000;

/**
 * Temporary AWS credentials from the Cloud Pipeline API.
 * Plain credential identity for @aws-sdk/* clients — no SDK credential providers.
 */
export default class Credentials {
  accessKeyId;
  secretAccessKey;
  sessionToken;
  expireTime;
  updateCredentials;

  constructor(accessKeyId, secretAccessKey, sessionToken, expireDate, updateCredentialsFn) {
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.sessionToken = sessionToken;
    this.expireTime = parseExpireDate(expireDate);
    this.updateCredentials = updateCredentialsFn;
  }

  update(accessKeyId, secretAccessKey, sessionToken, expireDate) {
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.sessionToken = sessionToken;
    this.expireTime = parseExpireDate(expireDate);
  }

  get() {
    // Kept for callers that trigger a refresh check before signing (v2 compat).
  }

  needsRefresh() {
    if (!this.expireTime) {
      return false;
    }
    return this.expireTime.getTime() - Date.now() < REFRESH_WINDOW_MS;
  }

  refresh = (callback) => {
    if (this.needsRefresh()) {
      if (!this.updateCredentialsPromise) {
        this.updateCredentialsPromise = new Promise((resolve, reject) => {
          this.updateCredentials(true)
            .then(() => resolve())
            .catch(reject)
            .then(() => {
              this.updateCredentialsPromise = undefined;
            });
        });
      }
      this.updateCredentialsPromise.then(() => callback()).catch(callback);
    } else {
      callback();
    }
  };
}

function parseExpireDate(expireDate) {
  if (!expireDate) {
    return undefined;
  }
  const expireTime = dayjs.utc(expireDate, 'YYYY-MM-DD HH:mm:ss');
  return expireTime.isValid() ? expireTime.toDate() : undefined;
}
