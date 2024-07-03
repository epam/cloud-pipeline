/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import moment from 'moment';
import BaseBillingRequest from './base-billing-request';
import GetDataWithPrevious from './get-data-with-previous';
import {costMapper} from './utils';
import extendFiltersWithFilterBy from './filter-by-payload';

class GetBillingData extends BaseBillingRequest {
  /**
   * @param {BaseBillingRequestOptions} options
   */
  constructor (options = {}) {
    super(options);
    const {
      filters = {},
      loadCostDetails
    } = options;
    const {dateFilter, dateMapper} = filters;
    this.dateMapper = dateMapper || (o => o);
    this.dateFilter = dateFilter || (() => true);
    this.loadCostDetails = loadCostDetails;
  }

  static FILTER_BY = {
    storages: 'STORAGE',
    objectStorages: 'OBJECT_STORAGE',
    fileStorages: 'FILE_STORAGE',
    compute: 'COMPUTE',
    cpu: 'CPU',
    gpu: 'GPU'
  };

  prepareBody () {
    super.prepareBody();
    if (this.filters && this.filters.tick) {
      this.body.interval = this.filters.tick;
    }
    this.body.filters = extendFiltersWithFilterBy(
      this.body.filters,
      this.filters ? this.filters.filterBy : {}
    );
    if (this.loadCostDetails) {
      this.body.loadCostDetails = true;
    }
  }

  postprocess (value) {
    const payload = super.postprocess(value);

    const res = {
      quota: null,
      previousQuota: null,
      values: []
    };
    (payload || []).forEach((item) => {
      const initialDate = moment.utc(item.periodStart, 'YYYY-MM-DD HH:mm:ss.SSS');
      if (this.dateFilter(initialDate)) {
        const momentDate = this.dateMapper(initialDate);
        res.values.push({
          costDetails: item.costDetails,
          date: moment(momentDate).format('DD MMM YYYY'),
          value: isNaN(item.accumulatedCost) ? undefined : costMapper(item.accumulatedCost),
          cost: isNaN(item.cost) ? undefined : costMapper(item.cost),
          dateValue: momentDate,
          initialDate
        });
      }
    });

    return res;
  }
}

class GetBillingDataWithPreviousRange extends GetDataWithPrevious {
  /**
   * @param {BaseBillingRequestOptions} options
   */
  constructor (options) {
    super(GetBillingData, options);
  }

  static FILTER_BY = GetBillingData.FILTER_BY;

  send () {
    return this.fetch();
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    if (!previous) {
      return current;
    }
    const {values: previousValues = []} = previous;
    const {values: currentValues = [], ...rest} = current || {};
    const result = previousValues.length > 0
      ? previousValues.map((o) => (
        {
          ...o,
          previous: o.value,
          previousCost: o.cost,
          previousInitialDate: o.initialDate,
          previousCostDetails: o.costDetails
        }))
      : [];
    result.forEach((o) => {
      delete o.value;
      delete o.cost;
      delete o.costDetails;
    });
    for (let i = 0; i < (currentValues || []).length; i++) {
      const item = currentValues[i];
      const {date} = item;
      const [prev] = result.filter((r) => r.date === date);
      if (prev) {
        prev.value = item.value;
        prev.cost = item.cost;
        prev.costDetails = item.costDetails;
      } else {
        result.push(item);
      }
    }
    const sorter = (a, b) => a.dateValue - b.dateValue;
    result.sort(sorter);
    return {...rest, values: result};
  }
}

export default GetBillingDataWithPreviousRange;
