/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import Remote from '../../basic/Remote';

const STATUS = {
  'INITIATED': 'INITIATED',
  'RUNNING': 'RUNNING',
  'SUCCEEDED': 'SUCCEEDED'
};

class DataStorageLifeCycleRulesLoad extends Remote {
  url;
  path;

  constructor (id, path = '') {
    super();
    if (path) {
      this.path = path.startsWith('/') ? path : `/${path}`;
      this.url = `/datastorage/${id}/lifecycle/rule?path=${this.path}`;
    } else {
      this.url = `/datastorage/${id}/lifecycle/rule`;
    }
  };
}

export {STATUS};
export default DataStorageLifeCycleRulesLoad;
