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

export default class PipelineRunSingleFilter extends RemotePost {
  static defaultValue = [];
  static auto = false;
  params;

  constructor (params, loadLinks = false) {
    super();
    this.params = params;
    this.url = `/run/filter?loadLinks=${loadLinks}`;
    this.filter();
  };

  @observable _total = 0;
  @computed
  get total () {
    return this._total;
  }

  async filter (params) {
    if (params) {
      this.params = params;
    }
    return this.send(params || this.params);
  }

  postprocess (value) {
    this._total = value.payload.totalCount;
    if (value.payload.totalCount === 0) { return []; }
    return value.payload.elements;
  }
}
