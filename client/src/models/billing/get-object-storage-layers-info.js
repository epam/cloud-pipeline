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

import BaseBillingRequest from './base-billing-request';

class GetObjectStorageLayersInfo extends BaseBillingRequest {
  /**
   * @param {BaseBillingRequestOptions} options
   */
  constructor (options) {
    super({
      ...options,
      loadDetails: true,
      loadCostDetails: true,
      pagination: undefined
    });
    this.grouping = 'STORAGE_TYPE';
  }

  prepareBody () {
    super.prepareBody();
    this.body.filters.storage_type = ['OBJECT_STORAGE'];
  }

  postprocess (value) {
    const payload = super.postprocess(value) || [];
    return payload.slice(0, 1);
  }
}

export default GetObjectStorageLayersInfo;
