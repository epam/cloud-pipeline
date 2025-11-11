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

function getUserSearchColumnsOrder (preferences, authenticatedUserInfo) {
  if (!preferences || !preferences.loaded) {
    return [];
  }
  const order = preferences.searchColumnsOrder;
  if (!order) {
    return [];
  }
  if (isObservableArray(order) || Array.isArray(order)) {
    return order;
  }
  if (!authenticatedUserInfo || !authenticatedUserInfo.loaded) {
    return [];
  }
  const {
    roles = [],
    groups: adGroups = []
  } = authenticatedUserInfo.value || {};
  const groups = [...new Set([
    ...roles.map((role) => role.name),
    ...adGroups
  ])];
  const {
    default: all = [],
    '*': fallback = all
  } = order;
  for (let i = 0; i < groups.length; i++) {
    const group = groups[i];
    if (Object.prototype.hasOwnProperty.call(order, group)) {
      return order[group];
    }
  }
  return fallback;
}

export default getUserSearchColumnsOrder;
