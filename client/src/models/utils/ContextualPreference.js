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

export const names = {
  allowedInstanceTypes: 'cluster.allowed.instance.types',
  allowedToolInstanceTypes: 'cluster.allowed.instance.types.docker',
  allowedPriceTypes: 'cluster.allowed.price.types',
  jobsVisibility: 'launch.run.visibility'
};

export class ContextualPreferenceLoad extends Remote {

  constructor (level, name, resourceId) {
    super();
    this.url = `/contextual/preference/load?name=${name}&level=${level}&resourceId=${resourceId}`;
  }

}

export class ContextualPreferenceUpdate extends RemotePost {

  constructor () {
    super();
    this.constructor.fetchOptions = {
      headers: {
        'Content-type': 'application/json; charset=UTF-8'
      },
      mode: 'cors',
      credentials: 'include',
      method: 'PUT'
    };
    this.url = '/contextual/preference';
  }

}

export class ContextualPreferenceDelete extends RemotePost {

  constructor (name, level, resourceId) {
    super();
    this.constructor.fetchOptions = {
      headers: {
        'Content-type': 'application/json; charset=UTF-8'
      },
      mode: 'cors',
      credentials: 'include',
      method: 'DELETE'
    };
    this.url = `/contextual/preference?name=${name}&level=${level}&resourceId=${resourceId}`;
  }

}
