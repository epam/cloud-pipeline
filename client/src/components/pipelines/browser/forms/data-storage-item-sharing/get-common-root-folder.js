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

function getCommonPathComponents (pathComponents1 = [], pathComponents2 = []) {
  const result = [];
  for (let i = 0; i < Math.min(pathComponents1.length, pathComponents2.length); i += 1) {
    if (pathComponents1[i] !== pathComponents2[i]) {
      break;
    }
    result.push(pathComponents1[i]);
  }
  return result;
}

export default function getCommonRootFolder (items = [], delimiter = '/') {
  const result = items
    .reduce(
      (commonPathComponents, item) => {
        const {path, type} = item;
        const components = (path || '')
          .split(delimiter)
          .slice(0, /^folder$/i.test(type) ? undefined : -1);
        if (commonPathComponents === undefined) {
          return components;
        }
        return getCommonPathComponents(commonPathComponents, components);
      },
      undefined
    );
  return result && result.length > 0
    ? result.join(delimiter)
    : undefined;
}
