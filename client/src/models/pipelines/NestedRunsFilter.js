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
import {observable, computed} from 'mobx';

const REFRESH_INTERVAL = 5000;

class PipelineRunFilter extends RemotePost {
  static defaultValue = [];
  static auto = false;
  refreshInterval;

  constructor (params, loadLinks = false) {
    super();
    this.params = params;
    this.url = `/run/filter?loadLinks=${loadLinks}`;
    this.fetch();
  };

  startRefreshInterval () {
    if (!this.refreshInterval) {
      this.refreshInterval = setInterval(() => this.fetch(), REFRESH_INTERVAL);
    }
  }

  clearRefreshInterval () {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
      delete this.refreshInterval;
    }
  }

  @observable _total = 0;
  @computed
  get total () {
    return this._total;
  }

  filter (params) {
    this.params = params;
    this.fetch();
  }

  async fetch () {
    return super.send(this.params);
  }

  postprocess (value) {
    this._total = value.payload.totalCount;
    if (value.payload.totalCount === 0) { return []; }
    return value.payload.elements;
  }
}

export default PipelineRunFilter;
