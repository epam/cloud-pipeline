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

import {isObservableArray} from 'mobx';

export function getFilterByPayload (filterBy) {
  const {
    resourceType,
    storageType,
    computeType
  } = filterBy || {};
  const payload = {};
  const asArray = (value) => Array.isArray(value) || isObservableArray(value) ? value : [value];
  if (resourceType) {
    payload.resource_type = asArray(resourceType);
  }
  if (storageType) {
    payload.storage_type = asArray(storageType);
  }
  if (computeType) {
    payload.compute_type = asArray(computeType);
  }
  return payload;
}

export default function extendFiltersWithFilterBy (filters, filterBy) {
  return {
    ...(filters || {}),
    ...getFilterByPayload(filterBy)
  };
}
