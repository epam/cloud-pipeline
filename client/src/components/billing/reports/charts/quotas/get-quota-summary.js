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

import {getAffectiveQuotaPeriods} from './get-affective-quota-period';
import {getQuotaType} from './get-quota-type';
import {__TOTAL__} from '../../../quotas/quota-provider';
import {discounts} from '../../discounts';

export function getQuotaSummary (options = {}) {
  const {
    request,
    data,
    discount,
    quotas,
    quotaGroup
  } = options;
  const periods = getAffectiveQuotaPeriods(request);
  const {type: quotaType, subject: aSubject} = getQuotaType(request);
  if (!quotaType) {
    return {};
  }
  const subject = aSubject || __TOTAL__;
  const quotaSummary = quotas.getSubjectQuotasByTypeAndGroup(quotaType, quotaGroup)[subject];
  const usage = Object.values(discounts.applyDiscountsToObjectProperties(data, discount) || {})
    .filter(item => item.value && !Number.isNaN(Number(item.value)))
    .map(item => Number(item.value))
    .reduce((total, current) => total + current, 0);
  const affectedQuotas = Object
    .entries(quotaSummary || {})
    .map(([period, quota]) => ({period, quota}))
    .filter(({period}) => periods.includes(period))
    .reduce((result, current) => ({...result, [current.period]: current.quota}), {});
  const lowestQuota = Math.min(Infinity, ...Object.values(affectedQuotas));
  return {
    usage,
    quota: Number.isFinite(lowestQuota) ? lowestQuota : undefined,
    quotaSummary: affectedQuotas,
    periods,
    exceeds: usage && lowestQuota && usage >= lowestQuota
  };
}

export function getQuotaSummaries (requestsWithDiscounts = []) {
  return requestsWithDiscounts
    .map(getQuotaSummary);
}

export function getQuotaSummariesExceeded (requestsWithDiscounts = []) {
  return requestsWithDiscounts.map(getQuotaSummary).some(o => o.exceeds);
}
