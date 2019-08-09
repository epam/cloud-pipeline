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
import RunTasks from './RunTasks';
import PipelineRunFilter from './PipelineRunFilter';
import displayDate from '../../utils/displayDate';
const repeatInterval = 5000;
const parseLog = (text, date) => (text.split('\n')
  .filter(String).map(s => date ? `[${date}] ${s}` : s));

class Run extends Remote {
  static defaultValue = [];
  refreshData;

  constructor (runId) {
    super();
    this.url = `/run/${runId}`;
  };

  clearInterval () {
    clearInterval(this.refreshData);
    delete this.refreshData;
  }

  postprocess (value) {
    if (
      (
        value.payload.status === 'RUNNING' ||
        value.payload.status === 'PAUSING' ||
        value.payload.status === 'RESUMING') &&
      !this.refreshData) {
      this.refreshData = setInterval(::this.silentFetch, repeatInterval);
    } else if (value.payload.status !== 'RUNNING' &&
      value.payload.status !== 'PAUSING' &&
      value.payload.status !== 'RESUMING') {
      this.clearInterval();
    }
    return value.payload;
  }
}

class Log extends Remote {
  static defaultValue = [];

  constructor (runId, taskName, parameters) {
    super();
    if (taskName) {
      const addParams = parameters && `&${parameters}`;
      this.url = `/run/${runId}/task?taskName=${taskName}${addParams}`;
    } else {
      this.url = `/run/${runId}/logs`;
    }
  };

  refreshData;
  onDataReceived;

  init () {
    this.clearInterval();
  }

  clearInterval () {
    clearInterval(this.refreshData);
    delete this.refreshData;
  }

  startInterval (value) {
    if (!this.refreshData) {
      this.refreshData = setInterval(async () => {
        await this.silentFetch();
        if (this.onDataReceived) {
          this.onDataReceived();
        }
      }, repeatInterval);
    }
  }

  postprocess (value) {
    const result = [];
    value.payload && value.payload.forEach(log => {
      if (log.logText) {
        const {date} = log;
        result.push(...parseLog(log.logText, displayDate(date)));
      }
    });
    return result;
  };
}

class PipelineRun extends Remote {
  /* eslint-disable */
  static getCache (cache, cacheName, model, ...params) {
    if (!cache.has(cacheName)) {
      cache.set(cacheName, new model(...params));
    }
    return cache.get(cacheName);
  }
  /* eslint-enable */

  runFilter = (params, loadLinks = false) => (new PipelineRunFilter(params, loadLinks));
  _logCache = new Map();

  logs (runId, taskName, parameters) {
    let cashName = `${runId}-${taskName}-${parameters}`;
    if (!this._logCache.has(cashName)) {
      this._logCache.set(cashName, new Log(runId, taskName, parameters));
    }
    return this._logCache.get(cashName);
  }

  _runRunIdCache = new Map();

  run (runId, params) {
    const {refresh} = params || {};
    if (!this._runRunIdCache.has(`${runId}`)) {
      this._runRunIdCache.set(`${runId}`, new Run(`${runId}`));
    } else {
      if (refresh) {
        this._runRunIdCache.get(`${runId}`).silentFetch();
        return this._runRunIdCache.get(`${runId}`);
      }
    }

    return this._runRunIdCache.get(`${runId}`);
  }

  _runRunIdTasksCache = new Map();
  _nestedRunsCache = new Map();

  runTasks (runId) {
    return this.constructor.getCache(this._runRunIdTasksCache, runId, RunTasks, runId);
  }
  nestedRuns (runId, count) {
    return this.constructor.getCache(
      this._nestedRunsCache,
      `${runId}`,
      PipelineRunFilter,
      {
        page: 1,
        pageSize: count,
        parentId: runId,
        userModified: true
      },
      false
    );
  }
}

export default new PipelineRun();
