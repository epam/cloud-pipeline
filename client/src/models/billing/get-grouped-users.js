/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import BaseBillingRequest from './base-billing-request';
import {costMapper, minutesToHours, bytesToGbs} from './utils';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';
import moment from 'moment-timezone';

export class GetGroupedUsers extends BaseBillingRequest {
  constructor (filters, pagination = null) {
    const {resourceType, fetchLastDay, ...rest} = filters || {};
    super(rest, true, pagination);
    this.resourceType = resourceType;
    this.fetchLastDay = fetchLastDay;
    this.grouping = 'USER';
  }

  async prepareBody () {
    await super.prepareBody();
    if (this.fetchLastDay && this.filters && this.filters.endStrict) {
      this.body.from = moment(this.filters.endStrict).startOf('d');
      this.body.to = moment(this.filters.endStrict).endOf('d');
    }
    if (this.resourceType) {
      this.body.filters.resource_type = [this.resourceType];
    }
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareUserData(payload);
  }

  prepareUserData (raw) {
    const res = {};
    (raw && raw.length ? raw : []).forEach(i => {
      if (this.filters && this.filters.group) {
        const name = i.groupingInfo[this.grouping];
        if (name && name !== 'unknown') {
          res[name] = {
            ...i,
            name,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost),
            runsDuration: minutesToHours(i.groupingInfo.usage_runs),
            storageUsage: bytesToGbs(i.groupingInfo.usage_storages),
            runsCount: i.groupingInfo.runs,
            spendings: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      } else {
        const name = i.groupingInfo[this.grouping];
        if (name && name !== 'unknown') {
          res[name] = {
            ...i,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      }
    });
    return res;
  }
}

export class GetGroupedUsersWithPrevious extends GetDataWithPrevious {
  constructor (filters, pagination = null) {
    const {
      end,
      endStrict,
      previousEnd,
      previousEndStrict,
      ...rest
    } = filters;
    const formattedFilters = {
      end: endStrict || end,
      previousEnd: previousEndStrict || previousEnd,
      ...rest
    };
    super(
      GetGroupedUsers,
      formattedFilters,
      pagination
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
