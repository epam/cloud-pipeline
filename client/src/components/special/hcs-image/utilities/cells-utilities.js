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

export function ascSorter (a, b) {
  return a - b;
}

export function cellsAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return a.id === b.id &&
    a.selectable === b.selectable &&
    a.info === b.info &&
    a.tags === b.tags;
}

export function cellsArraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b || a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    const aa = a[i];
    const bb = b[i];
    if (
      !b.find(bCell => cellsAreEqual(bCell, aa)) ||
      !a.find(aCell => cellsAreEqual(aCell, bb))
    ) {
      return false;
    }
  }
  return true;
}
