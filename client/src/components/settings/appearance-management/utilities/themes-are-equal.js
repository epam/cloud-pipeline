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

function propertiesAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const aKeys = Object.keys(a).sort();
  const bKeys = Object.keys(b).sort();
  if (aKeys.length !== bKeys.length) {
    return false;
  }
  for (const aKey of aKeys) {
    const bKey = bKeys.find(o => o === aKey);
    if (bKey !== aKey || a[aKey] !== b[bKey]) {
      return false;
    }
  }
  return true;
}

export default function themesAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    name: aName,
    extends: aExtends,
    properties: aProperties
  } = a;
  const {
    name: bName,
    extends: bExtends,
    properties: bProperties
  } = b;
  return aName === bName &&
    aExtends === bExtends &&
    propertiesAreEqual(aProperties, bProperties);
}
