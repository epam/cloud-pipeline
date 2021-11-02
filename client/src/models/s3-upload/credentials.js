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

import AWS from 'aws-sdk/index';
import moment from 'moment-timezone';

export default class Credentials extends AWS.Credentials {
  updateCredentials;

  constructor (
    accessKeyId,
    secretAccessKey,
    sessionToken,
    expireDate,
    updateCredentialsFn
  ) {
    super(accessKeyId, secretAccessKey, sessionToken);
    const expireTime = expireDate ? moment.utc(expireDate, 'YYYY-MM-DD HH:mm:ss') : undefined;
    this.expireTime = expireTime ? expireTime.toDate() : this.expireTime;
    this.updateCredentials = updateCredentialsFn;
  }

  update (
    accessKeyId,
    secretAccessKey,
    sessionToken,
    expireDate
  ) {
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.sessionToken = sessionToken;
    const expireTime = expireDate ? moment.utc(expireDate, 'YYYY-MM-DD HH:mm:ss') : undefined;
    this.expireTime = expireTime ? expireTime.toDate() : undefined;
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
      this.updateCredentialsPromise
        .then(() => callback())
        .catch(callback);
    } else {
      super.refresh(callback);
    }
  }
}
