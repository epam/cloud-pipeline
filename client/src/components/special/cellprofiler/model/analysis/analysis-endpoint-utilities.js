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

const removeSlashes = (str) => {
  if (!str) {
    return '';
  }
  let result = str.slice();
  if (result.startsWith('/')) {
    result = result.slice(1);
  }
  if (result.endsWith('/')) {
    result = result.slice(0, -1);
  }
  return result;
};

export function getAnalysisMethodURL (endpoint, method) {
  return [endpoint, method]
    .map(removeSlashes)
    .filter(o => o.length)
    .join('/');
}
