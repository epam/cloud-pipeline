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

import RemotePost from '../basic/RemotePost';
import moment from 'moment';
import BillingCenters from './billing-centers';
import User from '../user/User';
import GetDataWithPrevious from './get-data-with-previous';
import costMapper from './cost-mapper';

class GetBillingData extends RemotePost {
  constructor (filter) {
    super();
    this.filters = filter;
    const {dateFilter, dateMapper} = filter;
    this.dateMapper = dateMapper || (o => o);
    this.dateFilter = dateFilter || (() => true);
    this.url = '/billing/charts';
  }

  static FILTER_BY = {
    storages: 'STORAGE',
    objectStorages: 'OBJECT_STORAGE',
    fileStorages: 'FILE_STORAGE',
    compute: 'COMPUTE',
    cpu: 'CPU',
    gpu: 'GPU'
  };

  async fetch () {
    const body = {
      from: this.filters && this.filters.start ? this.filters.start.toISOString() : undefined,
      to: this.filters && this.filters.end ? this.filters.end.toISOString() : undefined,
      interval: this.filters && this.filters.tick ? this.filters.tick : undefined,
      filters: {}
    };
    if (this.filters && this.filters.group) {
      const billingCentersRequest = new BillingCenters();
      await billingCentersRequest.fetch();
      const [billingCenter] = billingCentersRequest.loaded && billingCentersRequest.value
        ? billingCentersRequest.value.filter(bc => bc.id === +this.filters.group)
        : [null];
      if (billingCenter) {
        body.filters.billing_center = [billingCenter.name];
      }
    }
    if (this.filters && this.filters.user) {
      const userRequest = new User(this.filters.user);
      await userRequest.fetchIfNeededOrWait();
      if (userRequest.loaded && userRequest.value) {
        body.filters.owner = [userRequest.value.userName];
      }
    }
    if (this.filters.filterBy) {
      if ([
        GetBillingData.FILTER_BY.storages,
        GetBillingData.FILTER_BY.fileStorages,
        GetBillingData.FILTER_BY.objectStorages
      ].includes(this.filters.filterBy)) {
        body.filters.resource_type = ['STORAGE'];
        if (this.filters.filterBy === GetBillingData.FILTER_BY.fileStorages) {
          body.filters.storage_type = ['FILE_STORAGE'];
        } else if (this.filters.filterBy === GetBillingData.FILTER_BY.objectStorages) {
          body.filters.storage_type = ['OBJECT_STORAGE'];
        }
      } else if ([
        GetBillingData.FILTER_BY.compute,
        GetBillingData.FILTER_BY.cpu,
        GetBillingData.FILTER_BY.gpu
      ].includes(this.filters.filterBy)) {
        body.filters.resource_type = ['COMPUTE'];
        if (this.filters.filterBy === GetBillingData.FILTER_BY.cpu) {
          body.filters.compute_type = ['CPU'];
        } else if (this.filters.filterBy === GetBillingData.FILTER_BY.gpu) {
          body.filters.compute_type = ['GPU'];
        }
      }
    }
    await super.send(body);
  }

  postprocess (value) {
    const payload = super.postprocess(value);

    const res = {
      quota: null,
      previousQuota: null,
      values: []
    };
    (payload || []).forEach((item) => {
      const initialDate = moment(item.periodStart);
      if (this.dateFilter(initialDate)) {
        const momentDate = this.dateMapper(initialDate);
        res.values.push({
          date: momentDate.format('DD MMM YYYY'),
          prevDate: initialDate.format('DD MMM YYYY'),
          value: isNaN(item.accumulatedCost) ? undefined : costMapper(item.accumulatedCost),
          momentDate
        });
      }
    });

    return res;
  }
}

class GetBillingDataWithPreviousRange extends GetDataWithPrevious {
  constructor (filter) {
    super(GetBillingData, filter);
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
      ? previousValues.map((o) => ({...o, previous: o.value}))
      : [];
    result.forEach((o) => delete o.value);
    for (let i = 0; i < (currentValues || []).length; i++) {
      const item = currentValues[i];
      const {date} = item;
      const [prev] = result.filter((r) => r.date === date);
      if (prev) {
        prev.value = item.value;
      } else {
        result.push(item);
      }
    }
    const sorter = (a, b) => a.momentDate - b.momentDate;
    result.sort(sorter);
    result.forEach((r) => delete r.momentDate);
    return {...rest, values: result};
  }
}

export default GetBillingDataWithPreviousRange;
