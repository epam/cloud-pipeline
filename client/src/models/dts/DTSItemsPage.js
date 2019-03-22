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

class DTSItemsPage extends Remote {
  url;

  id;
  path;

  constructor (id, prefix, path, pageSize, marker) {
    super();
    this.id = id;
    this.prefix = prefix;
    this.path = path;
    this.pageSize = pageSize;
    let itemPath;
    if (this.path) {
      itemPath = `${this.prefix}/${this.path}`.replace(/\/\//g, '/');
    } else {
      itemPath = this.prefix;
    }
    this.url = `/dts/list/${this.id}?path=${encodeURIComponent(itemPath)}&pageSize=${this.pageSize}${marker ? `&marker=${marker}` : ''}`;
  };

  async fetchPage (marker) {
    let path;
    if (this.path) {
      path = `${this.prefix}/${this.path}`.replace(/\/\//g, '/');
    } else {
      path = this.prefix;
    }
    this.url = `/dts/list/${this.id}?path=${encodeURIComponent(path)}&pageSize=${this.pageSize}${marker ? `&marker=${marker}` : ''}`;
    this._pending = true;
    await super.fetch();
  }
}

export default DTSItemsPage;
