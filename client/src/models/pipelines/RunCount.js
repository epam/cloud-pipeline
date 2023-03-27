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

import {action, computed, observable} from 'mobx';
import RemotePost from '../basic/RemotePost';
import preferencesLoad from '../preferences/PreferencesLoad';
import continuousFetch from '../../utils/continuous-fetch';

const DEFAULT_STATUSES = [
  'RUNNING',
  'PAUSED',
  'PAUSING',
  'RESUMING'
];

class UserRunCount extends RemotePost {
  static fetchOptions = {
    headers: {
      'Content-type': 'application/json; charset=UTF-8'
    },
    mode: 'cors',
    credentials: 'include',
    method: 'POST'
  };

  url = '/run/count';

  constructor (user, statuses, countChildNodes = false) {
    super();
    this.user = user;
    this.countChildNodes = countChildNodes;
    this.statuses = statuses || DEFAULT_STATUSES;
  }

  fetch () {
    return super.send({
      statuses: this.statuses,
      userModified: this.countChildNodes,
      eagerGrouping: false,
      owners: this.user ? [this.user] : undefined
    });
  }
}

class RunCount extends RemotePost {
  @observable usePreferenceValue = false;
  @observable onlyMasterJobs = true;
  @observable statuses = DEFAULT_STATUSES;

  @observable _runsCount = 0;

  listeners = [];

  /**
   * @typedef {Object} RunCounterOptions
   * @property {boolean} [usePreferenceValue=false]
   * @property {string[]} [statuses]
   * @property {boolean} [onlyMasterJobs=true]
   * @property {boolean} [autoUpdate=false]
   */

  /**
   * @param {RunCounterOptions} [options]
   */
  constructor (options) {
    super();
    this.url = '/run/count';
    const {
      usePreferenceValue,
      statuses = DEFAULT_STATUSES,
      onlyMasterJobs = true,
      autoUpdate
    } = options || {};
    this.statuses = statuses;
    this.onlyMasterJobs = onlyMasterJobs;
    this.usePreferenceValue = usePreferenceValue;
    if (autoUpdate) {
      continuousFetch({request: this});
    }
  }

  addListener = (listener) => {
    this.removeListener(listener);
    this.listeners.push(listener);
  };

  removeListener = (listener) => {
    this.listeners = this.listeners.filter((aListener) => aListener !== listener);
  };

  @computed
  get isDefault () {
    try {
      const currentStatuses = new Set(this.statuses);
      if (currentStatuses.size !== DEFAULT_STATUSES.length) {
        return false;
      }
      for (const status of DEFAULT_STATUSES) {
        if (!currentStatuses.has(status)) {
          return false;
        }
      }
      return this.onlyMasterJobs;
    } catch (_) {
      // empty
    }
    return true;
  }

  /**
   * @param {RunCount} otherRequest
   * @returns {boolean}
   */
  filtersEquals (otherRequest) {
    if (!otherRequest) {
      return false;
    }
    try {
      const currentStatuses = new Set(this.statuses);
      if (currentStatuses.size !== otherRequest.statuses.length) {
        return false;
      }
      for (const status of otherRequest.statuses) {
        if (!currentStatuses.has(status)) {
          return false;
        }
      }
      return this.onlyMasterJobs === otherRequest.onlyMasterJobs;
    } catch (_) {
      // empty
    }
    return true;
  }

  @computed
  get runsCount () {
    return this._runsCount || 0;
  }

  async fetch () {
    if (this.usePreferenceValue) {
      await preferencesLoad.fetchIfNeededOrWait();
      const {
        statuses = this.statuses,
        onlyMasterJobs = this.onlyMasterJobs
      } = preferencesLoad.uiRunsCounterFilter || {};
      this.statuses = statuses;
      this.onlyMasterJobs = onlyMasterJobs;
    }
    await super.send({
      statuses: this.statuses || ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'],
      userModified: !this.onlyMasterJobs,
      eagerGrouping: false
    });
    this._runsCount = this.value;
    (this.listeners || [])
      .filter((aListener) => typeof aListener === 'function')
      .forEach((aListener) => aListener(this.value));
  }
}

class RunCountDefault extends RunCount {
  /**
   * @param {RunCount} globalCounter
   * @param {{statuses: string[], onlyMasterJobs: boolean}} [filters]
   */
  constructor (globalCounter, filters = {}) {
    const {
      statuses = DEFAULT_STATUSES,
      onlyMasterJobs = true
    } = filters;
    super({
      autoUpdate: false,
      statuses,
      onlyMasterJobs,
      usePreferenceValue: false
    });
    this.globalCounter = globalCounter;
    this.updateFromGlobalCounter();
    if (globalCounter && globalCounter.filtersEquals(this)) {
      globalCounter.addListener(this.updateFromGlobalCounter);
    }
  }

  updateFromGlobalCounter = () => {
    if (this.globalCounter && this.globalCounter.filtersEquals(this)) {
      this._runsCount = this.globalCounter.runsCount;
    }
  };

  destroy () {
    if (this.globalCounter) {
      this.globalCounter.removeListener(this.updateFromGlobalCounter);
    }
  }

  @action
  async fetch () {
    if (this.globalCounter && this.globalCounter.filtersEquals(this)) {
      this._runsCount = this.globalCounter.runsCount;
      return;
    }
    await super.fetch();
  }
}

export {RunCountDefault, UserRunCount};
export default RunCount;
