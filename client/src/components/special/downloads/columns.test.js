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

import React from 'react';
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
    statusesFiltered: false,
    typeFilterDropdown: 'type-dropdown',
    typeFilterVisible: false,
    onTypeFilterVisibleChange: () => {},
    typeFiltered: false
  }, overrides);
}

function keysOf (columns) {
  return columns.map((column) => column.key);
}

function columnBy (columns, key) {
  return columns.find((column) => column.key === key);
}

const DOWNLOAD_TASK = {
  id: 'task-1',
  type: 'DOWNLOAD_ARCHIVE',
  status: 'SUCCESS',
  params: {storageId: 7, archiveName: 'report', paths: ['a.txt', 'b/']},
  result: {archiveSizeBytes: 2048}
};

const EXTRACTION_TASK = {
  id: 'task-2',
  type: 'UNPACK_ARCHIVE',
  status: 'SUCCESS',
  params: {sourceStorageId: 7, sourcePath: 'folder/data.tar.gz', destinationPath: 'folder/'},
  result: {bytesWritten: 4096, filesWritten: 12}
};

test('omits the Owner column entirely when isAdmin is false', () => {
  const columns = buildColumns(baseOptions({isAdmin: false}));

  expect(keysOf(columns)).toEqual([
    'type', 'archive', 'storage', 'size', 'status', 'created', 'finished', 'actions'
  ]);
});

test('includes the Owner column, right after Status, when isAdmin is true', () => {
  const columns = buildColumns(baseOptions({isAdmin: true}));

  expect(keysOf(columns)).toEqual([
    'type', 'archive', 'storage', 'size', 'status', 'owner', 'created', 'finished', 'actions'
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

test('filteredValue is empty for every filterable column when no filter is active', () => {
  const columns = buildColumns(baseOptions({isAdmin: true}));

  expect(columnBy(columns, 'type').filteredValue).toEqual([]);
  expect(columnBy(columns, 'status').filteredValue).toEqual([]);
  expect(columnBy(columns, 'owner').filteredValue).toEqual([]);
});

test('wires the Type column\'s filter props through verbatim', () => {
  const onTypeFilterVisibleChange = () => {};
  const columns = buildColumns(baseOptions({
    typeFilterDropdown: 'the-type-dropdown',
    typeFilterVisible: true,
    onTypeFilterVisibleChange,
    typeFiltered: true
  }));

  const typeColumn = columnBy(columns, 'type');
  expect(typeColumn.filterDropdown).toBe('the-type-dropdown');
  expect(typeColumn.filterDropdownVisible).toBe(true);
  expect(typeColumn.onFilterDropdownVisibleChange).toBe(onTypeFilterVisibleChange);
  expect(typeColumn.filteredValue).toEqual(['filtered']);
});

describe('rendering a task of each type', () => {
  const columns = buildColumns(baseOptions({storageNamesById: {7: 'my-storage'}}));
  const render = (key, task) => columnBy(columns, key).render(task);

  test('the Type column names the type rather than showing its enum', () => {
    expect(render('type', DOWNLOAD_TASK)).toBe('Download archive');
    expect(render('type', EXTRACTION_TASK)).toBe('Extract archive');
  });

  // An extraction has no archive name of its own, so the leaf of `sourcePath`
  // identifies the row - and gets no `.zip` appended, unlike a download.
  test('the Archive column shows the extracted archive\'s own file name', () => {
    expect(render('archive', EXTRACTION_TASK)).toBe('data.tar.gz');
  });

  test('the Storage column resolves an extraction\'s source storage', () => {
    expect(render('storage', EXTRACTION_TASK)).toBe('my-storage');
    expect(render('storage', DOWNLOAD_TASK)).toBe('my-storage');
  });

  // `bytesWritten` for an extraction, `archiveSizeBytes` for a download.
  test('the Size column reads whichever field the result carries', () => {
    expect(render('size', EXTRACTION_TASK)).toBe('4.00 Kb');
    expect(render('size', DOWNLOAD_TASK)).toBe('2.00 Kb');
  });

  test('a running extraction has no size yet', () => {
    expect(render('size', {...EXTRACTION_TASK, status: 'RUNNING', result: undefined})).toBe('—');
  });
});

describe('the Actions column per task type', () => {
  function actionsFor (task) {
    const columns = buildColumns(baseOptions());
    const rendered = columnBy(columns, 'actions').render(task);
    const buttons = React.Children.toArray(rendered.props.children).filter(Boolean);
    return buttons.map((button) => button.props.id);
  }

  // `/async-task/{id}/consume` throws for an extraction, so a successful one
  // offers no Download - unlike a successful download archive.
  test('a successful extraction offers no Download button', () => {
    expect(actionsFor(EXTRACTION_TASK)).toEqual([]);
    expect(actionsFor(DOWNLOAD_TASK)).toEqual(['async-task-task-1-download']);
  });

  test('a running extraction can still be cancelled', () => {
    expect(actionsFor({...EXTRACTION_TASK, status: 'RUNNING'}))
      .toEqual(['async-task-task-2-cancel']);
  });
});
