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

import moment from 'moment-timezone';
import {Period} from '../../../special/periods';
import periods, {
  getQuotaPeriodForReportPeriod,
  periodNamesAdjective
} from '../../quotas/utilities/quota-periods';

function getVisibleDataRange (values) {
  const filtered = values
    .map(item => ([item.value, item.previous]))
    .reduce((r, c) => ([...r, ...c]), [])
    .filter(element => !Number.isNaN(Number(element)))
    .map(element => Number(element));
  if (filtered.length === 0) {
    return {
      min: 0,
      max: Infinity
    };
  }
  const min = 0;
  const max = Math.max(...filtered);
  const offsetRatioPercent = 25;// 25%
  const range = max - min;
  const offset = range * offsetRatioPercent / 100.0;
  return {
    min: 0,
    max: max + offset
  };
}

const DISPLAY_QUOTAS_ONLY_FOR_CURRENT_PERIOD = true;

function getQuotaRanges (filters = {}) {
  const {start, end} = filters;
  const onlyCurrent = DISPLAY_QUOTAS_ONLY_FOR_CURRENT_PERIOD || filters.name === Period.custom;
  if (onlyCurrent) {
    return [
      filters.name === Period.month && {
        type: 'month',
        dateValue: moment().startOf('month'),
        dateEndValue: end || moment().endOf('year')
      },
      {
        type: 'quarter',
        dateValue: moment().startOf('quarter'),
        dateEndValue: end || moment().endOf('year')
      },
      {
        type: 'year',
        dateValue: moment().startOf('year'),
        dateEndValue: end || moment().endOf('year')
      }
    ].filter(Boolean);
  }
  if (start && end) {
    const getRange = (period) => {
      const result = [];
      let d = moment(start).startOf(period);
      while (d < end) {
        result.push({
          type: period,
          dateValue: d,
          dateEndValue: moment(d).endOf(period)
        });
        d = moment(d).add(1, period).startOf(period);
      }
      return result;
    };
    const months = getRange('month');
    const quarters = getRange('quarter');
    const years = getRange('year');
    return [...months, ...quarters, ...years];
  }
  return [];
}

function getRangedQuotaDatasets (quotas = [], data = [], filters = {}) {
  const getQuotaPeriod = (unit) => {
    switch (unit) {
      case 'quarter': return periods.quarter;
      case 'year': return periods.year;
      case 'month':
      default:
        return periods.month;
    }
  };
  const getQuota = (unit) => quotas.find(o => o.period === getQuotaPeriod(unit))?.quota;
  const quotaItems = getQuotaRanges(filters)
    .map(range => ({range, quota: getQuota(range.type)}))
    .filter(item => item.quota !== undefined)
    .map(item => {
      const {range, quota} = item;
      const last = data
        .filter(item => item.dateValue < range.dateValue)
        .filter(item => item.value !== undefined)
        .pop();
      return {
        ...range,
        quota: (last ? last.value : 0) + quota
      };
    });
  const types = [...new Set(quotaItems.map(qItem => qItem.type))];
  return types.map(type => {
    const items = quotaItems.filter(qItem => qItem.type === type);
    const dataset = data.map((dataItem, index) => {
      const quotaItem = items.find(quotaItem =>
        quotaItem.dateValue <= dataItem.dateValue &&
        quotaItem.dateEndValue > dataItem.dateValue
      );
      if (quotaItem) {
        return {y: quotaItem.quota, x: index, quota: getQuota(type)};
      }
      return undefined;
    });
    return ({
      quota: getQuota(type),
      period: getQuotaPeriod(type),
      dataset
    });
  });
}

export default function getQuotaDatasets (
  compute,
  storages,
  quotas,
  data = [],
  maximum = undefined
) {
  let accessor = () => quotas?.overallGlobal || {};
  if (compute && !storages) {
    accessor = () => quotas?.overallComputeInstances || {};
  } else if (!compute && storages) {
    accessor = () => quotas?.overallStorages || {};
  }
  const quotaInfo = quotas
    ? accessor()
    : {};
  const dataRequest = (compute || storages);
  if (!dataRequest || !dataRequest.filters) {
    return [];
  }
  let affectivePeriods;
  switch (dataRequest.filters.name) {
    case Period.month:
      affectivePeriods = [Period.month, Period.quarter, Period.year];
      break;
    case Period.quarter:
      affectivePeriods = [Period.quarter, Period.year];
      break;
    case Period.year:
      affectivePeriods = [Period.year];
      break;
    default:
      affectivePeriods = [Period.quarter, Period.year];
      break;
  }
  let maximumValue = maximum;
  if (maximumValue === undefined) {
    const {
      max = Infinity
    } = getVisibleDataRange(data);
    maximumValue = max;
  }
  const quotaValues = affectivePeriods
    .map(getQuotaPeriodForReportPeriod)
    .map(quotaPeriod => ({period: quotaPeriod, quota: quotaInfo[quotaPeriod]}))
    .filter(info => info.quota !== undefined && !Number.isNaN(Number(info.quota)))
    .filter(info => info.quota <= maximumValue); // filter quotas that not in displayed range
  return getRangedQuotaDatasets(quotaValues, data, dataRequest.filters)
    .map(rangedDataset => ({
      data: rangedDataset.dataset,
      quota: rangedDataset.quota,
      title: `${periodNamesAdjective[rangedDataset.period]} quota`
    }));
}
