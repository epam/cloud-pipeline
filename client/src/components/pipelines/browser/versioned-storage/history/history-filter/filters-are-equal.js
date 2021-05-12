/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

export default function filtersAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    users: aUsers = [],
    extensions: aExtensions = [],
    dateFrom: aDateFrom,
    dateTo: aDateTo
  } = a;
  const {
    users: bUsers = [],
    extensions: bExtensions = [],
    dateFrom: bDateFrom,
    dateTo: bDateTo
  } = b;
  const aUsersSorted = [...(new Set(aUsers))].sort();
  const bUsersSorted = [...(new Set(bUsers))].sort();
  if (aUsersSorted.length !== bUsersSorted.length) {
    return false;
  }
  const aExtensionsSorted = [...(new Set(aExtensions))].sort();
  const bExtensionsSorted = [...(new Set(bExtensions))].sort();
  if (aExtensionsSorted.length !== bExtensionsSorted.length) {
    return false;
  }
  if (aDateFrom !== bDateFrom || aDateTo !== bDateTo) {
    return false;
  }
  for (let u = 0; u < aUsersSorted.length; u++) {
    if (aUsersSorted[u] !== bUsersSorted[u]) {
      return false;
    }
  }
  for (let e = 0; e < aExtensionsSorted.length; e++) {
    if (aExtensionsSorted[e] !== bExtensionsSorted[e]) {
      return false;
    }
  }
  return true;
}
