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
import RemotePost from '../basic/RemotePost';

export class UserRunCount extends RemotePost {
  static fetchOptions = {
    headers: {
      'Content-type': 'application/json; charset=UTF-8'
    },
    mode: 'cors',
    credentials: 'include',
    method: 'POST'
  };

  url = '/run/count';

  constructor (user, countChildNodes = false) {
    super();
    this.user = user;
    this.countChildNodes = countChildNodes;
  }

  fetch () {
    return super.send({
      statuses: [
        'RUNNING',
        'PAUSED',
        'PAUSING',
        'RESUMING'
      ],
      userModified: this.countChildNodes,
      owners: this.user ? [this.user] : undefined
    });
  }
}

export default class RunCount extends Remote {
  static defaultValue = 0;
  static auto = false;
  static fetchOptions = {
    headers: {
      'Content-type': 'application/json; charset=UTF-8'
    },
    mode: 'cors',
    credentials: 'include',
    method: 'POST',
    body: JSON.stringify({
      statuses: [
        'RUNNING',
        'PAUSED',
        'PAUSING',
        'RESUMING'
      ],
      userModified: false
    })
  };

  url = '/run/count';

  constructor () {
    super();
    const fetch = ::this.silentFetch;
    (function tick () {
      fetch();
      setTimeout(tick, 5000);
    })();
  }
}
