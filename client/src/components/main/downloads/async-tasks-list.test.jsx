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
import {render, screen, within, click, hover, fn} from '@test';
import AsyncTasksList from './async-tasks-list';

const SUCCESS_TASK = {
  id: 1,
  status: 'SUCCESS',
  params: {archiveName: 'archive-success', storageId: 7, paths: ['a/', 'b.txt']},
  created: '2026-10-21T13:13:02.274Z',
  finished: '2026-10-21T13:15:19.689Z'
};

const RUNNING_TASK = {
  id: 2,
  status: 'RUNNING',
  params: {archiveName: 'archive-running', storageId: 7, paths: ['c.txt']},
  created: '2026-10-21 13:15:19.689'
};

const FAILED_TASK = {
  id: 3,
  status: 'FAILED',
  errorMessage: 'Something went wrong',
  params: {archiveName: 'archive-failed', storageId: 7, paths: ['d.txt']},
  created: '2026-10-21T13:13:02.274Z',
  finished: '2026-10-21T13:16:00.000Z'
};

const PENDING_TASK = {
  id: 5,
  status: 'PENDING',
  params: {archiveName: 'archive-pending', storageId: 7, paths: ['e.txt']},
  created: '2026-10-21T13:13:02.274Z'
};

const CANCEL_REQUESTED_TASK = {
  id: 6,
  status: 'CANCEL_REQUESTED',
  params: {archiveName: 'archive-cancel-requested', storageId: 7, paths: ['f.txt']},
  created: '2026-10-21T13:13:02.274Z'
};

const STOPPING_TASK = {
  id: 7,
  status: 'STOPPING',
  params: {archiveName: 'archive-stopping', storageId: 7, paths: ['g.txt']},
  created: '2026-10-21T13:13:02.274Z'
};

const CANCELLED_TASK = {
  id: 8,
  status: 'CANCELLED',
  params: {archiveName: 'archive-cancelled', storageId: 7, paths: ['h.txt']},
  created: '2026-10-21T13:13:02.274Z'
};

const EXPIRED_TASK = {
  id: 9,
  status: 'EXPIRED',
  params: {archiveName: 'archive-expired', storageId: 7, paths: ['i.txt']},
  created: '2026-10-21T13:13:02.274Z'
};

function rowFor (archiveName) {
  return screen.getByText(archiveName).closest('tr');
}

test('renders a row per task, found by the archive name', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(screen.getByText('archive-success')).toBeInTheDocument();
  expect(screen.getByText('archive-running')).toBeInTheDocument();
});

test('shows the Download button only for a SUCCESS task', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(within(rowFor('archive-success')).getByRole('button', {name: /Download/i}))
    .toBeInTheDocument();
  expect(within(rowFor('archive-running')).queryByRole('button', {name: /Download/i}))
    .not.toBeInTheDocument();
});

test('shows the Cancel button only for an active task', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(within(rowFor('archive-running')).getByRole('button', {name: /Cancel/i}))
    .toBeInTheDocument();
  expect(within(rowFor('archive-success')).queryByRole('button', {name: /Cancel/i}))
    .not.toBeInTheDocument();
});

test('calls onDownload with the task after clicking Download', async () => {
  const onDownload = fn();
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} onDownload={onDownload} />);

  await click(within(rowFor('archive-success')).getByRole('button', {name: /Download/i}));

  expect(onDownload).toHaveBeenCalledWith(SUCCESS_TASK);
});

test('calls onCancel with the task after clicking Cancel', async () => {
  const onCancel = fn();
  render(<AsyncTasksList tasks={[RUNNING_TASK]} onCancel={onCancel} />);

  await click(within(rowFor('archive-running')).getByRole('button', {name: /Cancel/i}));

  expect(onCancel).toHaveBeenCalledWith(RUNNING_TASK);
});

test('exposes a FAILED task\'s errorMessage through its tooltip', async () => {
  render(<AsyncTasksList tasks={[FAILED_TASK]} />);

  // Tooltip is rc-trigger based: its content only mounts once hovered (see
  // client/test/AGENTS.md, "mouseenter/mouseleave do not reach a component").
  await hover(screen.getByText('FAILED'));

  expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
});

test('a lowercase status still gets the FAILED CSS class and tooltip', async () => {
  const lowercaseFailedTask = {
    ...FAILED_TASK,
    id: 4,
    status: 'failed',
    params: {...FAILED_TASK.params, archiveName: 'archive-lowercase-failed'}
  };
  render(<AsyncTasksList tasks={[lowercaseFailedTask]} />);

  const statusText = screen.getByText('failed');
  expect(statusText).toHaveClass('cp-danger');

  await hover(statusText);

  expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
});

test('renders both API timestamp shapes through displayDate', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  // SUCCESS_TASK.created is ISO ('2026-10-21T13:13:02.274Z'), RUNNING_TASK.created
  // is space-separated ('2026-10-21 13:15:19.689') — both formats the API sends.
  // displayDate formats both the same way, and never the raw string.
  expect(screen.queryByText('2026-10-21T13:13:02.274Z')).not.toBeInTheDocument();
  expect(screen.queryByText('2026-10-21 13:15:19.689')).not.toBeInTheDocument();
  expect(screen.getAllByText('2026-10-21 13:13:02').length).toBeGreaterThan(0);
  expect(screen.getAllByText('2026-10-21 13:15:19').length).toBeGreaterThan(0);
});

test('a SUCCESS task gets the cp-success class, not cp-primary', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} />);

  const statusText = within(rowFor('archive-success')).getByText('SUCCESS');
  expect(statusText).toHaveClass('cp-success');
  expect(statusText).not.toHaveClass('cp-primary');
});

// Every one of the four "still working" statuses, driven through one loop
// rather than four copy-pasted tests, the way client/test/AGENTS.md's own
// rules favour a single assertion shape over several near-identical ones.
[PENDING_TASK, RUNNING_TASK, CANCEL_REQUESTED_TASK, STOPPING_TASK].forEach((task) => {
  test(`${task.status} is cp-primary and shows the "In progress" spinner`, () => {
    render(<AsyncTasksList tasks={[task]} />);

    const row = rowFor(task.params.archiveName);
    expect(within(row).getByText(task.status)).toHaveClass('cp-primary');
    expect(within(row).getByTitle('In progress')).toBeInTheDocument();
  });
});

[SUCCESS_TASK, FAILED_TASK, CANCELLED_TASK, EXPIRED_TASK].forEach((task) => {
  test(`the "In progress" spinner is absent for ${task.status}`, () => {
    render(<AsyncTasksList tasks={[task]} />);

    expect(within(rowFor(task.params.archiveName)).queryByTitle('In progress'))
      .not.toBeInTheDocument();
  });
});

test('CANCELLED and EXPIRED keep the cp-text-not-important class', () => {
  render(<AsyncTasksList tasks={[CANCELLED_TASK, EXPIRED_TASK]} />);

  expect(within(rowFor('archive-cancelled')).getByText('CANCELLED'))
    .toHaveClass('cp-text-not-important');
  expect(within(rowFor('archive-expired')).getByText('EXPIRED'))
    .toHaveClass('cp-text-not-important');
});

test('renders the archive size through displaySize', () => {
  const task = {...SUCCESS_TASK, result: {archiveSizeBytes: 1234567}};
  render(<AsyncTasksList tasks={[task]} />);

  // 1234567 bytes / 1024 / 1024 is 1.177…, which displaySize formats as "1.18 Mb".
  expect(within(rowFor('archive-success')).getByText('1.18 Mb')).toBeInTheDocument();
});

test('renders a dash when the result carries no archiveSizeBytes', () => {
  const task = {...SUCCESS_TASK, result: {}};
  render(<AsyncTasksList tasks={[task]} />);

  expect(within(rowFor('archive-success')).getByText('—')).toBeInTheDocument();
});

test('renders a dash when a SUCCESS task has no result at all', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} />);

  expect(within(rowFor('archive-success')).getByText('—')).toBeInTheDocument();
});

test('renders a dash, not "0 bytes", when archiveSizeBytes is null', () => {
  const task = {...SUCCESS_TASK, result: {archiveSizeBytes: null}};
  render(<AsyncTasksList tasks={[task]} />);

  const row = rowFor('archive-success');
  expect(within(row).getByText('—')).toBeInTheDocument();
  expect(within(row).queryByText('0 bytes')).not.toBeInTheDocument();
});

// The Status column's own filter dropdown - a custom antd Table column
// filter, copying client/src/components/runs/run-table/columns/
// column-run.js's StatusesRegionsFilterComponent (a Checkbox per value, OK/
// Clear links). Its own open/pending-checkbox state is local UI mechanics;
// the *committed* filter is owned by the parent through `statusesFilter`/
// `onStatusesFilterChange`, so this component stays free of @inject and a
// model import either way. The dropdown's overlay renders through rc-trigger
// into a portal, so it is queried via `screen`, the same caveat phase 6's
// modal and phase 14's popover already had to work around (client/test/
// AGENTS.md, "mouseenter/mouseleave do not reach a component", applies to
// rc-trigger content generally). The filter icon is found by its own
// `aria-label`, not by antd's `title` - that title is always antd's current
// locale's `filterTitle` (no LocaleProvider is configured in this harness,
// so it renders in antd's default zh-CN today), which is exactly the
// antd-internals coupling rule 6 warns against.
function openStatusesFilter () {
  return click(screen.getByLabelText('Filter by status'));
}

test('ticking a status and clicking OK calls onStatusesFilterChange with it', async () => {
  const onStatusesFilterChange = fn();
  render(<AsyncTasksList tasks={[]} onStatusesFilterChange={onStatusesFilterChange} />);

  await openStatusesFilter();
  await click(screen.getByRole('checkbox', {name: 'PENDING'}));
  await click(screen.getByText('OK'));

  expect(onStatusesFilterChange).toHaveBeenCalledWith(['PENDING']);
});

test(
  'clicking Clear calls onStatusesFilterChange with no statuses, never an empty array',
  async () => {
    const onStatusesFilterChange = fn();
    render(
      <AsyncTasksList
        tasks={[]}
        statusesFilter={['PENDING']}
        onStatusesFilterChange={onStatusesFilterChange}
      />
    );

    await openStatusesFilter();
    await click(screen.getByText('Clear'));

    expect(onStatusesFilterChange).toHaveBeenCalledWith(undefined);
  }
);

test('ticking a box without clicking OK does not call onStatusesFilterChange', async () => {
  const onStatusesFilterChange = fn();
  render(<AsyncTasksList tasks={[]} onStatusesFilterChange={onStatusesFilterChange} />);

  await openStatusesFilter();
  await click(screen.getByRole('checkbox', {name: 'RUNNING'}));

  expect(onStatusesFilterChange).not.toHaveBeenCalled();
});

test('reopening the status filter re-ticks the already-committed statuses', async () => {
  render(<AsyncTasksList tasks={[]} statusesFilter={['PENDING', 'RUNNING']} />);

  await openStatusesFilter();

  expect(screen.getByRole('checkbox', {name: 'PENDING'})).toBeChecked();
  expect(screen.getByRole('checkbox', {name: 'RUNNING'})).toBeChecked();
  expect(screen.getByRole('checkbox', {name: 'FAILED'})).not.toBeChecked();
});

// The pagination row beneath the table. `/async-task/filter` returns no
// total, so there is no last-page button - only first/prev/next, copying
// client/src/components/pipelines/browser/data-storage/components/
// storage-pagination.js. Rendered only when there is something to navigate
// (`page > 1 || hasNextPage`), per the "a single short page would be noise"
// decision, so the absent-row cases below assert there is nothing to find
// rather than a disabled button.
test('renders no pagination row on page 1 with no next page', () => {
  render(<AsyncTasksList tasks={[]} page={1} hasNextPage={false} />);

  expect(screen.queryByRole('button', {name: /next page/i})).not.toBeInTheDocument();
  expect(screen.queryByRole('button', {name: /previous page/i})).not.toBeInTheDocument();
  expect(screen.queryByRole('button', {name: /first page/i})).not.toBeInTheDocument();
});

test('renders the pagination row once there is a next page', async () => {
  const onPageChange = fn();
  render(
    <AsyncTasksList tasks={[]} page={1} hasNextPage onPageChange={onPageChange} />
  );

  expect(screen.getByText('1')).toBeInTheDocument();
  await click(screen.getByRole('button', {name: /next page/i}));

  expect(onPageChange).toHaveBeenCalledWith(2);
});

test('on page 3, First calls onPageChange(1) and Previous calls onPageChange(2)', async () => {
  const onPageChange = fn();
  render(
    <AsyncTasksList tasks={[]} page={3} hasNextPage onPageChange={onPageChange} />
  );

  expect(screen.getByText('3')).toBeInTheDocument();

  await click(screen.getByRole('button', {name: /first page/i}));
  expect(onPageChange).toHaveBeenCalledWith(1);

  await click(screen.getByRole('button', {name: /previous page/i}));
  expect(onPageChange).toHaveBeenCalledWith(2);
});

test('First and Previous are disabled on page 1 while Next stays enabled', () => {
  render(<AsyncTasksList tasks={[]} page={1} hasNextPage />);

  expect(screen.getByRole('button', {name: /first page/i})).toBeDisabled();
  expect(screen.getByRole('button', {name: /previous page/i})).toBeDisabled();
  expect(screen.getByRole('button', {name: /next page/i})).not.toBeDisabled();
});

test('Next is disabled when hasNextPage is false, on a page beyond the first', () => {
  render(<AsyncTasksList tasks={[]} page={2} hasNextPage={false} />);

  expect(screen.getByRole('button', {name: /next page/i})).toBeDisabled();
  expect(screen.getByRole('button', {name: /first page/i})).not.toBeDisabled();
  expect(screen.getByRole('button', {name: /previous page/i})).not.toBeDisabled();
});
