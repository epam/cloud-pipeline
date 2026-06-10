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

import Remote from '../basic/Remote';

// userId should be null or undefined to revoke current user tokens.
// "?jti=&userId=" - endpoint available only for admins.
// "?jti=" - available for non-admins too.
export default class UserNamedTokenRevoke extends Remote {
  constructor(jti, userId) {
    super();
    this.constructor.fetchOptions = {
      headers: {
        'Content-type': 'application/json; charset=UTF-8',
      },
      mode: 'cors',
      credentials: 'include',
      method: 'DELETE',
    };
    const queryParameters = [
      `jti=${encodeURIComponent(jti)}`,
      userId !== null && userId !== undefined && userId !== ''
        ? `userId=${encodeURIComponent(userId)}`
        : undefined,
    ].filter(Boolean);
    this.url = `/user/token/revoke?${queryParameters.join('&')}`;
  }
}
