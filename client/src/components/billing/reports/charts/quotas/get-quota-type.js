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

import QuotaTypes from '../../../quotas/utilities/quota-types';

export function getQuotaType (request) {
  if (!request || !request.filters) {
    return {
      type: QuotaTypes.overall
    };
  }
  const {
    group,
    user
  } = request.filters;
  if (group && group.length === 1) {
    return {
      type: QuotaTypes.billingCenter,
      subject: group[0]
    };
  }
  if (user && user.length === 1) {
    return {
      type: QuotaTypes.user,
      subject: user[0]
    };
  }
  if ((user && user.length > 1) || (group && group.length > 1)) {
    return {type: undefined};
  }
  return {
    type: QuotaTypes.overall
  };
}
