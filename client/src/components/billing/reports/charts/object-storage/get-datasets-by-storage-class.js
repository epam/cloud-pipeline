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

import {costTickFormatter, numberFormatter} from '../../utilities';
import {LAYERS_KEYS} from '../../../../../models/billing';
import {StorageMetrics} from '../../../navigation/metrics';
import {StorageFilters} from '../../filters/storage-filter';
import {getStorageClassName} from '../../../navigation/aggregate';

const getCostDetailsValue = (costDetails, storageClass, key) => {
  if (
    !costDetails ||
    !costDetails.tiers ||
    !costDetails.tiers[storageClass]
  ) {
    return undefined;
  }
  return costDetails.tiers[storageClass][key];
};
const getCostDetailsSumm = (costDetails, storageClass, ...keys) => {
  const values = keys.map((key) => getCostDetailsValue(costDetails, storageClass, key));
  if (values.some((value) => value !== undefined && !Number.isNaN(Number(value)))) {
    return values.reduce((summ, value) => (summ + (value || 0)), 0);
  }
  return undefined;
};

export function getSummaryDatasetsByStorageClass (storageClass) {
  const currentDataset = {
    accumulative: {
      value: (item) => getCostDetailsSumm(
        item.costDetails,
        storageClass,
        LAYERS_KEYS.accumulativeCost
      ),
      options: {
        borderWidth: 3,
        tooltipValue: (currentValue, item) => {
          if (item) {
            const currentValue = getCostDetailsValue(
              item.costDetails,
              storageClass,
              LAYERS_KEYS.accumulativeCost
            ) || 0;
            const oldVersionsValue = getCostDetailsValue(
              item.costDetails,
              storageClass,
              LAYERS_KEYS.accumulativeOldVersionCost
            ) || 0;
            const current = costTickFormatter(currentValue);
            const oldVersion = costTickFormatter(oldVersionsValue);
            const total = costTickFormatter(currentValue + oldVersionsValue);
            return `${total} (current: ${current}; old versions: ${oldVersion})`;
          }
          return currentValue;
        }
      }
    },
    fact: {
      value: (item) => getCostDetailsSumm(
        item.costDetails,
        storageClass,
        'cost'
      ),
      options: {
        subTitle: 'cost',
        stack: 'current',
        tooltipValue: (currentValue, item) => {
          if (item) {
            const currentValue = getCostDetailsValue(
              item.costDetails,
              storageClass,
              LAYERS_KEYS.cost
            ) || 0;
            const oldVersionsValue = getCostDetailsValue(
              item.costDetails,
              storageClass,
              LAYERS_KEYS.oldVersionCost
            ) || 0;
            const current = costTickFormatter(currentValue);
            const oldVersion = costTickFormatter(oldVersionsValue);
            const total = costTickFormatter(currentValue + oldVersionsValue);
            return `${total} (current: ${current}; old versions: ${oldVersion})`;
          }
          return currentValue;
        }
      }
    }
  };

  const currentOldVersionsDataset = {
    accumulative: {
      value: (item) => getCostDetailsSumm(
        item.costDetails,
        storageClass,
        LAYERS_KEYS.accumulativeOldVersionCost
      ),
      options: {
        borderWidth: 2,
        backgroundColor: 'transparent',
        dashed: true,
        showTooltip: false
      }
    },
    fact: {
      value: (item) => getCostDetailsSumm(
        item.costDetails,
        storageClass,
        LAYERS_KEYS.oldVersionCost
      ),
      options: {
        borderWidth: 1,
        subTitle: 'cost',
        stack: 'current',
        backgroundColor: 'transparent',
        showTooltip: false
      }
    }
  };

  const previousDataset = {
    accumulative: {
      value: (item) => getCostDetailsSumm(
        item.previousCostDetails,
        storageClass,
        LAYERS_KEYS.accumulativeCost
      ),
      options: {
        isPrevious: true,
        tooltipValue: (currentValue, item) => {
          if (item) {
            const currentValue = getCostDetailsValue(
              item.previousCostDetails,
              storageClass,
              LAYERS_KEYS.accumulativeCost
            ) || 0;
            const oldVersionsValue = getCostDetailsValue(
              item.previousCostDetails,
              storageClass,
              LAYERS_KEYS.accumulativeOldVersionCost
            ) || 0;
            const current = costTickFormatter(currentValue);
            const oldVersion = costTickFormatter(oldVersionsValue);
            const total = costTickFormatter(currentValue + oldVersionsValue);
            return `${total} (current: ${current}; old versions: ${oldVersion})`;
          }
          return currentValue;
        }
      }
    },
    fact: {
      value: (item) => getCostDetailsSumm(
        item.previousCostDetails,
        storageClass,
        LAYERS_KEYS.cost
      ),
      options: {
        isPrevious: true,
        subTitle: 'cost',
        stack: 'previous',
        tooltipValue: (currentValue, item) => {
          if (item) {
            const currentValue = getCostDetailsValue(
              item.previousCostDetails,
              storageClass,
              LAYERS_KEYS.cost
            ) || 0;
            const oldVersionsValue = getCostDetailsValue(
              item.previousCostDetails,
              storageClass,
              LAYERS_KEYS.oldVersionCost
            ) || 0;
            const current = costTickFormatter(currentValue);
            const oldVersion = costTickFormatter(oldVersionsValue);
            const total = costTickFormatter(currentValue + oldVersionsValue);
            return `${total} (current: ${current}; old versions: ${oldVersion})`;
          }
          return currentValue;
        }
      }
    }
  };

  const previousOldVersionsDataset = {
    accumulative: {
      value: (item) => getCostDetailsSumm(
        item.previousCostDetails,
        storageClass,
        LAYERS_KEYS.accumulativeOldVersionCost
      ),
      options: {
        borderWidth: 2,
        backgroundColor: 'transparent',
        dashed: true,
        showTooltip: false,
        isPrevious: true
      }
    },
    fact: {
      value: (item) => getCostDetailsSumm(
        item.previousCostDetails,
        storageClass,
        LAYERS_KEYS.oldVersionCost
      ),
      options: {
        borderWidth: 1,
        subTitle: 'cost',
        stack: 'previous',
        backgroundColor: 'transparent',
        showTooltip: false,
        isPrevious: true
      }
    }
  };

  return [
    currentDataset,
    currentOldVersionsDataset,
    previousDataset,
    previousOldVersionsDataset
  ];
}

export function getDetailsDatasetsByStorageClassAndMetrics (storageClass, metrics) {
  if (!storageClass) {
    const filters = metrics === StorageMetrics.volume
      ? StorageFilters.usage
      : StorageFilters.value;
    return [
      {
        sample: filters.dataSample
      },
      {
        sample: filters.previousDataSample,
        isPrevious: true
      }
    ];
  }
  const metricsName = metrics === StorageMetrics.volume ? 'avgSize' : 'cost';
  const oldVersionMetricsName = metrics === StorageMetrics.volume
    ? 'oldVersionAvgSize'
    : 'oldVersionCost';
  const formatter = metrics === StorageMetrics.volume ? numberFormatter : costTickFormatter;
  const currentDataSample = `costDetails.tiers.${storageClass}.${metricsName}`;
  const currentOldVersionDataSample = `costDetails.tiers.${storageClass}.${oldVersionMetricsName}`;
  const previousDataSample = `previousCostDetails.tiers.${storageClass}.${metricsName}`;
  const showDetailsDataLabel = (value, datasetValues, options) => {
    const {
      getPxSize = (() => 0)
    } = options || {};
    const detailsDatasets = datasetValues
      .filter((value) => value.dataset.details &&
        (value.value > 0 && getPxSize(value.value) > 5.0)
      );
    return detailsDatasets.length > 1;
  };
  return [
    {
      sample: currentDataSample,
      details: true,
      stack: 'current',
      flagColor: undefined,
      tooltipValue (value, item) {
        if (item) {
          const currentValue = getCostDetailsValue(
            item.costDetails,
            storageClass,
            metricsName
          ) || 0;
          const oldVersionsValue = getCostDetailsValue(
            item.costDetails,
            storageClass,
            oldVersionMetricsName
          ) || 0;
          const current = formatter(currentValue);
          const oldVersion = formatter(oldVersionsValue);
          const total = formatter(currentValue + oldVersionsValue);
          return `${total} (current: ${current}; old versions: ${oldVersion})`;
        }
        return value;
      },
      showDataLabel: showDetailsDataLabel
    },
    {
      sample: currentOldVersionDataSample,
      details: true,
      stack: 'current',
      backgroundColor: 'transparent',
      flagColor: undefined,
      showTooltip: false,
      showDataLabel: false
    },
    {
      sample: [currentDataSample, currentOldVersionDataSample],
      main: true,
      stack: 'current-total',
      hidden: true,
      showDataLabel: true
    },
    {
      sample: previousDataSample,
      isPrevious: true,
      stack: 'previous'
    }
  ];
}

export function getItemDetailsByMetrics (dataItem, metrics) {
  if (!dataItem || !dataItem.costDetails || !dataItem.costDetails.tiers) {
    return undefined;
  }
  const layers = Object
    .keys(dataItem.costDetails.tiers)
    .filter((tier) => !/^total$/i.test(tier));
  const metricsName = metrics === StorageMetrics.volume ? 'avgSize' : 'cost';
  const oldVersionMetricsName = metrics === StorageMetrics.volume
    ? 'oldVersionAvgSize'
    : 'oldVersionCost';
  const formatter = metrics === StorageMetrics.volume ? numberFormatter : costTickFormatter;
  const getTierInfo = (tier) => {
    const currentValue = getCostDetailsValue(
      dataItem.costDetails,
      tier,
      metricsName
    ) || 0;
    const oldVersionsValue = getCostDetailsValue(
      dataItem.costDetails,
      tier,
      oldVersionMetricsName
    ) || 0;
    const current = formatter(currentValue);
    const oldVersion = formatter(oldVersionsValue);
    const total = formatter(currentValue + oldVersionsValue);
    return {
      tier,
      current,
      oldVersion,
      total
    };
  };
  const infos = layers.map(getTierInfo);
  const layoutInfo = (info) =>
    // eslint-disable-next-line max-len
    `${getStorageClassName(info.tier)}: ${info.total} (current: ${info.current}, old version: ${info.oldVersion})`;
  return `\n${infos.map(layoutInfo).join('\n')}`;
}
