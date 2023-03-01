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

import {costTickFormatter} from '../../utilities';
import {LAYERS_KEYS} from '../../../../../models/billing';

export function getSummaryDatasetsByStorageClass (storageClass) {
  const total = /^total$/i.test(storageClass);
  const getCostDetailsValue = (costDetails, key) => {
    if (
      !costDetails ||
      !costDetails.tiers ||
      !costDetails.tiers[storageClass]
    ) {
      return undefined;
    }
    return costDetails.tiers[storageClass][key];
  };
  const getCostDetailsSumm = (costDetails, ...keys) => {
    const values = keys.map((key) => getCostDetailsValue(costDetails, key));
    if (values.some((value) => value !== undefined && !Number.isNaN(Number(value)))) {
      return values.reduce((summ, value) => (summ + (value || 0)), 0);
    }
    return undefined;
  };
  const currentDataset = {
    accumulative: {
      value: (item) => getCostDetailsSumm(
        item.costDetails,
        LAYERS_KEYS.accumulativeCost,
        LAYERS_KEYS.accumulativeOldVersionCost
      ),
      options: {
        borderWidth: 3,
        tooltipValue: (currentValue, item) => {
          if (item) {
            const currentValue = getCostDetailsValue(
              item.costDetails,
              LAYERS_KEYS.accumulativeCost
            ) || 0;
            const oldVersionsValue = getCostDetailsValue(
              item.costDetails,
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
        'cost'
      ),
      options: {
        subTitle: 'cost',
        stack: 'current',
        tooltipValue: (currentValue, item) => {
          if (item) {
            const currentValue = getCostDetailsValue(
              item.costDetails,
              LAYERS_KEYS.cost
            ) || 0;
            const oldVersionsValue = getCostDetailsValue(
              item.costDetails,
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
      value: (item) => total ? item.previous : getCostDetailsSumm(
        item.previousCostDetails,
        LAYERS_KEYS.accumulativeCost,
        LAYERS_KEYS.accumulativeOldVersionCost
      ),
      options: {
        isPrevious: true
      }
    },
    fact: {
      value: (item) => total ? item.previousCost : getCostDetailsSumm(
        item.previousCostDetails,
        LAYERS_KEYS.cost,
        LAYERS_KEYS.oldVersionCost
      ),
      options: {
        isPrevious: true,
        subTitle: 'cost',
        stack: 'previous'
      }
    }
  };
  return [
    currentDataset,
    currentOldVersionsDataset,
    previousDataset
  ];
}
