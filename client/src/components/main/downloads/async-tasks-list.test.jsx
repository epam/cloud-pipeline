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
import {observable} from 'mobx';
import {
  render,
  renderWithLocale,
  screen,
  within,
  click,
  hover,
  fn,
  useFakeTimers,
  useRealTimers,
  advanceTimers
} from '@test';
import AsyncTasksList from './async-tasks-list';
import {POLLED_STATUSES} from '../../../models/asyncTask/statuses';

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

// Every fixture's `archiveName` above is bare (no `.zip`), so the component's
// own display logic always appends the suffix - this is what every `rowFor`
// lookup below actually finds in the DOM. The dedicated ".zip suffix" suite
// further down exercises that display logic directly.
function displayedArchiveName (archiveName) {
  return `${archiveName}.zip`;
}

function rowFor (archiveName) {
  return screen.getByText(displayedArchiveName(archiveName)).closest('tr');
}

function authenticatedUserInfoDouble (admin = true) {
  return observable({
    loaded: true,
    value: {userName: 'current-user', admin}
  });
}

test('renders a row per task, found by the archive name', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(screen.getByText('archive-success.zip')).toBeInTheDocument();
  expect(screen.getByText('archive-running.zip')).toBeInTheDocument();
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

// The Archive column appends ".zip" the same way the backend's own
// DataStorageDownloadArchiveManager.offeredArchiveName does, so the name
// shown here always matches the file the user actually receives.
describe('the Archive column\'s .zip suffix', () => {
  test('appends .zip to a bare name', () => {
    const task = {...SUCCESS_TASK, params: {...SUCCESS_TASK.params, archiveName: 'my-archive'}};
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('my-archive.zip')).toBeInTheDocument();
    expect(screen.queryByText('my-archive')).not.toBeInTheDocument();
  });

  test('leaves a name that already ends in .zip unchanged, never doubled', () => {
    const task = {
      ...SUCCESS_TASK,
      params: {...SUCCESS_TASK.params, archiveName: 'my-archive.zip'}
    };
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('my-archive.zip')).toBeInTheDocument();
    expect(screen.queryByText('my-archive.zip.zip')).not.toBeInTheDocument();
  });

  test('treats the suffix check case-insensitively', () => {
    const task = {
      ...SUCCESS_TASK,
      params: {...SUCCESS_TASK.params, archiveName: 'my-archive.ZIP'}
    };
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('my-archive.ZIP')).toBeInTheDocument();
  });

  test('applies to the task-id fallback when archiveName is absent', () => {
    const task = {id: 123, status: 'SUCCESS', created: '2026-10-21T13:13:02.274Z'};
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('123.zip')).toBeInTheDocument();
  });
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
// rc-trigger content generally).
//
// The column supplies no custom `filterIcon` (see async-tasks-list.js's own
// comment, above its class declaration, for why), so the icon opened here is
// antd's own default one, reached by its real English `title` ("Filter
// menu", `antd/lib/locale-provider/en_US.js`'s `filterTitle`) - which only
// renders instead of antd's bundled zh-CN default once the tree sits inside
// a `<LocaleProvider>`, which `renderWithLocale` from `@test` provides, the
// way src/components/main/App.js wraps the real application. Scoped to the
// Status column's own header cell, because the Owner column (admin-only,
// further down this file) renders the identical default icon under the
// same title when both are present.
function openStatusesFilter () {
  const header = screen.getByText('Status').closest('th');
  return click(within(header).getByTitle('Filter menu'));
}

test(
  'ticking a status not already in the default and clicking OK adds it',
  async () => {
    const onStatusesFilterChange = fn();
    renderWithLocale(
      <AsyncTasksList tasks={[]} onStatusesFilterChange={onStatusesFilterChange} />
    );

    // `statusesFilter` defaults to POLLED_STATUSES (see defaultProps), which
    // already has PENDING/RUNNING/CANCEL_REQUESTED/STOPPING/SUCCESS/FAILED
    // ticked when the dropdown opens - CANCELLED is the one still unticked.
    await openStatusesFilter();
    await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
    await click(screen.getByText('OK'));

    expect(onStatusesFilterChange).toHaveBeenCalledTimes(1);
    const [committed] = onStatusesFilterChange.mock.calls[0];
    expect([...committed].sort()).toEqual([...POLLED_STATUSES, 'CANCELLED'].sort());
  }
);

test(
  'clicking Clear calls onStatusesFilterChange with POLLED_STATUSES, never undefined',
  async () => {
    const onStatusesFilterChange = fn();
    renderWithLocale(
      <AsyncTasksList
        tasks={[]}
        statusesFilter={['PENDING']}
        onStatusesFilterChange={onStatusesFilterChange}
      />
    );

    await openStatusesFilter();
    await click(screen.getByText('Clear'));

    expect(onStatusesFilterChange).toHaveBeenCalledWith(POLLED_STATUSES);
  }
);

test('ticking a box without clicking OK does not call onStatusesFilterChange', async () => {
  const onStatusesFilterChange = fn();
  renderWithLocale(
    <AsyncTasksList tasks={[]} onStatusesFilterChange={onStatusesFilterChange} />
  );

  await openStatusesFilter();
  await click(screen.getByRole('checkbox', {name: 'RUNNING'}));

  expect(onStatusesFilterChange).not.toHaveBeenCalled();
});

test('reopening the status filter re-ticks the already-committed statuses', async () => {
  renderWithLocale(<AsyncTasksList tasks={[]} statusesFilter={['PENDING', 'RUNNING']} />);

  await openStatusesFilter();

  expect(screen.getByRole('checkbox', {name: 'PENDING'})).toBeChecked();
  expect(screen.getByRole('checkbox', {name: 'RUNNING'})).toBeChecked();
  expect(screen.getByRole('checkbox', {name: 'FAILED'})).not.toBeChecked();
});

// The statuses filter's new default, and the comparison that decides whether
// the header's filter icon lights up. This is where the behaviour is most
// likely to regress, since `filteredValue` now compares by value rather than
// by "is anything set at all" - see async-tasks-list.js's own comment on it.
//
// `filteredValue` is read straight off the rendered `columns` getter via a
// ref, not off a DOM class. antd's own `FilterMenu` does apply a `-selected`
// class to its default filter icon once `filteredValue` lands here (that is
// the production bug this change fixes - see async-tasks-list.js's own
// comment above its class declaration), but that class is antd's own
// internal, not this component's - asserting on it here would be exactly
// the antd-internals coupling rule 6 warns against. Reading `filteredValue`
// off `columns` instead asserts on this component's own output, which is
// what the decision is actually about. Neither
// client/src/components/runs/run-table/columns/column-run.js nor
// column-owner.js has a test file at all, so there is no precedent here for
// asserting on antd's own highlight either - only for not trying to.
describe('the Status column default (POLLED_STATUSES)', () => {
  function statusFilteredValue (instance) {
    return instance.columns.find((column) => column.key === 'status').filteredValue;
  }

  test('is not active at the default, with no statusesFilter prop at all', () => {
    let instance;
    render(<AsyncTasksList tasks={[]} ref={(r) => { instance = r; }} />);

    expect(statusFilteredValue(instance)).toEqual([]);
  });

  test('is not active when POLLED_STATUSES is passed explicitly', () => {
    let instance;
    render(
      <AsyncTasksList
        tasks={[]}
        statusesFilter={POLLED_STATUSES}
        ref={(r) => { instance = r; }}
      />
    );

    expect(statusFilteredValue(instance)).toEqual([]);
  });

  test('is not active when the default arrives in a shuffled order', () => {
    let instance;
    const shuffled = [...POLLED_STATUSES].reverse();
    render(
      <AsyncTasksList tasks={[]} statusesFilter={shuffled} ref={(r) => { instance = r; }} />
    );

    expect(statusFilteredValue(instance)).toEqual([]);
  });

  test('becomes active once the selection differs from the default', () => {
    let instance;
    render(
      <AsyncTasksList
        tasks={[]}
        statusesFilter={[...POLLED_STATUSES, 'CANCELLED']}
        ref={(r) => { instance = r; }}
      />
    );

    expect(statusFilteredValue(instance)).toEqual(['filtered']);
  });

  test(
    'ticking everything off and pressing OK reports the default, not an empty list',
    async () => {
      const onStatusesFilterChange = fn();
      renderWithLocale(
        <AsyncTasksList
          tasks={[]}
          statusesFilter={['PENDING']}
          onStatusesFilterChange={onStatusesFilterChange}
        />
      );

      await openStatusesFilter();
      await click(screen.getByRole('checkbox', {name: 'PENDING'}));
      await click(screen.getByText('OK'));

      expect(onStatusesFilterChange).toHaveBeenCalledWith(POLLED_STATUSES);
    }
  );
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

// The Storage column resolves `task.params.storageId` against the
// `storageNamesById` lookup the container builds from the `dataStorages`
// singleton. Every fixture above carries `storageId: 7`, so these reuse
// SUCCESS_TASK rather than adding new fixtures.
test('resolves a known storageId to its name, and hides the raw id', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} storageNamesById={{7: 'my-storage'}} />);

  const row = rowFor('archive-success');
  expect(within(row).getByText('my-storage')).toBeInTheDocument();
  expect(within(row).queryByText('7')).not.toBeInTheDocument();
});

test('falls back to the raw id when the storageId has no entry in the map', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} storageNamesById={{99: 'some-other-storage'}} />);

  expect(within(rowFor('archive-success')).getByText('7')).toBeInTheDocument();
});

test('falls back to the raw id, without throwing, when storageNamesById is omitted', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} />);

  expect(within(rowFor('archive-success')).getByText('7')).toBeInTheDocument();
});

test('still resolves a name when the task\'s storageId arrives as a string', () => {
  const task = {...SUCCESS_TASK, params: {...SUCCESS_TASK.params, storageId: '7'}};
  render(<AsyncTasksList tasks={[task]} storageNamesById={{7: 'my-storage'}} />);

  expect(within(rowFor('archive-success')).getByText('my-storage')).toBeInTheDocument();
});

// The Items column: a count, wrapped in a Popover listing the archived
// paths. The popover is built on rc-trigger (antd's Popover), so its content
// only mounts once hovered (client/test/AGENTS.md, "mouseenter/mouseleave do
// not reach a component") and `mouseEnterDelay={1}` defers that open by a
// second via setTimeout - hence the fake-timer dance below, rather than
// waiting on wall time.
test('shows the correct item count for a populated task', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(within(rowFor('archive-success')).getByText('2')).toBeInTheDocument();
  expect(within(rowFor('archive-running')).getByText('1')).toBeInTheDocument();
});

test('hovering the count shows every archived path', async () => {
  useFakeTimers();
  try {
    render(<AsyncTasksList tasks={[SUCCESS_TASK]} />);

    await hover(within(rowFor('archive-success')).getByText('2'));
    advanceTimers(1000);

    // The popover renders into a portal outside the table's subtree, so it
    // is searched for from `screen`, never from `within(rowFor(...))`.
    expect(await screen.findByText('a/')).toBeInTheDocument();
    expect(await screen.findByText('b.txt')).toBeInTheDocument();
  } finally {
    useRealTimers();
  }
});

// All three shapes the count's own guard already treats as "nothing to
// show": no `params` at all, `params` with no `paths`, and an empty `paths`
// array. Each renders the bare count (0) with no Popover at all - an empty
// popover that opens on hover and shows nothing would be worse than no
// affordance.
[
  {
    description: 'params is absent entirely',
    task: {id: 20, status: 'SUCCESS', created: '2026-10-21T13:13:02.274Z'}
  },
  {
    description: 'params carries no paths key',
    task: {
      id: 21,
      status: 'SUCCESS',
      params: {archiveName: 'archive-no-paths-key', storageId: 7},
      created: '2026-10-21T13:13:02.274Z'
    }
  },
  {
    description: 'paths is an empty array',
    task: {
      id: 22,
      status: 'SUCCESS',
      params: {archiveName: 'archive-empty-paths', storageId: 7, paths: []},
      created: '2026-10-21T13:13:02.274Z'
    }
  }
].forEach(({description, task}) => {
  test(`renders the bare count with no popover trigger when ${description}`, async () => {
    useFakeTimers();
    try {
      render(<AsyncTasksList tasks={[task]} />);

      await hover(screen.getByText('0'));
      advanceTimers(1000);

      expect(document.querySelector('.archivePathsPopoverContainer')).not.toBeInTheDocument();
    } finally {
      useRealTimers();
    }
  });
});

// The Owner column - admin-only, with its own UserAutoComplete-backed filter
// dropdown (client/src/components/runs/run-table/columns/column-owner.js is
// the precedent). UserAutoComplete carries its own
// `@inject('authenticatedUserInfo')`, so every case here renders through
// `renderWithLocale`'s own `stores` option (which wraps in the same
// `<Provider>` `renderWithStores` would) with that store doubled, even
// though the rest of this file never needs a Provider - see
// async-tasks-list.js's own header comment for why that narrows, rather
// than breaks, this component's "presentational" invariant. It also wraps
// in antd's `<LocaleProvider>`, needed for the same reason the Status
// column's own suite above does: opening the dropdown goes through the
// column's own default filter icon, found by its real English `title`
// ("Filter menu") rather than antd's bundled zh-CN default. These cases
// stay to the commit paths (OK/Clear/visibility) and leave
// UserAutoComplete's own search-as-you-type behaviour (which issues its own
// `UserFind` request) covered by that component's own tests - exercising it
// here would mean stubbing the network for a component this file does not
// own.
describe('the Owner column', () => {
  function renderList (props = {}) {
    return renderWithLocale(
      <AsyncTasksList tasks={[]} {...props} />,
      {stores: {authenticatedUserInfo: authenticatedUserInfoDouble()}}
    );
  }

  // Scoped to the Owner column's own header cell - see openStatusesFilter's
  // own comment above for why the Status and Owner columns' default filter
  // icons cannot be told apart by title alone once both are present.
  function openOwnerFilter () {
    const header = screen.getByText('Owner').closest('th');
    return click(within(header).getByTitle('Filter menu'));
  }

  test('is absent entirely when isAdmin is false', () => {
    renderList({isAdmin: false});

    expect(screen.queryByText('Owner')).not.toBeInTheDocument();
    // Only the Status column's own default filter icon remains - no second
    // one for an Owner column that was never rendered at all.
    expect(screen.getAllByTitle('Filter menu')).toHaveLength(1);
  });

  test('is present, with its own filter icon, when isAdmin is true', () => {
    renderList({isAdmin: true});

    expect(screen.getByText('Owner')).toBeInTheDocument();
    // One default filter icon per filterable column - Status and Owner.
    expect(screen.getAllByTitle('Filter menu')).toHaveLength(2);
  });

  test('opening the dropdown renders the autocomplete and the OK/Clear links', async () => {
    renderList({isAdmin: true});

    await openOwnerFilter();

    // antd's AutoComplete fakes its placeholder with a sibling `<div>`
    // rather than the native HTML attribute (it has to, to keep showing it
    // once a value is selected but the text input itself is empty), so
    // `getByPlaceholderText` cannot find it - `combobox` is this control's
    // own ARIA role, and the only one in this popover (the Status column's
    // own dropdown has none).
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText('OK')).toBeInTheDocument();
    expect(screen.getByText('Clear')).toBeInTheDocument();
  });

  test('OK commits the already-selected owner as a string, never an array', async () => {
    const onOwnerFilterChange = fn();
    renderList({isAdmin: true, ownerFilter: 'someone-else', onOwnerFilterChange});

    await openOwnerFilter();
    await click(screen.getByText('OK'));

    expect(onOwnerFilterChange).toHaveBeenCalledWith('someone-else');
  });

  test('Clear commits undefined, regardless of the committed owner', async () => {
    const onOwnerFilterChange = fn();
    renderList({isAdmin: true, ownerFilter: 'someone-else', onOwnerFilterChange});

    await openOwnerFilter();
    await click(screen.getByText('Clear'));

    expect(onOwnerFilterChange).toHaveBeenCalledWith(undefined);
  });

  // No accessible way to drive antd's own outside-click dismissal from this
  // harness (rc-trigger's Trigger wires that through its own internal
  // document listener, not through a role/label/text this suite can reach -
  // the Status column's own suite above does not attempt it either). Calling
  // the instance's own handler is the same "no accessible DOM hook" case
  // DownloadsManager.test.jsx already reaches into `wrappedInstance.state`
  // for - here there is no store/MobX wrapper, so the ref is the component
  // itself.
  test('closing the dropdown by any other means still commits (commit-on-close)', async () => {
    const onOwnerFilterChange = fn();
    let instance;
    renderList({
      isAdmin: true,
      ownerFilter: 'someone-else',
      onOwnerFilterChange,
      ref: (r) => { instance = r; }
    });

    await openOwnerFilter();
    instance.handleOwnerFilterVisibleChange(false);

    expect(onOwnerFilterChange).toHaveBeenCalledWith('someone-else');
  });

  // Read off `columns` via a ref, not a DOM class - the real, testable
  // signal for "is this filter active" is this component's own
  // `filteredValue`, which is what antd's own (now unmodified) default
  // filter icon highlight is driven by in production; asserting on that
  // icon's own internal class here would be exactly the antd-internals
  // coupling rule 6 warns against.
  //
  // `ownerFiltered`'s semantics: "differs from `defaultOwner`" (the current
  // user, passed down from DownloadsManager), never "is anything set at
  // all" - an owner is set from the very first load, since it is seeded by
  // default, so a lit icon from that alone would be permanently wrong. See
  // `ownerIsFiltered` in models/asyncTask/statuses.js.
  describe('filteredValue - differs from defaultOwner, not "is anything set"', () => {
    test('is empty when the committed owner equals defaultOwner, even though it is set', () => {
      let instance;
      renderList({
        isAdmin: true,
        ownerFilter: 'current-user',
        defaultOwner: 'current-user',
        ref: (r) => { instance = r; }
      });

      const ownerColumn = instance.columns.find((column) => column.key === 'owner');
      expect(ownerColumn.filteredValue).toEqual([]);
    });

    test('lights up once the committed owner differs from defaultOwner', () => {
      let instance;
      renderList({
        isAdmin: true,
        ownerFilter: 'someone-else',
        defaultOwner: 'current-user',
        ref: (r) => { instance = r; }
      });

      const ownerColumn = instance.columns.find((column) => column.key === 'owner');
      expect(ownerColumn.filteredValue).toEqual(['filtered']);
    });

    test('is empty while defaultOwner is not yet known, even with an owner already set', () => {
      let instance;
      renderList({isAdmin: true, ownerFilter: 'someone-else', ref: (r) => { instance = r; }});

      const ownerColumn = instance.columns.find((column) => column.key === 'owner');
      expect(ownerColumn.filteredValue).toEqual([]);
    });
  });
});
