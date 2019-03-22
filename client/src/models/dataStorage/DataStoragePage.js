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

class DataStoragePage extends Remote {
  url;

  id;
  path;
  type;

  constructor (id, path, showVersion, pageSize, marker) {
    super();
    this.id = id;
    this.path = path;
    this.pageSize = pageSize;
    this.showVersion = showVersion;
    if (this.path) {
      this.url = `/datastorage/${this.id}/list/page?path=${this.path}&showVersion=${!!showVersion}&pageSize=${pageSize}${marker ? `&marker=${marker}` : ''}`;
    } else {
      this.url = `/datastorage/${this.id}/list/page?showVersion=${!!showVersion}&pageSize=${pageSize}${marker ? `&marker=${marker}` : ''}`;
    }
  };

  async fetchPage (marker) {
    if (this.path) {
      this.url = `/datastorage/${this.id}/list/page?path=${this.path}&showVersion=${!!this.showVersion}&pageSize=${this.pageSize}${marker ? `&marker=${marker}` : ''}`;
    } else {
      this.url = `/datastorage/${this.id}/list/page?showVersion=${!!this.showVersion}&pageSize=${this.pageSize}${marker ? `&marker=${marker}` : ''}`;
    }
    this._pending = true;
    await super.fetch();
  }
}

export default DataStoragePage;
