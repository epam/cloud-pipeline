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

function applyDiscountsForObject (
  obj,
  discountFn,
  keysToProcess,
  discountPeriodsConfiguration
) {
  if (!obj) {
    return obj;
  }
  const result = {...obj};
  for (let i = 0; i < keysToProcess.length; i++) {
    const key = keysToProcess[i];
    if (result.hasOwnProperty(key) && !isNotSet(result[key]) && discountFn) {
      result[key] = discountFn(
        result[key],
        discountPeriodsConfiguration(key, obj)
      );
    }
  }
  return result;
}

function applyDiscountsForTierCostDetails (tier, discountFn, periodFn) {
  if (!tier) {
    return tier;
  }
  const keysToProcess = [
    'cost',
    'oldVersionCost',
    'accumulativeCost',
    'accumulativeOldVersionCost'
  ];
  function discountPeriodConfiguration (key, item) {
    return periodFn(item);
  }
  return applyDiscountsForObject(
    tier,
    discountFn,
    keysToProcess,
    discountPeriodConfiguration
  );
}

function applyDiscountsForCostDetails (costDetails, discountFn, periodFn) {
  if (!costDetails) {
    return costDetails;
  }
  const {
    tiers = {},
    ...rest
  } = costDetails;
  const processedTiers = {};
  Object.entries(tiers).forEach(([tierKey, tier]) => {
    processedTiers[tierKey] = applyDiscountsForTierCostDetails(tier, discountFn, periodFn);
  });
  const result = {
    ...rest,
    tiers: processedTiers
  };
  return applyDiscountsForObject(
    result,
    discountFn,
    [
      'computeCost',
      'accumulatedComputeCost',
      'diskCost',
      'accumulatedDiskCost'
    ],
    periodFn
  );
}

function applyDiscounts (obj, discountFn) {
  if (!obj) {
    return obj;
  }
  const keysToProcess = ['value', 'cost', 'previous', 'previousCost', 'spendings'];
  function discountPeriodConfiguration (key, item) {
    switch (key) {
      case 'value':
      case 'cost':
      case 'spendings':
        return item.initialDate || item.startDate;
      case 'previous':
      case 'previousCost':
        return item.previousInitialDate;
      default:
        return undefined;
    }
  }
  const result = applyDiscountsForObject(
    obj,
    discountFn,
    keysToProcess,
    discountPeriodConfiguration
  );
  result.costDetails = applyDiscountsForCostDetails(
    result.costDetails,
    discountFn,
    (item) => item.initialDate || item.startDate
  );
  result.previousCostDetails = applyDiscountsForCostDetails(
    result.previousCostDetails,
    discountFn,
    (item) => item.previousInitialDate
  );
  return result;
}

function applySummaryDiscounts (request, discountFn) {
  if (!request || !request.loaded) {
    return undefined;
  }
  const {quota, previousQuota, values: initialValues} = request.value || {};
  const values = (initialValues || []).map((value) => applyDiscounts(value, discountFn));
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
  if (Array.isArray(groupedData)) {
    return joinObjects(applyDiscountsToObjects(groupedData, discountFn));
  }
  return applyDiscountsToObjectProperties(groupedData, discountFn);
}

function applyDiscountsToObjectProperties (object, discountFn) {
  if (!object) {
    return object;
  }
  return Object.keys(object)
    .map(key => ({
      key,
      data: applyDiscounts(object[key], discountFn)
    }))
    .reduce((r, c) => ({...r, [c.key]: c.data}), {});
}

function joinObjects (joins) {
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
  let result;
  const filtered = (joins || []).filter(Boolean);
  for (let j = 0; j < filtered.length; j++) {
    const join = filtered[j];
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

function applyDiscountsToObjects (objects, discountFn) {
  if (!objects) {
    return objects;
  }
  const asArray = Array.isArray(objects)
    ? objects
    : [objects];
  const joins = [];
  for (let i = 0; i < asArray.length; i++) {
    const data = asArray[i];
    let discount = discountFn;
    if (discountFn && Array.isArray(discountFn)) {
      discount = discountFn.length > i ? discountFn[i] : undefined;
    }
    const appliedData = applyDiscountsToObjectProperties(data, discount);
    if (appliedData) {
      joins.push(appliedData);
    } else {
      joins.push(undefined);
    }
  }
  return joins;
}

function joinSummaryDiscounts (summaries, discounts) {
  let result;

  const printEntry = (entry) => {
    const {
      cost,
      value,
      previous,
      previousCost,
      date
    } = entry || {};
    return {
      date,
      cost,
      accumulative: value,
      previousCost,
      previousAccumulative: previous
    };
  };

  console.groupCollapsed('joining billing data');
  for (let i = 0; i < (summaries || []).length; i++) {
    console.groupCollapsed(`Joining #${i + 1} out of ${(summaries || []).length} billing data`);
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
          const extractDate = (o) => ({
            date: o.date,
            dateValue: o.dateValue,
            initialDate: o.initialDate,
            previousInitialDate: o.previousInitialDate
          });
          const dates = current.map(extractDate);
          for (const val of values) {
            if (!dates.some((v) => v.date === val.date)) {
              dates.push(extractDate(val));
            }
          }
          dates.sort(sorter);
          console.log(dates, {current: current.map(printEntry), values: values.map(printEntry)});
          // `dates` holds all unique dates from both arrays
          // `current` (merge target) and `values` (what is being merged) sorted in ACC order.
          // Each object contains:
          // - date: string (e.g., "22 Dec 2025")
          // - dateValue: moment (corresponding moment date object)
          values.sort(sorter);
          const allTiers = [];
          for (const entry of [...current, ...values]) {
            const tiersKeys = [
              ...Object.keys((entry.costDetails ?? {}).tiers ?? {}),
              ...Object.keys((entry.previousCostDetails ?? {}).tiers ?? {})
            ];
            for (const tier of tiersKeys) {
              if (!allTiers.includes(tier)) {
                allTiers.push(tier);
              }
            }
          }
          const makeAccumulativeArray = (field) => current
            .filter((o) => !isNotSet(o[field]))
            .map((o) => ({
              date: o.date,
              dateValue: o.dateValue,
              [field]: o[field]
            }))
            .reverse();
          const getEntryTierValue = (entry, tiersDetails, tier, field) => {
            const {[tiersDetails]: _tiersDetails = {}} = entry || {};
            const {tiers = {}} = _tiersDetails;
            const tierObj = tiers[tier] || {};
            return tierObj[field];
          };
          const setEntryTierValue = (entry, tiersDetails, tier, field, value) => {
            if (!entry) {
              return;
            }
            if (!entry[tiersDetails]) {
              entry[tiersDetails] = {};
            }
            if (!entry[tiersDetails].tiers) {
              entry[tiersDetails].tiers = {};
            }
            if (!entry[tiersDetails].tiers[tier]) {
              entry[tiersDetails].tiers[tier] = {};
            }
            entry[tiersDetails].tiers[tier][field] = value;
          };
          const makeTierAccumulativeArray = (tiersDetails, tier, field) => current
            .map((o) => ({
              date: o.date,
              dateValue: o.dateValue,
              [field]: getEntryTierValue(o, tiersDetails, tier, field)
            }))
            .filter((o) => !isNotSet(o[field]))
            .reverse();
          // `accumulativeArray` holds non-empty accumulative values ('value')
          const accumulativeArray = makeAccumulativeArray('value');
          // `previousAccumulativeArray` holds non-empty previous accumulative values ('previous')
          const previousAccumulativeArray = makeAccumulativeArray('previous');
          // eslint-disable-next-line max-len
          // `tiersAccumulative` holds map of <tier, non-empty accumulative values ('accumulativeCost')>
          const tiersAccumulative = {};
          // eslint-disable-next-line max-len
          // `tiersAccumulativeOldVersions` holds map of <tier, non-empty accumulative old versions values ('accumulativeOldVersionCost')>
          const tiersAccumulativeOldVersions = {};
          // eslint-disable-next-line max-len
          // `tiersPreviousAccumulative` holds map of <tier, non-empty previous accumulative values ('accumulativeCost')>
          const tiersPreviousAccumulative = {};
          // eslint-disable-next-line max-len
          // `tiersPreviousAccumulativeOldVersions` holds map of <tier, non-empty previous accumulative old versions values ('accumulativeOldVersionCost')>
          const tiersPreviousAccumulativeOldVersions = {};
          for (const tier of allTiers) {
            tiersAccumulative[tier] = makeTierAccumulativeArray(
              'costDetails',
              tier,
              'accumulativeCost'
            );
            tiersAccumulativeOldVersions[tier] = makeTierAccumulativeArray(
              'costDetails',
              tier,
              'accumulativeOldVersionCost'
            );
            tiersPreviousAccumulative[tier] = makeTierAccumulativeArray(
              'previousCostDetails',
              tier,
              'accumulativeCost'
            );
            tiersPreviousAccumulativeOldVersions[tier] = makeTierAccumulativeArray(
              'previousCostDetails',
              tier,
              'accumulativeOldVersionCost'
            );
          }
          const findLastSetValue = (array, dateValue, field) => array
            .find(
              (o) => o.dateValue <= dateValue && !isNotSet(o[field])
            );

          const merge = (options) => {
            const {
              dateObj,
              valueEntry,
              currentEntry,
              previous = false
            } = options;
            const field = previous ? 'previousCost' : 'cost';
            const accumulativeField = previous ? 'previous' : 'value';
            const array = previous ? previousAccumulativeArray : accumulativeArray;
            // merging cost
            if (isNotSet(valueEntry[field])) {
              console.log(`"${field}" to merge is undefined, skipping`);
            } else {
              const result = (currentEntry[field] ?? 0) + valueEntry[field];
              console.log(
                `merging "${field}":`,
                currentEntry[field],
                '(target) +',
                valueEntry[field],
                '=', result
              );
              currentEntry[field] = result;
            }
            // merging accumulative value
            if (isNotSet(valueEntry[accumulativeField])) {
              console.log(`"${accumulativeField}" value to merge is undefined, skipping`);
            } else {
              const last = findLastSetValue(array, dateObj.dateValue, accumulativeField);
              const result = (last?.[accumulativeField] ?? 0) + valueEntry[accumulativeField];
              console.log(
                `merging "${accumulativeField}":`,
                last?.[accumulativeField],
                `(target from ${last?.date || currentEntry.date}) +`,
                valueEntry[accumulativeField],
                '=',
                result
              );
              currentEntry[accumulativeField] = result;
            }
          };
          const mergeTier = (options) => {
            const {
              tier,
              dateObj,
              valueEntry,
              currentEntry,
              previous = false,
              oldVersions = false
            } = options;
            const costDetailsField = previous ? 'previousCostDetails' : 'costDetails';
            const field = oldVersions
              ? 'oldVersionCost'
              : 'cost';
            const accumulativeField = oldVersions
              ? 'accumulativeOldVersionCost'
              : 'accumulativeCost';
            const array = (() => {
              if (previous) {
                if (oldVersions) {
                  return tiersPreviousAccumulativeOldVersions[tier] ?? [];
                }
                return tiersPreviousAccumulative[tier] ?? [];
              }
              if (oldVersions) {
                return tiersAccumulativeOldVersions[tier] ?? [];
              }
              return tiersAccumulative[tier] ?? [];
            })();
            // merging cost
            const valueEntryCost = getEntryTierValue(valueEntry, costDetailsField, tier, field);
            const currentEntryCost = getEntryTierValue(currentEntry, costDetailsField, tier, field);
            if (isNotSet(valueEntryCost)) {
              console.log(`"${costDetailsField}.${field}" to merge is undefined, skipping`);
            } else {
              const result = (currentEntryCost ?? 0) + valueEntryCost;
              console.log(
                `merging "${costDetailsField}.${field}":`,
                currentEntryCost,
                '(target) +',
                valueEntryCost,
                '=', result
              );
              setEntryTierValue(currentEntry, costDetailsField, tier, field, result);
            }
            // merging accumulative value
            const valueEntryAccumulativeCost = getEntryTierValue(
              valueEntry,
              costDetailsField,
              tier,
              accumulativeField
            );
            if (isNotSet(valueEntryAccumulativeCost)) {
              // eslint-disable-next-line max-len
              console.log(`"${costDetailsField}.${accumulativeField}" value to merge is undefined, skipping`);
            } else {
              const last = findLastSetValue(array, dateObj.dateValue, accumulativeField);
              const result = (last?.[accumulativeField] ?? 0) + valueEntryAccumulativeCost;
              console.log(
                `merging "${costDetailsField}.${accumulativeField}":`,
                last?.[accumulativeField],
                `(target from ${last?.date || currentEntry.date}) +`,
                valueEntryAccumulativeCost,
                '=',
                result
              );
              setEntryTierValue(currentEntry, costDetailsField, tier, accumulativeField, result);
            }
          };

          for (const dateObj of dates) {
            console.groupCollapsed(dateObj.date);
            const valueEntry = values.find((entry) => entry.date === dateObj.date);
            if (!valueEntry) {
              console.log('nothing to merge');
              console.groupEnd();
              continue;
            }
            console.log('entry to merge:', printEntry(valueEntry));
            let currentEntry = current.find((entry) => entry.date === dateObj.date);
            if (currentEntry) {
              console.log('target entry:', printEntry(currentEntry));
            } else {
              console.log('creating target entry');
              currentEntry = {...dateObj};
              current.push(currentEntry);
              current.sort(sorter);
            }
            merge({dateObj, valueEntry, currentEntry, previous: false});
            merge({dateObj, valueEntry, currentEntry, previous: true});
            for (const tier of allTiers) {
              mergeTier({
                dateObj,
                currentEntry,
                valueEntry,
                tier,
                previous: false,
                oldVersions: false
              });
              mergeTier({
                dateObj,
                currentEntry,
                valueEntry,
                tier,
                previous: false,
                oldVersions: true
              });
              mergeTier({
                dateObj,
                currentEntry,
                valueEntry,
                tier,
                previous: true,
                oldVersions: false
              });
              mergeTier({
                dateObj,
                currentEntry,
                valueEntry,
                tier,
                previous: true,
                oldVersions: true
              });
            }
            console.groupEnd();
          }
        }
      }
    }
    console.groupEnd();
  }
  console.groupEnd();
  return result;
}

export {
  applySummaryDiscounts,
  applyGroupedDataDiscounts,
  applyDiscountsToObjects,
  applyDiscountsToObjectProperties,
  joinSummaryDiscounts,
  simpleDiscount
};
