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

export default function compareSubObjects (subObjectsA, subObjectsB) {
  if (!subObjectsA && !subObjectsB) {
    return true;
  }
  if (!subObjectsA || !subObjectsB) {
    return false;
  }
  const setA = new Set(subObjectsA.map(({entityId, entityClass}) => `${entityClass}|${entityId}`));
  const setB = new Set(subObjectsB.map(({entityId, entityClass}) => `${entityClass}|${entityId}`));
  if (setA.size !== setB.size) {
    return false;
  }
  for (let a of setA) {
    if (!setB.has(a)) {
      return false;
    }
  }
  return true;
}
