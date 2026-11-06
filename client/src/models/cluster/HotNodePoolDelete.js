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

import RemotePost from '../basic/RemotePost';

class HotNodePoolDelete extends RemotePost {
  // `force`: a capacity-reservation pool whose reservation has not reached a terminal
  // status (or `REQUIRED_APPROVE`) is given up on delete, which the backend refuses
  // unless the caller insists - see `needsForceToDeletePool` in
  // `components/cluster/hot-node-pool/edit-hot-node-pool/helpers.js`.
  constructor (id, force = false) {
    super();
    this.constructor.fetchOptions = {
      headers: {
        'Content-type': 'application/json; charset=UTF-8'
      },
      mode: 'cors',
      credentials: 'include',
      method: 'DELETE'
    };
    this.url = `/cluster/pool/${id}?force=${force ? 'true' : 'false'}`;
  }
}

export default HotNodePoolDelete;
