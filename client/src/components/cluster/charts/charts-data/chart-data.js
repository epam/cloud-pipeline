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

import {action, observable} from 'mobx';
import moment from 'moment';

class ChartData {
  @observable data = [];
  @observable groups = [];
  @observable xMin = 0;
  @observable xMax = 0;

  @observable noData = true;

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
    for (let i = 0; i < responseData.length; i++) {
      const item = responseData[i];
      const x = moment(item.startTime).unix();
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

class FileSystemUsage extends ChartData {
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

export {CPUUsageData, MemoryUsageData, NetworkUsageData, FileSystemUsage};
export default ChartData;
