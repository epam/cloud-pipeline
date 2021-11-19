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

function applyDiscounts (obj, discountFn) {
  if (!obj) {
    return obj;
  }
  const keysToProcess = ['value', 'cost', 'previous', 'previousCost', 'spendings'];
  const discountPeriod = {
    value: v => v.initialDate || v.startDate,
    cost: v => v.initialDate || v.startDate,
    spendings: v => v.initialDate || v.startDate,
    previous: v => v.previousInitialDate,
    previousCost: v => v.previousInitialDate
  };
  const result = {...obj};
  for (let i = 0; i < keysToProcess.length; i++) {
    const key = keysToProcess[i];
    if (result.hasOwnProperty(key) && !isNotSet(result[key]) && discountFn) {
      result[key] = discountFn(
        result[key],
        discountPeriod[key] ? discountPeriod[key](obj) : undefined
      );
    }
  }
  return result;
}

function applySummaryDiscounts (request, discountFn) {
  if (!request || !request.loaded) {
    return undefined;
  }
  const {quota, previousQuota, values: initialValues} = request.value || {};
  const values = initialValues.map((value) => applyDiscounts(value, discountFn));
  return {
    quota,
    previousQuota,
    values
  };
}

function simpleDiscount (percent) {
  return (value) => +value * percent / 100.0;
}

function isNotSet (a) {
  return a === undefined || a === null;
}

function safelySumm (a, b) {
  if (isNotSet(a) && isNotSet(b)) {
    return undefined;
  }
  const aa = isNotSet(a) ? 0 : +a;
  const bb = isNotSet(b) ? 0 : +b;
  return aa + bb;
}

function applyGroupedDataDiscounts (groupedData, discountFn) {
  if (!groupedData) {
    return groupedData;
  }
  const processSumm = (objA, join, isGroupingInfoProcessing = false) => {
    const result = {...(objA || {})};
    const summKeys = Object.keys(join || {});
    for (let sk = 0; sk < summKeys.length; sk++) {
      const summKey = summKeys[sk];
      const current = result[summKey];
      const summ = join[summKey];
      if (
        typeof current !== 'object' && !isNaN(current) &&
        typeof summ !== 'object' && !isNaN(summ)
      ) {
        result[summKey] = safelySumm(current, summ);
      }
    }
    if (!isGroupingInfoProcessing) {
      result.groupingInfo = processSumm(result.groupingInfo, (join || {}).groupingInfo, true);
    }
    return result;
  };
  if (Array.isArray(groupedData)) {
    const joins = [];
    for (let i = 0; i < groupedData.length; i++) {
      const data = groupedData[i];
      let discount = discountFn;
      if (discountFn && Array.isArray(discountFn)) {
        discount = discountFn.length > i ? discountFn[i] : undefined;
      }
      const appliedData = applyGroupedDataDiscounts(data, discount);
      if (appliedData) {
        joins.push(appliedData);
      }
    }
    let result;
    for (let j = 0; j < joins.length; j++) {
      const join = joins[j];
      if (!result) {
        result = join;
      } else {
        const joinKeys = Object.keys(join);
        for (let jk = 0; jk < joinKeys.length; jk++) {
          const joinKey = joinKeys[jk];
          if (!result.hasOwnProperty(joinKey)) {
            result[joinKey] = join[joinKey];
          } else {
            result[joinKey] = processSumm(result[joinKey], join[joinKey]);
          }
        }
      }
    }
    return result;
  }
  return Object.keys(groupedData)
    .map(key => ({
      key,
      data: applyDiscounts(groupedData[key], discountFn)
    }))
    .reduce((r, c) => ({...r, [c.key]: c.data}), {});
}

function joinSummaryDiscounts (summaries, discounts) {
  let result;
  const add = (target, add, onlyAccumulative = true) => {
    if (!isNotSet(target.value)) {
      target.value = safelySumm(target.value, add.value);
    }
    if (!isNotSet(target.cost) && !onlyAccumulative) {
      target.cost = safelySumm(target.cost, add.cost);
    }
    if (!isNotSet(target.previous)) {
      target.previous = safelySumm(target.previous, add.previous);
    }
    if (!isNotSet(target.previousCost) && !onlyAccumulative) {
      target.previousCost = safelySumm(target.previousCost, add.previousCost);
    }
  };
  for (let i = 0; i < (summaries || []).length; i++) {
    const summary = summaries[i];
    if (summary) {
      let discount;
      if (discounts && Array.isArray(discounts) && discounts.length > i) {
        discount = discounts[i];
      } else if (discounts && typeof discounts === 'function') {
        discount = discounts;
      }
      if (!result) {
        result = applySummaryDiscounts(summary, discount);
      } else {
        const subResult = applySummaryDiscounts(summary, discount);
        if (subResult) {
          const sorter = (a, b) => a.dateValue - b.dateValue;
          const {values = []} = subResult;
          const {values: current = []} = result;
          values.sort(sorter);
          // join values with date before first date of current
          const [first] = current;
          const last = current.slice().pop();
          if (first) {
            const before = values.filter(v => v.dateValue < first.dateValue);
            current.push(...before);
            current.sort(sorter);
          }
          // update current values after last value
          const lastValue = values.slice().pop();
          if (lastValue) {
            const afterLast = current.filter(c => c.dateValue > lastValue.dateValue);
            if (afterLast.length > 0) {
              for (let j = 0; j < afterLast.length; j++) {
                const value = afterLast[j];
                add(value, lastValue);
              }
            }
          }
          // join values with date after last date of current array
          if (last) {
            const after = values.filter(value => value.dateValue > last.dateValue);
            for (let j = 0; j < after.length; j++) {
              const value = after[j];
              add(value, last);
              current.push(value);
            }
          }
          // join overlapping values
          const filterStart = value => first ? value.dateValue >= first.dateValue : true;
          const filterEnd = value => last ? value.dateValue <= last.dateValue : true;
          const overlappingValues = values.filter(v => filterStart(v) && filterEnd(v));
          for (let j = 0; j < (overlappingValues || []).length; j++) {
            const value = overlappingValues[j];
            const [existing] = current.filter(e => e.date === value.date);
            if (!existing) {
              // This is a gap.
              // We need to find last value and summ only accumulative values
              // (e.g. 'value', 'previous')
              const lastGap = current.filter(c => c.dateValue < value.dateValue).slice().pop();
              if (lastGap) {
                add(value, lastGap);
              }
              current.push(value);
            } else {
              add(existing, value, false);
            }
          }
        }
      }
    }
  }
  return result;
}

export {
  applySummaryDiscounts,
  applyGroupedDataDiscounts,
  joinSummaryDiscounts,
  simpleDiscount
};
