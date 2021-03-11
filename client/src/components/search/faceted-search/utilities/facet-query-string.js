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

function parse (queryString) {
  const {
    query,
    filters: f
  } = queryString || {};
  const filters = (f || '')
    .split(';')
    .filter(Boolean)
    .map(filterPart => {
      const [name, ...valueParts] = filterPart.split(':');
      const values = valueParts.join(':').split(',').filter(Boolean);
      return {[name]: values};
    })
    .reduce((r, c) => ({...r, ...c}), {});
  return {
    query,
    filters
  };
}

function build (query, filters) {
  const filtersKeys = Object.keys(filters || {}).sort();
  const filtersParts = filtersKeys
    .map(key => `${key}:${(filters[key] || []).sort().join(',')}`)
    .join(';');
  const parts = [
    query ? `query=${encodeURIComponent(query)}` : false,
    filtersKeys.length > 0 ? `filters=${encodeURIComponent(filtersParts)}` : false
  ]
    .filter(Boolean);
  return parts.join('&');
}

export {build, parse};
