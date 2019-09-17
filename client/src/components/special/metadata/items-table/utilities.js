/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

export function isJson (string) {
  try {
    const obj = JSON.parse(string);
    return !!obj;
  } catch (_) {}
  return false;
}

export function plural (count, itemName) {
  return `${count} ${itemName}${count !== 0 ? 's' : ''}`;
}

export function parse (string) {
  try {
    let obj = JSON.parse(string);
    if (!obj) {
      return null;
    }
    if (!Array.isArray(obj)) {
      obj = [obj];
    }
    const keys = [];
    for (let i = 0; i < obj.length; i++) {
      const ownKeys = Object.keys(obj[i]);
      for (let j = 0; j < ownKeys.length; j++) {
        const key = ownKeys[j];
        if (obj[i].hasOwnProperty(key) && keys.indexOf(key) === -1) {
          keys.push(key);
        }
      }
    }
    return {
      keys,
      items: obj,
      length: obj.length
    };
  } catch (_) {}
  return null;
}
