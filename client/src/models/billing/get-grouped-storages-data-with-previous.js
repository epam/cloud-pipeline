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

import GetDataWithPrevious from './get-data-with-previous';

class GetGroupedStoragesDataWithPrevious extends GetDataWithPrevious {
  /**
   * @param Model
   * @param {BaseBillingRequestOptions} options
   */
  constructor (
    Model,
    options
  ) {
    super(Model, options, (currentPeriodData) => {
      const storageIds = Object.entries(currentPeriodData || {})
        .map(([key, storage]) => {
          if (storage && storage.groupingInfo && storage.groupingInfo.id) {
            return storage.groupingInfo.id;
          }
          return key;
        });
      return {storageIds};
    });
  }
}

export default GetGroupedStoragesDataWithPrevious;
