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

export const HARDWARE_MAPPING = {
  gpu: {
    title: 'GPU',
    key: 'gpu'
  },
  gpuPending: {
    title: 'GPU pending',
    key: 'gpuPending',
    type: 'pending'
  },
  cpu: {
    title: 'CPU',
    key: 'cpu'
  },
  cpuPending: {
    title: 'CPU pending',
    key: 'cpuPending',
    type: 'pending'
  },
  ram: {
    title: 'RAM',
    key: 'ram'
  },
  ramPending: {
    title: 'RAM pending',
    key: 'ramPending',
    type: 'pending'
  }
};

const CAPACITY_MOCK = 18;

export function extractHardwareData (
  data,
  mappings = [],
  colors,
  lineColor,
  limitColor,
  backgroundColor
) {
  if (!data) {
    return {
      datasets: [],
      entries: [],
      labels: []
    };
  }
  const records = data.records.filter(record => !!record.measureTime);
  const extractData = (records, label, key) => {
    const record = records.find(record => record.measureTime === label);
    // todo: delete mock
    return Math.ceil(Math.random() * 10);
    return record?.[key] || 0;
  };
  const labels = records.map(record => record.measureTime);
  const datasets = mappings.map(({key, title, type}, index) => ({
    label: title,
    data: labels.map(label => extractData(records, label, key)),
    backgroundColor: type === 'pending'
      ? backgroundColor
      : getColor(index, colors),
    borderColor: type === 'pending'
      ? lineColor
      : getColor(index, colors),
    fill: false,
    borderWidth: 1
  }));
  const capacity = CAPACITY_MOCK;
  return {
    datasets: [...datasets, {
      type: 'line',
      label: 'Capacity',
      borderColor: limitColor,
      fill: false,
      data: labels.map(l => capacity),
      pointRadius: 0
    }],
    entries: labels,
    labels: labels.map(label => label)
  };
}
