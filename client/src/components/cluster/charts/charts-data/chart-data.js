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

import {observable, computed, action, makeObservable} from 'mobx';
import dayjs from '../../../../utils/dayjs';
import NodeUsage from '../../../../models/cluster/ClusterNodeUsage';
import {alphabeticalSorter} from '../../../../utils/sorting';

async function makePromise(node, from, to, runId) {
  const fromValue = from ? dayjs.unix(from).utc().format('YYYY-MM-DD HH:mm:ss') : undefined;
  const toValue = to ? dayjs.unix(to).utc().format('YYYY-MM-DD HH:mm:ss') : undefined;
  const request = new NodeUsage(node, fromValue, toValue, runId);
  await request.fetchIfNeededOrWait();
  return {
    error: request.error,
    networkError: request.networkError,
    value: request.value,
  };
}

function sortData(a, b) {
  const {startTime: startA} = a;
  const {startTime: startB} = b;
  return dayjs(startA).unix() - dayjs(startB).unix();
}

async function loadData(node, from, to, instanceFrom, instanceTo, runId) {
  const now = dayjs().unix();
  let toCorrected = to || now;
  let fromCorrected = from;
  if (from && toCorrected - from > 0) {
    fromCorrected = from - (toCorrected - from);
  }
  if (toCorrected && toCorrected < now) {
    let range = now - toCorrected;
    if (from && toCorrected - from < range) {
      range = toCorrected - from;
    }
    toCorrected = toCorrected + range;
  }
  if (instanceFrom) {
    toCorrected = Math.max(instanceFrom, toCorrected);
    fromCorrected = Math.max(instanceFrom, fromCorrected);
  }
  if (instanceTo) {
    toCorrected = Math.min(instanceTo, toCorrected);
    fromCorrected = Math.min(instanceTo, fromCorrected);
  }
  const {
    value = [],
    error,
    networkError,
  } = await makePromise(node, fromCorrected, toCorrected, runId);
  if (error) {
    return {
      error,
      networkError,
      from,
      to,
    };
  }
  return {
    value: value.sort(sortData),
    from,
    to,
  };
}

class ChartData {
  data = {};
  groups = [];
  xPoints = [];
  xMin = 0;
  xMax = 0;
  noData = true;
  _pending = true;
  error;
  networkError;
  instanceFrom;
  instanceTo;
  from;
  to;
  rangeEndIsFixed = false;
  _refreshToken = 0;
  ranges = {};
  listeners = [];
  nodeName;

  get pending() {
    return this._pending;
  }

  get refreshToken() {
    return this._refreshToken;
  }

  set pending(value) {
    this._pending = value;
  }

  constructor(nodeName, instanceFrom, instanceTo, runId) {
    makeObservable(this, {
      data: observable,
      groups: observable,
      xPoints: observable,
      xMin: observable,
      xMax: observable,
      noData: observable,
      _pending: observable,
      error: observable,
      networkError: observable,
      instanceFrom: observable,
      instanceTo: observable,
      from: observable,
      to: observable,
      rangeEndIsFixed: observable,
      _refreshToken: observable,
      ranges: observable,
      listeners: observable,
      nodeName: observable,
      registerListener: action,
      unRegisterListener: action,
      loadData: action,
      fetch: action,
      updateRange: action,
      processValues: action,
      apply: action,
    });
    this.nodeName = nodeName;
    this.instanceFrom = instanceFrom;
    this.instanceTo = instanceTo;
    this.from = instanceFrom;
    this.to = instanceTo;
    this.runId = runId;
    this.rangeEndIsFixed = !!instanceTo;
  }

  registerListener = (listener) => {
    const index = this.listeners.indexOf(listener);
    if (index === -1) {
      this.listeners.push(listener);
    }
  };

  unRegisterListener = (listener) => {
    const index = this.listeners.indexOf(listener);
    if (index >= 0) {
      this.listeners.splice(index, 1);
    }
  };

  loadData = () => {
    this.pending = true;
    return new Promise((resolve) => {
      loadData(this.nodeName, this.from, this.to, this.instanceFrom, this.instanceTo, this.runId)
        .then(({error, networkError, from, to, value}) => {
          this._refreshToken += 1;
          if (from !== this.from || to !== this.to) {
            return;
          }
          this.error = error;
          this.networkError = error;
          if (!error) {
            this.processValues(value || []);
          }
          this.pending = false;
        })
        .then(() => resolve());
    });
  };

  async fetch() {
    return this.loadData();
  }

  updateRange = () => {
    if (!this.rangeEndIsFixed) {
      this.instanceTo = dayjs().unix();
    }
  };

  correctDateToFixRange = (unixDateTime) => {
    if (!unixDateTime) {
      return unixDateTime;
    }
    return Math.max(
      this.instanceFrom || -Infinity,
      Math.min(this.instanceTo || Infinity, unixDateTime),
    );
  };

  processValues(values) {
    this.apply(values);
    this.updateRange();
    this.listeners.forEach((fn) => fn(this));
  }

  getConfigForData(responseData) {
    return [
      {
        field: 'y',
        group: 'default',
        valueFn: (o) => o.y,
      },
    ];
  }

  apply = (responseData) => {
    if (!responseData || responseData.length === 0) {
      this.noData = true;
      return;
    }
    let xMin = Infinity;
    let xMax = -Infinity;
    const config = this.getConfigForData(responseData);
    const makeEmptyData = () => {
      return {
        data: observable([]),
        min: observable(Infinity),
        max: observable(-Infinity),
      };
    };
    const data = config
      .map((g) => g.group)
      .reduce((d, group) => ({[group]: makeEmptyData(), ...d}), {});
    const groups = Object.keys(data);
    groups.sort(alphabeticalSorter);
    const xPoints = [];
    for (let i = 0; i < responseData.length; i++) {
      const item = responseData[i];
      const x = dayjs(item.startTime).unix();
      xPoints.push(x);
      groups.forEach((groupName) => {
        const group = data[groupName];
        const rules = config.filter((c) => c.group === groupName);
        const values = rules
          .map(({field, valueFn}) => ({
            field,
            value: valueFn(item),
          }))
          .filter((v) => v.value !== undefined);
        group.min = Math.min(...values.map((v) => v.value), group.min);
        group.max = Math.max(...values.map((v) => v.value), group.max);
        group.data.push({
          ...values.reduce((r, v) => ({...r, [v.field]: v.value}), {}),
          x,
        });
      });
      xMin = Math.min(xMin, x);
      xMax = Math.max(xMax, x);
    }
    this.data = data;
    this.xPoints = xPoints;
    this.groups = groups;
    this.noData = false;
    this.xMin = xMin;
    this.xMax = xMax;
  };
}

class CPUUsageData extends ChartData {
  getConfigForData(responseData) {
    return [
      {
        field: 'cpu',
        group: 'default',
        valueFn: (o) => o.cpuUsage.load,
      },
      {
        field: 'cpuMax',
        group: 'default',
        valueFn: (o) => o.cpuUsage.max,
      },
    ];
  }
}

class MemoryUsageData extends ChartData {
  getConfigForData(responseData) {
    return [
      {
        field: 'memory',
        group: 'default',
        valueFn: (o) => o.memoryUsage.usage / 1024 ** 2,
      },
      {
        field: 'percent',
        group: 'percent',
        valueFn: (o) => (o.memoryUsage.usage / o.memoryUsage.capacity) * 100.0,
      },
      {
        field: 'memoryMax',
        group: 'default',
        valueFn: (o) => (o.memoryUsage.max ? o.memoryUsage.max / 1024 ** 2 : undefined),
      },
      {
        field: 'percentMax',
        group: 'percent',
        valueFn: (o) =>
          o.memoryUsage.max ? (o.memoryUsage.max / o.memoryUsage.capacity) * 100.0 : undefined,
      },
      {
        field: 'usage',
        group: 'capacity',
        valueFn: (o) => o.memoryUsage.usage,
      },
      {
        field: 'capacity',
        group: 'capacity',
        valueFn: (o) => o.memoryUsage.capacity,
      },
    ];
  }
}

class NetworkUsageData extends ChartData {
  getConfigForData(responseData) {
    let interfaces = [];
    if (responseData && responseData.length > 0) {
      const {networkUsage} = responseData[0];
      const {statsByInterface} = networkUsage || {};
      interfaces = Object.keys(statsByInterface || {});
    }
    return interfaces
      .map((i) => [
        {
          field: 'rx',
          group: i,
          valueFn: (o) => (o.networkUsage?.statsByInterface || {})[i]?.rxBytes,
        },
        {
          field: 'tx',
          group: i,
          valueFn: (o) => (o.networkUsage?.statsByInterface || {})[i]?.txBytes,
        },
      ])
      .reduce((r, a) => [...r, ...a], []);
  }
}

class FileSystemUsageData extends ChartData {
  getConfigForData(responseData) {
    let devices = [];
    if (responseData && responseData.length > 0) {
      const {disksUsage} = responseData[0];
      const {statsByDevices} = disksUsage || {};
      devices = Object.keys(statsByDevices || {});
    }
    return devices
      .map((d) => [
        {
          field: 'usage',
          group: d,
          valueFn: (o) => (o.disksUsage?.statsByDevices || {})[d]?.usableSpace,
        },
        {
          field: 'capacity',
          group: d,
          valueFn: (o) => (o.disksUsage?.statsByDevices || {})[d]?.capacity,
        },
      ])
      .reduce((r, a) => [...r, ...a], []);
  }
}

export {ChartData, CPUUsageData, MemoryUsageData, NetworkUsageData, FileSystemUsageData};
