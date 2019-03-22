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

class Source extends Remote {
  constructor (id, version, path, recursive=false) {
    super();
    this.id = id;
    this.version = version;
    this.path = path;
    if (path === undefined || path === null || path === '') {
      this.url = `/pipeline/${id}/sources?version=${version}${recursive ? '&recursive=true' : ''}`;
    } else {
      this.url = `/pipeline/${id}/sources?version=${version}&path=${path}${recursive ? '&recursive=true' : ''}`;
    }
  }
}

export default Source;
