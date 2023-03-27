/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import {computed, observable, action} from 'mobx';

class TicketsFilter {
  constructior () {
  };

  @observable
  _filters = {
    title: '',
    statuses: []
  };

  @computed
  get filters () {
    return this._filters;
  }

  @action
  setTitle = (value) => {
    this._filters.title = value;
  };

  @action
  setStatuses = (value) => {
    this._filters.statuses = value;
  };

  @action
  clear = () => {
    this._filters.title = '';
    this._filters.statuses = [];
  };
}

const ticketsFilter = new TicketsFilter();

export {ticketsFilter};
