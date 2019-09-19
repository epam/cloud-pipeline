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
import moment from 'moment';
import NodeUsage from '../../../../../models/cluster/ClusterNodeUsage';

async function loadData (node, from, to) {
  const fromValue = from
    ? moment.unix(from).utc().format('YYYY-MM-DD HH:mm:ss')
    : undefined;
  const toValue = to
    ? moment.unix(to).utc().format('YYYY-MM-DD HH:mm:ss')
    : undefined;
  const request = new NodeUsage(node, fromValue, toValue);
  await request.fetchIfNeededOrWait();
  if (request.error) {
    return {
      error: request.error,
      from,
      to
    };
  }
  return {
    value: request.value,
    from,
    to
  };
}

class ChartDataItem {
  @observable x;

  @observable y;

  static instance (usageItem) {
    return new this(usageItem);
  }

  static getDataRanges (data) {
    return {
      x: {
        max: Math.max(...(data || []).map(d => d.x)),
        min: Math.min(...(data || []).map(d => d.x))
      },
      y: {
        max: Math.max(...(data || []).map(d => d.y)),
        min: Math.min(...(data || []).map(d => d.y))
      }
    };
  }

  constructor (usageItem) {
    this.x = moment.utc(usageItem.startTime).unix();
    this.y = 0;
  }
}

class CPUUsageItem extends ChartDataItem {
  constructor (usageItem) {
    super(usageItem);
    this.y = usageItem.cpuUsage?.load;
  }
}

class MemoryUsageItem extends ChartDataItem {
  static getDataRanges (data) {
    return {
      x: {
        max: Math.max(...(data || []).map(d => d.x)),
        min: Math.min(...(data || []).map(d => d.x))
      },
      y: {
        max: Math.max(...(data || []).map(d => d.y)),
        min: Math.min(...(data || []).map(d => d.y))
      },
      percent: {
        max: Math.max(...(data || []).map(d => d.percent)),
        min: Math.min(...(data || []).map(d => d.percent))
      }
    };
  }

  constructor (usageItem) {
    super(usageItem);
    this.y = usageItem.memoryUsage?.usage / 1024 / 1024;
    this.percent = usageItem.memoryUsage?.usage / usageItem.memoryUsage?.capacity * 100.0;
    this.usage = usageItem.memoryUsage?.usage;
    this.capacity = usageItem.memoryUsage?.capacity;
  }
}

class NetworkUsageItem extends ChartDataItem {
  static getDataRanges (data) {
    const interfaceList = data && data.length ? Object.keys(data[0].stats) : [];
    return {
      x: {
        max: Math.max(...(data || []).map(d => d.x)),
        min: Math.min(...(data || []).map(d => d.x))
      },
      stats: {
        ...(interfaceList.map(networkInterface => ({
          [networkInterface]: {
            rxBytes: {
              max: Math.max(...(data || []).map(d => d.stats[networkInterface]?.rxBytes)),
              min: Math.min(...(data || []).map(d => d.stats[networkInterface]?.rxBytes))
            },
            txBytes: {
              max: Math.max(...(data || []).map(d => d.stats[networkInterface]?.txBytes)),
              min: Math.min(...(data || []).map(d => d.stats[networkInterface]?.txBytes))
            }
          }
        }))
          .reduce((result, current) => ({...result, ...current}), {}))
      }
    };
  }

  constructor (usageItem) {
    super(usageItem);
    this.stats = usageItem.networkUsage?.statsByInterface;
  }
}

class FileSystemUsageItem extends ChartDataItem {
  static getDataRanges (data) {
    const deviceList = data && data.length ? Object.keys(data[0].stats) : [];
    return {
      x: {
        max: Math.max(...(data || []).map(d => d.x)),
        min: Math.min(...(data || []).map(d => d.x))
      },
      stats: {
        ...(deviceList.map(device => ({
          [device]: {
            usableSpace: {
              max: Math.max(...(data || []).map(d => d.stats[device]?.usableSpace)),
              min: Math.min(...(data || []).map(d => d.stats[device]?.usableSpace))
            },
            capacity: {
              max: Math.max(...(data || []).map(d => d.stats[device]?.capacity)),
              min: Math.min(...(data || []).map(d => d.stats[device]?.capacity))
            }
          }
        }))
          .reduce((result, current) => ({...result, ...current}), {}))
      }
    };
  }

  constructor (usageItem) {
    super(usageItem);
    this.stats = usageItem.disksUsage?.statsByDevices;
  }
}

class ChartData {
  @observable _pending = true;
  @observable error;
  @observable data = [];
  @observable instanceFrom;
  @observable instanceTo;
  @observable from;
  @observable to;

  @observable ranges = {};

  nodeName;

  usageItemType;

  static UsageItemType = ChartDataItem;

  @computed
  get pending () {
    return this._pending;
  }

  set pending (value) {
    this._pending = value;
  }

  constructor (nodeName, instanceFrom) {
    this.nodeName = nodeName;
    this.instanceFrom = instanceFrom;
    this.from = instanceFrom;
  }

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
    const UsageItemType = this.constructor.UsageItemType;
    if (!UsageItemType || !UsageItemType.instance) {
      this.data.splice(0, this.data.length, ...values);
    } else {
      const data = (values || []).map(item => UsageItemType.instance(item));
      this.ranges = UsageItemType.getDataRanges(data);
      this.data.splice(0, this.data.length, ...data);
    }
    this.updateRange();
  }
}

class CPUUsageData extends ChartData {
  static UsageItemType = CPUUsageItem;
}

class MemoryUsageData extends ChartData {
  static UsageItemType = MemoryUsageItem;
}

class NetworkUsageData extends ChartData {
  static UsageItemType = NetworkUsageItem;
}

class FileSystemUsageData extends ChartData {
  static UsageItemType = FileSystemUsageItem;
}

export {
  ChartData,
  CPUUsageData,
  FileSystemUsageData,
  MemoryUsageData,
  NetworkUsageData
};
