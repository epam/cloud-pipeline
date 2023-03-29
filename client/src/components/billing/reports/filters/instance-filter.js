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

import React from 'react';
import {Radio} from 'antd';
import {getInstanceMetricsName, InstanceMetrics} from '../../navigation/metrics';

const filters = {
  [InstanceMetrics.costs]: {
    dataSample: 'value',
    previousDataSample: 'previous'
  },
  [InstanceMetrics.computeCosts]: {
    dataSample: 'costDetails.computeCost',
    previousDataSample: 'previousCostDetails.computeCost',
    title: getInstanceMetricsName(InstanceMetrics.computeCosts)
  },
  [InstanceMetrics.diskCosts]: {
    dataSample: 'costDetails.diskCost',
    previousDataSample: 'previousCostDetails.diskCost',
    title: getInstanceMetricsName(InstanceMetrics.diskCosts)
  },
  [InstanceMetrics.usage]: {
    dataSample: 'usage',
    previousDataSample: 'previousUsage',
    title: getInstanceMetricsName(InstanceMetrics.usage)
  },
  [InstanceMetrics.runs]: {
    dataSample: 'runsCount',
    previousDataSample: 'previousRunsCount',
    title: getInstanceMetricsName(InstanceMetrics.runs)
  }
};

function getSummaryDatasets (metrics) {
  const getValue = (property, costDetails) => {
    if (!costDetails) {
      return undefined;
    }
    return costDetails[property];
  };
  const currentValue = (item, property) => getValue(property, item.costDetails);
  const previousValue = (item, property) => getValue(property, item.previousCostDetails);
  let current = (item) => item.value;
  let currentFact = (item) => item.cost;
  let previous = (item) => item.previous;
  let previousFact = (item) => item.previousCost;
  switch (metrics) {
    case InstanceMetrics.computeCosts:
      current = (item) => currentValue(item, 'accumulatedComputeCost');
      currentFact = (item) => currentValue(item, 'computeCost');
      previous = (item) => previousValue(item, 'accumulatedComputeCost');
      previousFact = (item) => previousValue(item, 'computeCost');
      break;
    case InstanceMetrics.diskCosts:
      current = (item) => currentValue(item, 'accumulatedDiskCost');
      currentFact = (item) => currentValue(item, 'diskCost');
      previous = (item) => previousValue(item, 'accumulatedDiskCost');
      previousFact = (item) => previousValue(item, 'diskCost');
      break;
    default:
      break;
  }
  const currentDataset = {
    accumulative: {
      value: current,
      options: {
        borderWidth: 3
      }
    },
    fact: {
      value: currentFact
    }
  };
  const previousDataset = {
    accumulative: {
      value: previous,
      options: {
        isPrevious: true
      }
    },
    fact: {
      value: previousFact,
      options: {
        isPrevious: true
      }
    }
  };
  return [
    currentDataset,
    previousDataset
  ];
}

export default function ({onChange, value}) {
  let correctedValue = value;
  if (InstanceMetrics.computeCosts === value || InstanceMetrics.diskCosts === value) {
    correctedValue = InstanceMetrics.costs;
  }
  const onRadioGroupValueChange = (event) => {
    const newValue = event.target.value;
    if (newValue !== correctedValue) {
      onChange(newValue);
    }
  };
  return (
    <Radio.Group
      value={correctedValue}
      onChange={onRadioGroupValueChange}
      size="small"
    >
      <Radio.Button
        key={InstanceMetrics.costs}
        value={InstanceMetrics.costs}
      >
        Cost
      </Radio.Button>
      <Radio.Button
        key={InstanceMetrics.usage}
        value={InstanceMetrics.usage}
      >
        Usage (hours)
      </Radio.Button>
      <Radio.Button
        key={InstanceMetrics.runs}
        value={InstanceMetrics.runs}
      >
        Runs
      </Radio.Button>
    </Radio.Group>
  );
}

export {
  filters as InstanceFilters,
  getSummaryDatasets
};
