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

const KeyMappers = {
  value: 'previous',
  usage: 'previousUsage',
  runsCount: 'previousRunsCount'
};

export default function join (current = {}, previous = {}, keyMappers = KeyMappers) {
  const currentKeys = Object.keys(current || {});
  const previousKeys = Object.keys(previous || {});
  const keys = [...currentKeys, ...previousKeys]
    .filter((key, index, array) => array.indexOf(key) === index);
  const result = {};
  const prevKeys = Object.keys(keyMappers);
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const c = current[key] ? current[key] : {};
    const p = previous[key] ? previous[key] : {};
    const prevObj = {};
    for (let j = 0; j < prevKeys.length; j++) {
      const prevKey = prevKeys[j];
      if (p && p.hasOwnProperty(prevKey)) {
        prevObj[keyMappers[prevKey]] = p[prevKey];
        delete p[prevKey];
      }
    }
    result[key] = {
      ...p,
      value: 0,
      ...c,
      ...prevObj
    };
  }
  return result;
}
