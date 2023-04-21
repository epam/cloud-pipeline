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

class VersionFile extends Remote {
  constructor (id, path, version, byteLimit = undefined) {
    super();
    const url = `/pipeline/${id}/file`;
    if (byteLimit && !Number.isNaN(Number(byteLimit))) {
      // eslint-disable-next-line max-len
      this.url = `${url}/truncate?version=${version}&path=${encodeURIComponent(path)}&byteLimit=${byteLimit}`;
    } else {
      this.url = `${url}?version=${version}&path=${encodeURIComponent(path)}`;
    }
    this.path = path;
    this.version = version;
  }
}

export default VersionFile;
