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

function parseFilterValue (value) {
  try {
    const json = JSON.parse(value);
    if (Array.isArray(json)) {
      return json;
    }
    return [json];
  } catch (_) {
    return undefined;
  }
}

function buildFilterValue (value) {
  if (!value) {
    return '';
  }
  if (Array.isArray(value) && value.length === 1) {
    return JSON.stringify(value[0]);
  }
  return JSON.stringify(value);
}

export function parse (queryParameters = {}) {
  if (!queryParameters) {
    return {};
  }
  return Object.entries(queryParameters)
    .map(([key, values]) => ({key, values: parseFilterValue(values)}))
    .filter(({values}) => values && values.length > 0);
}

export function build (filters) {
  if (!filters) {
    return undefined;
  }
  const parameters = (filters || [])
    .filter(({key, values}) => key && values && (!Array.isArray(values) || values.length > 0))
    .sort((a, b) => a.key.localeCompare(b.key))
    .map(({key, values}) =>
      `${encodeURIComponent(key)}=${encodeURIComponent(buildFilterValue(values))}`
    );
  if (parameters.length) {
    return parameters.join('&');
  }
  return undefined;
}

export function filtersChanged (filtersA, filtersB) {
  return build(filtersA) !== build(filtersB);
}
