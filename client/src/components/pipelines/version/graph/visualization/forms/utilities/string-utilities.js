/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

export function clearQuotes (string) {
  if (!string) {
    return undefined;
  }
  const e = /^("(.+)"|'(.+)'|`(.+)`)$/.exec(string);
  if (e) {
    return e[2] || e[3] || e[4];
  }
  return string;
}

export function capitalizeFirstLetter (string) {
  if (!string) {
    return string;
  }
  return string.slice(0, 1).toUpperCase().concat(string.slice(1));
}
