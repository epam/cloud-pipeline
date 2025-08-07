/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import {getColor} from './colors';

function bytesToGiB (bytes) {
  return bytes && !Number.isNaN(Number(bytes)) ? Number(bytes) / (2 ** 30) : 0;
}

export const HARDWARE_MAPPING = {
  gpu: {
    title: 'GPU',
    key: 'activeGPUCount'
  },
  gpuPending: {
    title: 'GPU pending',
    key: 'pendingGPUCount',
    type: 'pending'
  },
  gpuLimit: {
    title: 'Total GPU',
    key: 'totalGPUCount',
    type: 'total'
  },
  cpu: {
    title: 'CPU',
    key: 'activeCPUCount'
  },
  cpuPending: {
    title: 'CPU pending',
    key: 'pendingCPUCount',
    type: 'pending'
  },
  cpuLimit: {
    title: 'Total CPU',
    key: 'totalCPUCount',
    type: 'total'
  },
  ram: {
    title: 'RAM',
    key: 'activeMemoryCount',
    valueFormatter: bytesToGiB
  },
  ramPending: {
    title: 'RAM pending',
    key: 'pendingMemoryCount',
    type: 'pending',
    valueFormatter: bytesToGiB
  },
  ramLimit: {
    title: 'Total RAM',
    key: 'totalMemoryCount',
    type: 'total',
    valueFormatter: bytesToGiB
  },
  runs: {
    title: 'Jobs',
    key: 'activeRunsCount'
  },
  runsPending: {
    title: 'Pending jobs',
    key: 'pendingRunsCount',
    type: 'pending'
  }
};

export function extractHardwareData (
  data,
  mappings = [],
  colors,
  lineColor
) {
  if (!data) {
    return {
      datasets: [],
      entries: [],
      labels: []
    };
  }
  const records = data.originalRecords.filter(record => !!record.measureTime);
  const revertedRecords = records.slice().reverse();
  const extractData = (label, key, type, valueFormatter) => {
    const record = records.find(record => record.measureTime === label);
    let value = record?.[key] || 0;
    if (type === 'total' && value <= 0) {
      const lastNonNullRecord = revertedRecords.find(record => record[key] > 0);
      value = lastNonNullRecord ? lastNonNullRecord[key] : undefined;
    }
    if (value === undefined || value <= 0) {
      return undefined;
    }
    return valueFormatter ? valueFormatter(value) : value;
  };
  const labels = records.map(record => record.measureTime);
  const datasets = mappings.map(({key, title, type = 'active', valueFormatter}, index) => ({
    label: title,
    data: labels.map(label => extractData(label, key, type, valueFormatter)),
    backgroundColor: colors[type],
    borderColor: type === 'pending'
      ? lineColor
      : colors[type],
    fill: false,
    borderWidth: type === 'total' ? 3 : 1,
    type: type === 'total' ? 'line' : 'bar',
    pointRadius: 0,
    order: type === 'total' ? 1 : 2
  }));
  let max = 1;
  for (let i = 0; i < labels.length; i++) {
    const v = datasets.reduce((acc, d) => acc + (d.data || [])[i] || 0, 0);
    if (v > max) {
      max = v;
    }
  }
  return {
    datasets,
    entries: labels,
    labels: labels.map(label => label),
    max
  };
}
