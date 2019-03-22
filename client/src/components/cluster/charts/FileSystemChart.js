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

import UsageChart from './UsageChart';

export default class FileSystemChart extends UsageChart {

  static noDataAvailableMessage = 'File System usage is unavailable';
  static title = 'File System';

  static postfixes = ['',
    'KB',
    'MB',
    'GB',
    'TB',
    'PB',
    'EB'
  ];

  transformEntries (entries) {
    if (entries) {
      const devices = entries.map(
        entry => {
          if (entry.disksUsage && entry.disksUsage.statsByDevices) {
            const statsByDevices = entry.disksUsage.statsByDevices;
            const keys = [];
            for (let key in statsByDevices) {
              if (statsByDevices.hasOwnProperty(key)) {
                keys.push(key);
              }
            }
            return keys;
          } else {
            return [];
          }
        }
      ).reduce((keys, currentKeys) => {
        for (let i = 0; i < currentKeys.length; i++) {
          if (keys.indexOf(currentKeys[i]) === -1) {
            keys.push(currentKeys[i]);
          }
        }
        return keys;
      }, []);
      const itemsWithDevices = entries
        .filter(entry =>
          entry.disksUsage &&
          entry.disksUsage.statsByDevices
        );
      if (itemsWithDevices.length === 0) {
        return;
      }
      devices.sort((a, b) => {
        if (a < b) {
          return -1;
        } else if (a > b) {
          return 1;
        }
        return 0;
      });
      const lastEntry = itemsWithDevices[itemsWithDevices.length - 1];
      const entryData = {};
      for (let i = 0; i < devices.length; i++) {
        const stats = lastEntry.disksUsage.statsByDevices[devices[i]];
        const value = Math.round(stats.usableSpace / stats.capacity * 10000.0) / 100.0;
        entryData[devices[i]] = {
          value: value,
          freeValue: 100.0 - value,
          label: this.getUsageDescription(stats.usableSpace, stats.capacity, value)
        };
      }
      return {
        labels: devices.map((device, index) => `FS ${index + 1}: ${device}`),
        keys: devices,
        entryData: entryData
      };
    }
    return undefined;
  };
}
