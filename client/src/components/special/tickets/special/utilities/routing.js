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

/**
 * @typedef {Object} TicketsRoutingProps
 * @property {number} [page=0]
 * @property {boolean} [default=true]
 * @property {string[]} [statuses]
 * @property {string} [search]
 */

function parseStatuses (statusesString) {
  return (statusesString || '')
    .split(/[,;]/)
    .filter((status) => status.length > 0);
}

function buildStatusesQuery (statuses = []) {
  return statuses.join(',');
}

/**
 * @param {string|undefined} query
 * @returns {TicketsRoutingProps}
 */
export function parseTicketsFilters (query) {
  if (!query || !query.length) {
    return {
      page: 1,
      default: true
    };
  }
  const {
    default: defaultFilters,
    statuses,
    search,
    page
  } = query
    .slice(query.startsWith('?') ? 1 : 0)
    .split('&')
    .map((part) => part.split('='))
    .map(([key, value]) => ({
      [decodeURIComponent(key)]: value ? decodeURIComponent(value) : undefined
    }))
    .reduce((r, c) => ({...r, ...c}), {});
  let statusesList = parseStatuses(statuses);
  let searchValue = search;
  let isDefault = statusesList.length === 0;
  if (isDefault && defaultFilters !== undefined) {
    isDefault = /^true$/i.test(defaultFilters);
  }
  if (isDefault) {
    statusesList = undefined;
  }
  return {
    page: page !== undefined && !Number.isNaN(Number(page)) ? Number(page) : 1,
    default: isDefault,
    statuses: statusesList,
    search: searchValue
  };
}

/**
 * @param {TicketsRoutingProps} filters
 * @returns {string|undefined}
 */
export function buildTicketsFiltersQuery (filters) {
  const {
    page = 1,
    search = undefined,
    statuses = [],
    default: defaultFilters = !statuses || !statuses.length
  } = filters || {};
  const isDefault = statuses && statuses.length > 0
    ? false
    : defaultFilters;
  if (page === 1 && isDefault) {
    return undefined;
  }
  const query = {};
  query.search = search && search.length > 0 ? search : undefined;
  if (!isDefault) {
    query.statuses = statuses && statuses.length > 0 ? buildStatusesQuery(statuses) : undefined;
    if (!query.statuses) {
      query.default = false;
    }
  }
  if (page > 1) {
    query.page = page;
  }
  return Object.entries(query)
    .filter(([key, value]) => value !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(`${value}`)}`)
    .join('&');
}

export function ticketsFiltersAreEqual (a, b) {
  return buildTicketsFiltersQuery(a) === buildTicketsFiltersQuery(b);
}
