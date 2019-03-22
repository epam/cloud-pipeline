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

export default class CurrentMemoryUsageChart extends UsageChart {

  static noDataAvailableMessage = 'Memory usage is unavailable';
  static title = null;

  updateChartOptions () {
    super.updateChartOptions();
    this.chart.options.title.display = false;
  };

  transformEntries (entries) {
    if (entries && entries.length > 0) {
      const lastEntry = entries[entries.length - 1];
      const value = Math.round(
          lastEntry.memoryUsage.usage / lastEntry.memoryUsage.capacity * 10000.0
        ) / 100.0;
      const entryData = {
        usage: {
          value: value,
          freeValue: 100 - value,
          label: this.getUsageDescription(
            lastEntry.memoryUsage.usage,
            lastEntry.memoryUsage.capacity,
            value)
        }
      };
      return {
        labels: [''],
        keys: ['usage'],
        entryData: entryData
      };
    }
    return undefined;
  };
}
