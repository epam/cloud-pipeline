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

import RemotePost from '../basic/RemotePost';

export default class RoleCreate extends RemotePost {
  constructor (roleName, userDefault, defaultStorageId = null) {
    super();
    let url = `/role/create?roleName=${roleName}`;
    if (userDefault) {
      url = `${url}&userDefault=true`;
    }
    if (defaultStorageId) {
      url = `${url}&defaultStorageId=${defaultStorageId}`;
    }
    this.url = url;
  }
}
