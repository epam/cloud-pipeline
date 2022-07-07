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

class UserToken extends Remote {
  constructor (expiration) {
    super();
    this.url = `/user/token?expiration=${expiration}`;
  }
}

const TOKEN_EXPIRATION_SECONDS = 60 * 60 * 24;

let tokenCache;

export function fetchToken () {
  return new Promise((resolve, reject) => {
    if (tokenCache) {
      resolve(tokenCache);
    } else {
      const tokenRequest = new UserToken(TOKEN_EXPIRATION_SECONDS);
      tokenRequest
        .fetch()
        .then(() => {
          if (tokenRequest.loaded) {
            tokenCache = tokenRequest.value.token;
            resolve(tokenCache);
          } else {
            reject(new Error(`Error fetching user token: ${tokenRequest.value.message}`));
          }
        })
        .catch(e => {
          reject(new Error(`Error fetching user token: ${e.message}`));
        });
    }
  });
}

export default UserToken;
