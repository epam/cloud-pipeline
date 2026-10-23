/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import buildColumns from './columns';

// `buildColumns` is a plain, store-free function of its inputs - no @inject,
// no rendering, no DOM - so its own shape (which columns it returns, in
// which order, and how each filterable column's own props are wired through)
// is tested directly here, rather than only indirectly through
// async-tasks-list.test.jsx's rendering-based suite (which stays the home
// for what each column actually displays and how a user interacts with its
// filter dropdown - a `<Table>`-rendering concern buildColumns itself has no
// part in).
function baseOptions (overrides = {}) {
  return Object.assign({
    onCancel: () => {},
    onDownload: () => {},
    disabled: false,
    storageNamesById: {},
    isAdmin: false,
    ownerFilterDropdown: 'owner-dropdown',
    ownerFilterVisible: false,
    onOwnerFilterVisibleChange: () => {},
    ownerFiltered: false,
    statusesFilterDropdown: 'statuses-dropdown',
    statusesFilterVisible: false,
    onStatusesFilterVisibleChange: () => {},
    statusesFiltered: false
  }, overrides);
}

function keysOf (columns) {
  return columns.map((column) => column.key);
}

test('omits the Owner column entirely when isAdmin is false', () => {
  const columns = buildColumns(baseOptions({isAdmin: false}));

  expect(keysOf(columns)).toEqual([
    'archive', 'storage', 'items', 'size', 'status', 'created', 'finished', 'actions'
  ]);
});

test('includes the Owner column, right after Status, when isAdmin is true', () => {
  const columns = buildColumns(baseOptions({isAdmin: true}));

  expect(keysOf(columns)).toEqual([
    'archive', 'storage', 'items', 'size', 'status', 'owner', 'created', 'finished', 'actions'
  ]);
});

test('wires the Owner column\'s filter props through verbatim', () => {
  const onOwnerFilterVisibleChange = () => {};
  const columns = buildColumns(baseOptions({
    isAdmin: true,
    ownerFilterDropdown: 'the-owner-dropdown',
    ownerFilterVisible: true,
    onOwnerFilterVisibleChange,
    ownerFiltered: true
  }));

  const ownerColumn = columns.find((column) => column.key === 'owner');
  expect(ownerColumn.filterDropdown).toBe('the-owner-dropdown');
  expect(ownerColumn.filterDropdownVisible).toBe(true);
  expect(ownerColumn.onFilterDropdownVisibleChange).toBe(onOwnerFilterVisibleChange);
  expect(ownerColumn.filteredValue).toEqual(['filtered']);
});

test('wires the Status column\'s filter props through verbatim', () => {
  const onStatusesFilterVisibleChange = () => {};
  const columns = buildColumns(baseOptions({
    statusesFilterDropdown: 'the-statuses-dropdown',
    statusesFilterVisible: true,
    onStatusesFilterVisibleChange,
    statusesFiltered: true
  }));

  const statusColumn = columns.find((column) => column.key === 'status');
  expect(statusColumn.filterDropdown).toBe('the-statuses-dropdown');
  expect(statusColumn.filterDropdownVisible).toBe(true);
  expect(statusColumn.onFilterDropdownVisibleChange).toBe(onStatusesFilterVisibleChange);
  expect(statusColumn.filteredValue).toEqual(['filtered']);
});

test('filteredValue is empty for both filterable columns when neither filter is active', () => {
  const columns = buildColumns(baseOptions({isAdmin: true}));

  expect(columns.find((column) => column.key === 'status').filteredValue).toEqual([]);
  expect(columns.find((column) => column.key === 'owner').filteredValue).toEqual([]);
});
