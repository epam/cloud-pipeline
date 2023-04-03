/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

class GetInstanceCostsDetailsInfo extends BaseBillingRequest {
  /**
   * @param {BaseBillingRequestOptions} options
   */
  constructor (options = {}) {
    const {
      filters = {},
      ...restOptions
    } = options;
    const {
      filterBy = {},
      ...restFilters
    } = filters;
    super({
      ...restOptions,
      filters: {
        ...restFilters,
        filterBy: {
          ...filterBy,
          resourceType: 'COMPUTE'
        }
      },
      loadDetails: true,
      pagination: undefined
    });
    this.grouping = 'RUN_COMPUTE_TYPE';
  }

  postprocess (value) {
    const payload = super.postprocess(value) || [];
    const copy = (a, b) => {
      if (a === undefined && b === undefined) {
        return undefined;
      }
      if (a === undefined) {
        return b;
      }
      if (b === undefined) {
        return a;
      }
      return Number(a) + Number(b);
    };
    return payload
      .map((o) => o.costDetails || {})
      .reduce((result, current) => ({
        computeCost: copy(result.computeCost, current.computeCost),
        diskCost: copy(result.diskCost, current.diskCost),
        accumulatedComputeCost: copy(result.accumulatedComputeCost, current.accumulatedComputeCost),
        accumulatedDiskCost: copy(result.accumulatedDiskCost, current.accumulatedDiskCost)
      }), {
        computeCost: undefined,
        diskCost: undefined,
        accumulatedComputeCost: undefined,
        accumulatedDiskCost: undefined
      });
  }
}

export default GetInstanceCostsDetailsInfo;
