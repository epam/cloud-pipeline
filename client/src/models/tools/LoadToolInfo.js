/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';

const repeatInterval = 5000;

export default class LoadToolInfo extends Remote {
  constructor (id) {
    super();
    this.url = `/tool/${id}/info`;
  }

  refreshData;

  clearInterval () {
    if (this.refreshData) {
      clearInterval(this.refreshData);
      delete this.refreshData;
      this.refreshData = null;
    }
  }

  startInterval () {
    if (!this.refreshData) {
      this.refreshData = setInterval(::this.fetch, repeatInterval);
    }
  }

  @computed
  get isUpdating () {
    return !!this.refreshData;
  }
}
