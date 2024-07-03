/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

const StorageMetrics = {
  costs: 'costs',
  volume: 'volume'
};

const InstanceMetrics = {
  costs: 'costs',
  computeCosts: 'compute-costs',
  diskCosts: 'disk-cost',
  usage: 'usage',
  runs: 'runs'
};

export function getBillingGroupingSortField (metrics) {
  switch (metrics) {
    case StorageMetrics.volume:
      return 'USAGE';
    default:
      return 'COST';
  }
}

export function parseStorageMetrics (metrics) {
  if (/^volume$/i.test(metrics)) {
    return StorageMetrics.volume;
  }
  return StorageMetrics.costs;
}

export function parseInstanceMetrics (metrics) {
  const found = Object
    .values(InstanceMetrics || {})
    .find((o) => o === (metrics || '').toLowerCase());
  return found || InstanceMetrics.costs;
}

export function getInstanceBillingOrderMetricsField (metrics) {
  switch (metrics) {
    case InstanceMetrics.usage:
      return 'USAGE_RUNS';
    case InstanceMetrics.runs:
      return 'COUNT_RUNS';
    case InstanceMetrics.computeCosts:
    case InstanceMetrics.diskCosts:
    case InstanceMetrics.costs:
    default:
      return 'COST';
  }
}

export function getInstanceBillingOrderAggregateField (metrics) {
  switch (metrics) {
    case InstanceMetrics.computeCosts:
      return 'RUN_COMPUTE';
    case InstanceMetrics.diskCosts:
      return 'RUN_DISK';
    case InstanceMetrics.costs:
    default:
      return 'RUN';
  }
}

export function getInstanceMetricsName (metrics) {
  switch (metrics) {
    case InstanceMetrics.usage:
      return 'Usage (hours)';
    case InstanceMetrics.runs:
      return 'Runs (count)';
    case InstanceMetrics.computeCosts:
      return 'Compute costs';
    case InstanceMetrics.diskCosts:
      return 'Disk costs';
    case InstanceMetrics.costs:
    default:
      return 'Costs';
  }
}

export {StorageMetrics, InstanceMetrics};
