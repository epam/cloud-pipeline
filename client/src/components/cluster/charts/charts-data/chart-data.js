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
import moment from 'moment-timezone';
import NodeUsage from '../../../../models/cluster/ClusterNodeUsage';

function makePromise (node, from, to) {
  return new Promise(async (resolve) => {
    const fromValue = from
      ? moment.unix(from).utc().format('YYYY-MM-DD HH:mm:ss')
      : undefined;
    const toValue = to
      ? moment.unix(to).utc().format('YYYY-MM-DD HH:mm:ss')
      : undefined;
    const request = new NodeUsage(node, fromValue, toValue);
    await request.fetchIfNeededOrWait();
    resolve({
      error: request.error,
      value: request.value
    });
  });
}

function joinArrays (arrays) {
  const result = (arrays || []).reduce((result, array) => ([...result, ...(array || [])]), []);
  result.sort((a, b) => {
    const {startTime: startA} = a;
    const {startTime: startB} = b;
    return moment(startA).unix() - moment(startB).unix();
  });
  return result;
}

async function loadData (node, from, to) {
  const now = moment().unix()
  const toCorrected = to || now;
  const promises = [
    makePromise(node, from, toCorrected)
  ];
  if (from && toCorrected - from > 0) {
    promises.push(makePromise(node, from - (toCorrected - from), from));
  }
  if (toCorrected && toCorrected < now) {
    let range = now - toCorrected;
    if (from && toCorrected - from < range) {
      range = toCorrected - from;
    }
    promises.push(makePromise(node, toCorrected, toCorrected + range));
  }
  const results = await Promise.all(promises);
  const [error] = results.map(r => r.error).filter(Boolean);
  const values = results.map(r => r.value);
  if (error) {
    return {
      error: error,
      from,
      to
    };
  }
  return {
    value: joinArrays(values),
    from,
    to
  };
}

class ChartData {
  @observable data = {};
  @observable groups = [];
  @observable xPoints = [];
  @observable xMin = 0;
  @observable xMax = 0;

  @observable noData = true;

  @observable _pending = true;
  @observable error;
  @observable instanceFrom;
  @observable instanceTo;
  @observable from;
  @observable to;

  @observable ranges = {};
  listeners = [];

  nodeName;

  @computed
  get pending () {
    return this._pending;
  }

  set pending (value) {
    this._pending = value;
  }

  constructor (nodeName, instanceFrom, instanceTo) {
    this.nodeName = nodeName;
    this.instanceFrom = instanceFrom;
    this.instanceTo = instanceTo;
    this.from = instanceFrom;
    this.to = instanceTo;
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

  @action
  loadData = () => {
    this.pending = true;
    loadData(this.nodeName, this.from, this.to)
      .then(({error, from, to, value}) => {
        if (from !== this.from || to !== this.to) {
          return;
        }
        this.error = error;
        if (!error) {
          this.processValues(value || []);
        }
        this.pending = false;
      });
  };

  @action
  async fetch () {
    return this.loadData();
  }

  @action
  updateRange = () => {
    this.instanceTo = moment().unix();
  };

  correctDateToFixRange = (unixDateTime) => {
    if (!unixDateTime) {
      return unixDateTime;
    }
    return Math.max(
      this.instanceFrom || -Infinity,
      Math.min(this.instanceTo || Infinity, unixDateTime)
    );
  };

  @action
  processValues (values) {
    this.apply(values);
    this.updateRange();
    this.listeners.forEach(fn => fn(this));
  }

  getConfigForData (responseData) {
    return [{
      field: 'y',
      group: 'default',
      valueFn: o => o.y
    }];
  }

  @action apply = (responseData) => {
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
        max: observable(-Infinity)
      };
    };
    const data = config
      .map(g => g.group)
      .reduce((d, group) => ({[group]: makeEmptyData(), ...d}), {});
    const groups = Object.keys(data);
    groups.sort();
    const xPoints = [];
    for (let i = 0; i < responseData.length; i++) {
      const item = responseData[i];
      const x = moment(item.startTime).unix();
      xPoints.push(x);
      groups.forEach(groupName => {
        const group = data[groupName];
        const rules = config.filter(c => c.group === groupName);
        const values = rules.map(({field, valueFn}) => ({
          field,
          value: valueFn(item)
        }));
        group.min = Math.min(...values.map(v => v.value), group.min);
        group.max = Math.max(...values.map(v => v.value), group.max);
        group.data.push({
          ...(values.reduce((r, v) => ({...r, [v.field]: v.value}), {})),
          x
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
  getConfigForData (responseData) {
    return [{
      field: 'cpu',
      group: 'default',
      valueFn: o => o.cpuUsage.load
    }];
  }
}

class MemoryUsageData extends ChartData {
  getConfigForData (responseData) {
    return [{
      field: 'memory',
      group: 'default',
      valueFn: o => o.memoryUsage.usage / (1024 ** 2)
    }, {
      field: 'percent',
      group: 'percent',
      valueFn: o => o.memoryUsage.usage / o.memoryUsage.capacity * 100.0
    }, {
      field: 'usage',
      group: 'capacity',
      valueFn: o => o.memoryUsage.usage
    }, {
      field: 'capacity',
      group: 'capacity',
      valueFn: o => o.memoryUsage.capacity
    }];
  }
}

class NetworkUsageData extends ChartData {
  getConfigForData (responseData) {
    let interfaces = [];
    if (responseData && responseData.length > 0) {
      const {networkUsage} = responseData[0];
      const {statsByInterface} = networkUsage || {};
      interfaces = Object.keys(statsByInterface || {});
    }
    return interfaces.map(i => ([
      {
        field: 'rx',
        group: i,
        valueFn: o => (o.networkUsage?.statsByInterface || {})[i]?.rxBytes
      },
      {
        field: 'tx',
        group: i,
        valueFn: o => (o.networkUsage?.statsByInterface || {})[i]?.txBytes
      }
    ]))
      .reduce((r, a) => ([...r, ...a]), []);
  }
}

class FileSystemUsageData extends ChartData {
  getConfigForData (responseData) {
    let devices = [];
    if (responseData && responseData.length > 0) {
      const {disksUsage} = responseData[0];
      const {statsByDevices} = disksUsage || {};
      devices = Object.keys(statsByDevices || {});
    }
    return devices.map(d => ([
      {
        field: 'usage',
        group: d,
        valueFn: o => (o.disksUsage?.statsByDevices || {})[d]?.usableSpace
      },
      {
        field: 'capacity',
        group: d,
        valueFn: o => (o.disksUsage?.statsByDevices || {})[d]?.capacity
      }
    ]))
      .reduce((r, a) => ([...r, ...a]), []);
  }
}

export {ChartData, CPUUsageData, MemoryUsageData, NetworkUsageData, FileSystemUsageData};
