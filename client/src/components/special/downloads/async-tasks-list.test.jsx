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
  change,
  clear,
  blur,
  hover,
  waitFor,
  fn,
  stubApi,
  flushPromises,
  useFakeTimers,
  useRealTimers,
  advanceTimers
} from '@test';
import AsyncTasksList from './async-tasks-list';
import {ALL_ASYNC_TASK_STATUSES, POLLED_STATUSES} from '../../../models/asyncTask/statuses';

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
// own display logic always appends the suffix - and, for a task with paths,
// the item count after it: `archive-success.zip (2 items)`. A substring match
// is what makes `rowFor` independent of that count; the suites further down
// exercise the suffix and the count themselves.
function displayedArchiveName (archiveName) {
  return `${archiveName}.zip`;
}

function rowFor (archiveName) {
  const name = displayedArchiveName(archiveName);
  // The name exactly, or the name followed by its item count - and not a
  // loose prefix match, which would make every lookup here ambiguous the day
  // a fixture named `archive-success-2` is added beside `archive-success`.
  return screen
    .getByText((content) => content === name || content.startsWith(`${name} (`))
    .closest('tr');
}

function authenticatedUserInfoDouble (admin = true) {
  return observable({
    loaded: true,
    value: {userName: 'current-user', admin}
  });
}

test('renders a row per task, found by the archive name and its item count', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  // Two paths and one path respectively - the count, and its plural, are part
  // of what the Archive column shows.
  expect(screen.getByText('archive-success.zip (2 items)')).toBeInTheDocument();
  expect(screen.getByText('archive-running.zip (1 item)')).toBeInTheDocument();
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
// DownloadArchiveTaskProcessor.offeredArchiveName does, so the name
// shown here always matches the file the user actually receives.
describe('the Archive column\'s .zip suffix', () => {
  test('appends .zip to a bare name', () => {
    const task = {...SUCCESS_TASK, params: {...SUCCESS_TASK.params, archiveName: 'my-archive'}};
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('my-archive.zip (2 items)')).toBeInTheDocument();
    expect(screen.queryByText('my-archive (2 items)')).not.toBeInTheDocument();
  });

  test('leaves a name that already ends in .zip unchanged, never doubled', () => {
    const task = {
      ...SUCCESS_TASK,
      params: {...SUCCESS_TASK.params, archiveName: 'my-archive.zip'}
    };
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('my-archive.zip (2 items)')).toBeInTheDocument();
    expect(screen.queryByText('my-archive.zip.zip (2 items)')).not.toBeInTheDocument();
  });

  test('treats the suffix check case-insensitively', () => {
    const task = {
      ...SUCCESS_TASK,
      params: {...SUCCESS_TASK.params, archiveName: 'my-archive.ZIP'}
    };
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('my-archive.ZIP (2 items)')).toBeInTheDocument();
  });

  test('applies to the task-id fallback when archiveName is absent', () => {
    const task = {id: 123, status: 'SUCCESS', created: '2026-10-21T13:13:02.274Z'};
    render(<AsyncTasksList tasks={[task]} />);

    expect(screen.getByText('123.zip')).toBeInTheDocument();
  });
});

// The Status column's own filter dropdown - a custom antd Table column
// filter, copying client/src/components/runs/run-table/columns/
// column-run.js's StatusesRegionsFilterComponent (a Checkbox per value, with
// its own commit links; that precedent's pair is `OK`/`Clear`, where this
// column's is `OK`/`Reset to defaults` and the cases below say why).
// Its own open/pending-checkbox state is local UI mechanics;
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
  'clicking Reset to defaults calls onStatusesFilterChange with POLLED_STATUSES, ' +
  'never undefined and never an empty list',
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
    await click(screen.getByText('Reset to defaults'));

    expect(onStatusesFilterChange).toHaveBeenCalledWith(POLLED_STATUSES);
  }
);

// There is no `Clear` link on this column any more: it never cleared
// anything (it committed POLLED_STATUSES), and now that unticking every box
// genuinely commits an empty list, the two gestures are different and the
// old label would be wrong for both. Asserted explicitly so the rename
// cannot be half-reverted - a stale `Clear` alongside the new link would
// otherwise pass every other case in this file.
test('the Status filter offers no Clear link, only OK and Reset to defaults', async () => {
  renderWithLocale(<AsyncTasksList tasks={[]} />);

  await openStatusesFilter();

  expect(screen.getByText('OK')).toBeInTheDocument();
  expect(screen.getByText('Reset to defaults')).toBeInTheDocument();
  expect(screen.queryByText('Clear')).not.toBeInTheDocument();
});

// The dropdown's own select-all box.
//
// Its label is the action the next click performs - `Select all` unless
// every status is already ticked, where the next click unticks them and it
// reads `Unselect all` - so these cases find it by whichever of the two the
// state under test calls for. That label is also the box's accessible name,
// which is why `selectAllBox` below takes it as an argument rather than
// hard-coding one.
//
// Its `checked`/`indeterminate` pair is read off this component's own output
// - the props it hands antd's `Checkbox`, reached through the
// `statusesFilterDropdown` getter via a ref - the same way the suites above
// read `filteredValue` off `columns`, and for the same reason. antd 2.13
// renders `indeterminate` as a class on its own internal wrapper
// (`ant-checkbox-indeterminate`, see node_modules/antd/lib/checkbox/
// Checkbox.js) and sets no native `indeterminate` property on the `<input>`,
// so the DOM offers no platform-level hook for it: the only DOM-level
// assertion available would be on that antd class, which is exactly the
// antd-internals coupling rule 6 forbids. What the box *does* - select or
// clear every status - is driven and asserted through the real DOM below,
// where the per-status boxes give `toBeChecked` something real to read.
describe('the Status filter\'s select-all box', () => {
  const SELECT_ALL = 'Select all';
  const UNSELECT_ALL = 'Unselect all';

  function selectAllBox (name = SELECT_ALL) {
    return screen.getByRole('checkbox', {name});
  }

  // Walks the dropdown's own element tree for whichever of the two labels is
  // rendered, and hands back that Checkbox's props. By label rather than by
  // position, so adding anything else to the popover does not silently point
  // this at the wrong element.
  function selectAllProps (instance) {
    let found;
    const walk = (node) => {
      if (found || !node || typeof node !== 'object') {
        return;
      }
      if (Array.isArray(node)) {
        node.forEach(walk);
        return;
      }
      if (!node.props) {
        return;
      }
      if (node.props.children === SELECT_ALL || node.props.children === UNSELECT_ALL) {
        found = node.props;
        return;
      }
      walk(node.props.children);
    };
    walk(instance.statusesFilterDropdown);
    return found;
  }

  function renderAndOpen (props = {}) {
    let instance;
    renderWithLocale(
      <AsyncTasksList tasks={[]} {...props} ref={(r) => { instance = r; }} />
    );
    return openStatusesFilter().then(() => instance);
  }

  test('is indeterminate at the default, where six of the eight are ticked', async () => {
    const instance = await renderAndOpen();

    expect(selectAllProps(instance)).toMatchObject({
      checked: false,
      indeterminate: true,
      // A partial selection's next click selects everything, so the label
      // names that rather than the control.
      children: SELECT_ALL
    });
  });

  test('is ticked, and not indeterminate, when every status is already selected', async () => {
    const instance = await renderAndOpen({statusesFilter: ALL_ASYNC_TASK_STATUSES});

    expect(selectAllProps(instance)).toMatchObject({
      checked: true,
      indeterminate: false,
      // The one state whose next click unticks - and the only one that reads
      // `Unselect all`.
      children: UNSELECT_ALL
    });
  });

  test('is unticked, and not indeterminate, when nothing is selected', async () => {
    const instance = await renderAndOpen({statusesFilter: []});

    expect(selectAllProps(instance)).toMatchObject({
      checked: false,
      indeterminate: false,
      children: SELECT_ALL
    });
  });

  // The label is the only thing in the dropdown that says which way the next
  // click goes, so it has to follow the selection rather than be rendered
  // once. Driven through the real DOM: the previous cases assert the label
  // for a given committed filter, this one asserts it CHANGES as the user
  // ticks boxes without committing anything.
  test('its label follows the selection as the boxes are ticked', async () => {
    renderWithLocale(<AsyncTasksList tasks={[]} statusesFilter={[]} />);

    await openStatusesFilter();
    expect(screen.getByText(SELECT_ALL)).toBeInTheDocument();
    expect(screen.queryByText(UNSELECT_ALL)).not.toBeInTheDocument();

    await click(selectAllBox(SELECT_ALL));
    expect(screen.getByText(UNSELECT_ALL)).toBeInTheDocument();
    expect(screen.queryByText(SELECT_ALL)).not.toBeInTheDocument();

    // One status off the full set is a partial selection again, so it goes
    // back to offering "select all" - the half-ticked state.
    await click(screen.getByRole('checkbox', {name: 'EXPIRED'}));
    expect(screen.getByText(SELECT_ALL)).toBeInTheDocument();
    expect(screen.queryByText(UNSELECT_ALL)).not.toBeInTheDocument();
  });

  // A status the vocabulary does not know must not count towards "all eight
  // are ticked" - `selectedCount` is counted against ALL_ASYNC_TASK_STATUSES rather
  // than read off `pendingStatuses.length`, which a longer list would
  // otherwise satisfy by length alone.
  test('is not ticked when an unknown status pads the committed list out', async () => {
    const instance = await renderAndOpen({
      statusesFilter: [...ALL_ASYNC_TASK_STATUSES.slice(1), 'SOMETHING_ELSE']
    });

    expect(selectAllProps(instance)).toMatchObject({
      checked: false,
      indeterminate: true
    });
  });

  test('clicking it from the indeterminate default ticks every status', async () => {
    const onStatusesFilterChange = fn();
    renderWithLocale(
      <AsyncTasksList tasks={[]} onStatusesFilterChange={onStatusesFilterChange} />
    );

    await openStatusesFilter();
    await click(selectAllBox());

    // Every per-status box, not just the summary one - this is what the
    // gesture is actually for. antd reports an indeterminate checkbox's
    // `checked` as false, which is what makes one click mean "select all".
    ALL_ASYNC_TASK_STATUSES.forEach((status) => {
      expect(screen.getByRole('checkbox', {name: status})).toBeChecked();
    });

    await click(screen.getByText('OK'));

    const [committed] = onStatusesFilterChange.mock.calls[0];
    expect([...committed].sort()).toEqual([...ALL_ASYNC_TASK_STATUSES].sort());
  });

  test('clicking it again unticks every status, and OK commits an empty list', async () => {
    const onStatusesFilterChange = fn();
    renderWithLocale(
      <AsyncTasksList tasks={[]} onStatusesFilterChange={onStatusesFilterChange} />
    );

    await openStatusesFilter();
    // First click selects everything (the indeterminate default), the second
    // clears it - and the box is found by a different label each time,
    // because the label names whichever of the two is about to happen.
    await click(selectAllBox(SELECT_ALL));
    await click(selectAllBox(UNSELECT_ALL));

    ALL_ASYNC_TASK_STATUSES.forEach((status) => {
      expect(screen.getByRole('checkbox', {name: status})).not.toBeChecked();
    });

    await click(screen.getByText('OK'));

    expect(onStatusesFilterChange).toHaveBeenCalledWith([]);
  });

  // The divider the box is separated from the list by. `filterDivider` is
  // this module's own CSS class (identity-obj-proxy under jest), while
  // `cp-divider`/`horizontal` are the themable classes from
  // src/themes/styles/layout.less that draw the actual line - asserting on
  // both is what catches a divider that renders but follows no theme.
  test('is separated from the status list by a themable divider', async () => {
    renderWithLocale(<AsyncTasksList tasks={[]} />);

    await openStatusesFilter();

    // Through `document.body`, not the render's own `container`: antd's
    // filter dropdown renders its overlay into a portal of its own, outside
    // the container this component was mounted into.
    const divider = document.body.querySelector('.filterDivider');
    expect(divider).toBeInTheDocument();
    expect(divider).toHaveClass('cp-divider');
    expect(divider).toHaveClass('horizontal');

    // Position, not just presence: a divider rendered somewhere else in the
    // popover would satisfy the classes above while separating nothing.
    // `compareDocumentPosition` is the platform's own ordering check -
    // PRECEDING/FOLLOWING are relative to the node it is called on.
    const box = selectAllBox(SELECT_ALL);
    const firstStatus = screen.getByRole('checkbox', {name: 'PENDING'});
    /* eslint-disable no-bitwise */
    expect(divider.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_PRECEDING)
      .toBeTruthy();
    expect(divider.compareDocumentPosition(firstStatus) & Node.DOCUMENT_POSITION_FOLLOWING)
      .toBeTruthy();
    /* eslint-enable no-bitwise */
  });
});

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

  // The former behaviour was the opposite of this: an empty selection was
  // mapped back onto POLLED_STATUSES, on the reasoning that showing MORE
  // rows than before unticking is not the gesture. The decision changed - an
  // empty selection now goes to the server as `statuses: []`, so the request
  // says what the user actually chose. What the server then shows for an
  // empty list is its own business (`AsyncTaskSpecification.of` adds no
  // predicate for one), and `Reset to defaults` is the gesture that puts the
  // default back.
  test(
    'ticking everything off and pressing OK reports an empty list, not the default',
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

      expect(onStatusesFilterChange).toHaveBeenCalledWith([]);
    }
  );

  test('an empty selection reads as filtered, so the header icon lights up', () => {
    let instance;
    render(<AsyncTasksList tasks={[]} statusesFilter={[]} ref={(r) => { instance = r; }} />);

    expect(statusFilteredValue(instance)).toEqual(['filtered']);
  });

  // The guard in `handleStatusesFilterVisibleChange`. Committing used to be
  // safe to repeat because an empty `pendingStatuses` was mapped back to the
  // default; now it is not, so a second close must not fire at all - it
  // would commit the emptied pending list straight over the selection OK
  // just made. Driven through the instance's own handler for the same reason
  // the Owner column's commit-on-close case is: antd's outside-click
  // dismissal has no accessible hook this harness can reach.
  test('a second close after OK does not commit again over the selection', async () => {
    const onStatusesFilterChange = fn();
    let instance;
    renderWithLocale(
      <AsyncTasksList
        tasks={[]}
        statusesFilter={['PENDING']}
        onStatusesFilterChange={onStatusesFilterChange}
        ref={(r) => { instance = r; }}
      />
    );

    await openStatusesFilter();
    await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
    await click(screen.getByText('OK'));

    expect(onStatusesFilterChange).toHaveBeenCalledTimes(1);
    const [committed] = onStatusesFilterChange.mock.calls[0];
    expect([...committed].sort()).toEqual(['CANCELLED', 'PENDING']);

    instance.handleStatusesFilterVisibleChange(false);

    expect(onStatusesFilterChange).toHaveBeenCalledTimes(1);
  });
});

// Paging is antd's own `Pagination`, beneath the table, driven by `total`
// (the response's `totalCount` - every match, not this page) and `pageSize`.
// It renders nothing at all while one page holds everything, which is the
// "a single short page would be noise" decision the hand-rolled control
// before it also made.
//
// Its page numbers are `<li title="<n>">`; its arrows take their titles from
// antd's locale, which is the bundled zh-CN unless a `LocaleProvider` is in
// scope - so an arrow is only ever queried through `renderWithLocale`, never
// a bare `render`.
test('renders no pagination while every match fits on one page', () => {
  render(<AsyncTasksList tasks={[]} page={1} pageSize={15} total={15} />);

  expect(screen.queryByTitle('1')).not.toBeInTheDocument();
  expect(screen.queryByTitle('2')).not.toBeInTheDocument();
});

// The way to get stranded, if the control were hidden on the total alone:
// cancelling the last task on page 2 - or one expiring between two polls -
// takes the total back to a single page while page 2 is still on screen, and
// the server answers that page with nothing. The control has to stay, or
// there is no way back to page 1 short of reloading.
test('still renders pagination on a later page whose total has shrunk to one', () => {
  render(<AsyncTasksList tasks={[]} page={2} pageSize={15} total={15} />);

  expect(screen.getByTitle('1')).toBeInTheDocument();
});

test('clicking back from such a page asks for page 1', async () => {
  const onPageChange = fn();
  renderWithLocale(
    <AsyncTasksList tasks={[]} page={2} pageSize={15} total={15} onPageChange={onPageChange} />
  );

  await click(screen.getByTitle('Previous Page'));

  expect(onPageChange).toHaveBeenCalledWith(1, 15);
});

// `pageSize` has no sensible default, and rc-pagination divides by it
// (`calculatePage`), so a zero would render `Infinity` pages rather than
// nothing.
test('renders no pagination when no page size is given', () => {
  render(<AsyncTasksList tasks={[]} page={1} total={45} />);

  expect(screen.queryByTitle('1')).not.toBeInTheDocument();
  expect(screen.queryByTitle('Infinity')).not.toBeInTheDocument();
});

test('renders one page number per page of the total', () => {
  render(<AsyncTasksList tasks={[]} page={1} pageSize={15} total={31} />);

  // 31 matches over 15 per page: three pages, and no fourth.
  expect(screen.getByTitle('1')).toBeInTheDocument();
  expect(screen.getByTitle('3')).toBeInTheDocument();
  expect(screen.queryByTitle('4')).not.toBeInTheDocument();
});

test('clicking a page number asks the parent for that page', async () => {
  const onPageChange = fn();
  render(
    <AsyncTasksList tasks={[]} page={1} pageSize={15} total={45} onPageChange={onPageChange} />
  );

  await click(screen.getByTitle('3'));

  // 1-based, the way the API counts and the way DownloadsManager holds it.
  expect(onPageChange).toHaveBeenCalledWith(3, 15);
});

test('clicking Next asks for the page after the current one', async () => {
  const onPageChange = fn();
  renderWithLocale(
    <AsyncTasksList tasks={[]} page={2} pageSize={15} total={45} onPageChange={onPageChange} />
  );

  await click(screen.getByTitle('Next Page'));

  expect(onPageChange).toHaveBeenCalledWith(3, 15);
});

// Asserted as behaviour rather than through whatever antd 2.13 marks a
// disabled arrow with: there is no page before the first, so clicking back
// from it must ask for nothing at all.
test('clicking Previous on the first page asks for nothing', async () => {
  const onPageChange = fn();
  renderWithLocale(
    <AsyncTasksList tasks={[]} page={1} pageSize={15} total={45} onPageChange={onPageChange} />
  );

  await click(screen.getByTitle('Previous Page'));

  expect(onPageChange).not.toHaveBeenCalled();
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

// The item count, and its popover listing the archived paths, both live in
// the Archive column - there is no Items column of its own any more. The
// popover is built on rc-trigger (antd's Popover), so its content only mounts
// once hovered (client/test/AGENTS.md, "mouseenter/mouseleave do not reach a
// component") and `mouseEnterDelay={1}` defers that open by a second via
// setTimeout - hence the fake-timer dance below, rather than waiting on wall
// time.
test('shows the item count beside the archive name, singular for one', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(within(rowFor('archive-success')).getByText('archive-success.zip (2 items)'))
    .toBeInTheDocument();
  expect(within(rowFor('archive-running')).getByText('archive-running.zip (1 item)'))
    .toBeInTheDocument();
});

test('hovering the archive name shows every archived path', async () => {
  useFakeTimers();
  try {
    render(<AsyncTasksList tasks={[SUCCESS_TASK]} />);

    await hover(within(rowFor('archive-success')).getByText('archive-success.zip (2 items)'));
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
// array. Each renders the archive name alone - no count in brackets, and no
// Popover at all, since one that opens on hover and shows nothing would be
// worse than no affordance.
[
  {
    description: 'params is absent entirely',
    // No `params` means no archive name either, so the name falls back to the
    // task id - see `archiveNameOf`.
    task: {id: 20, status: 'SUCCESS', created: '2026-10-21T13:13:02.274Z'},
    displayedName: '20.zip'
  },
  {
    description: 'params carries no paths key',
    task: {
      id: 21,
      status: 'SUCCESS',
      params: {archiveName: 'archive-no-paths-key', storageId: 7},
      created: '2026-10-21T13:13:02.274Z'
    },
    displayedName: 'archive-no-paths-key.zip'
  },
  {
    description: 'paths is an empty array',
    task: {
      id: 22,
      status: 'SUCCESS',
      params: {archiveName: 'archive-empty-paths', storageId: 7, paths: []},
      created: '2026-10-21T13:13:02.274Z'
    },
    displayedName: 'archive-empty-paths.zip'
  }
].forEach(({description, task, displayedName}) => {
  test(`renders the name alone, with no count and no popover, when ${description}`, async () => {
    useFakeTimers();
    try {
      render(<AsyncTasksList tasks={[task]} />);

      const cell = screen.getByText(displayedName);
      expect(cell).toBeInTheDocument();

      await hover(cell);
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
// ("Filter menu") rather than antd's bundled zh-CN default.
//
// These cases stay to the commit paths - OK, Reset to defaults, clearing the
// field, and the dropdown's own visibility. The one that empties the field
// does reach into UserAutoComplete far enough to need `stubApi` (that
// component fires a `UserFind` on every search, the empty string included),
// which is unavoidable: emptying the field is now the Owner filter's only
// clearing gesture, so the wiring between the two components is this
// column's behaviour rather than a detail of the other one. Its
// search-as-you-type behaviour proper - typing a name and picking a
// candidate out of the results - is left to UserAutoComplete's own test
// file, which covers the `onTextChange` contract this column depends on.
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

  test(
    'opening the dropdown renders the autocomplete, OK and Reset to defaults, ' +
    'and no Clear link',
    async () => {
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
      expect(screen.getByText('Reset to defaults')).toBeInTheDocument();
      // No `Clear` link on either column any more. "Every user's tasks" is
      // asked for by emptying the input instead - the case below.
      expect(screen.queryByText('Clear')).not.toBeInTheDocument();
    }
  );

  // The field the user types in. `combobox` is the role antd puts on the
  // control's own wrapper, not on that field - the `textbox` inside it is
  // that, and the only one in this popover.
  function ownerInput () {
    return within(screen.getByRole('combobox')).getByRole('textbox');
  }

  const OWNER_HINT = 'Select a user from the list, or empty the field to show every user\'s tasks.';

  // Emptying the input by hand, which is now the only way to ask for every
  // user's tasks. It reaches this component through UserAutoComplete's own
  // `onTextChange` - added for this, because its `onChange` fires only from a
  // selection and so cannot report an emptied field. Typed through the real
  // combobox rather than by calling the handler: the wiring between the two
  // components is the part that would break silently.
  test('emptying the input by hand commits undefined, not the default owner', async () => {
    // UserAutoComplete issues a `UserFind` request from its own `onSearch`,
    // the empty string included - `stubApi` is what keeps that from tripping
    // the harness's network guard. Its answer is irrelevant here:
    // `onTextChange` fires before the lookup and regardless of what it
    // returns.
    stubApi();
    const onOwnerFilterChange = fn();
    renderList({
      isAdmin: true,
      ownerFilter: 'someone-else',
      defaultOwner: 'current-user',
      onOwnerFilterChange
    });

    await openOwnerFilter();
    // `clear` from `@test` fires the change event with an empty value, which
    // is what deleting the text produces; UserAutoComplete turns that into
    // `onTextChange('')`.
    await clear(ownerInput());
    // UserAutoComplete sets state when its own `UserFind` settles, and it
    // has no unmount guard - letting that land before this test ends keeps
    // the "setState on an unmounted component" warning out of the run. It is
    // not what the case is about: `onTextChange` has already fired by here.
    await flushPromises();
    await click(screen.getByText('OK'));

    expect(onOwnerFilterChange).toHaveBeenCalledWith(undefined);
  });

  // The gesture the clearing one above would otherwise turn into a trap:
  // empty the field, type a replacement name, and commit before the lookup
  // answers (or when it matches nothing). Committing there would send no
  // `owner` key and widen the table to EVERY user's tasks while the field
  // still reads like a name.
  describe('text typed but no candidate picked', () => {
    test('OK commits nothing at all', async () => {
      stubApi();
      const onOwnerFilterChange = fn();
      renderList({
        isAdmin: true,
        ownerFilter: 'someone-else',
        defaultOwner: 'current-user',
        onOwnerFilterChange
      });

      await openOwnerFilter();
      await clear(ownerInput());
      await change(ownerInput(), 'jdoe');
      await flushPromises();
      await click(screen.getByText('OK'));

      expect(onOwnerFilterChange).not.toHaveBeenCalled();
    });

    // Typing over a committed owner without emptying the field first - the
    // same state by a different route, and it must not commit that stale
    // owner back either.
    test('OK commits nothing when the text replaced a committed owner', async () => {
      stubApi();
      const onOwnerFilterChange = fn();
      renderList({
        isAdmin: true,
        ownerFilter: 'someone-else',
        defaultOwner: 'current-user',
        onOwnerFilterChange
      });

      await openOwnerFilter();
      await change(ownerInput(), 'jdoe');
      await flushPromises();
      await click(screen.getByText('OK'));

      expect(onOwnerFilterChange).not.toHaveBeenCalled();
    });

    // Same for the commit-on-close path: clicking away is not a commit
    // either, so the filter stays exactly as it was.
    test('closing the dropdown commits nothing', async () => {
      stubApi();
      const onOwnerFilterChange = fn();
      let instance;
      renderList({
        isAdmin: true,
        ownerFilter: 'someone-else',
        defaultOwner: 'current-user',
        onOwnerFilterChange,
        ref: (r) => { instance = r; }
      });

      await openOwnerFilter();
      await clear(ownerInput());
      await change(ownerInput(), 'jdoe');
      await flushPromises();
      instance.handleOwnerFilterVisibleChange(false);

      expect(onOwnerFilterChange).not.toHaveBeenCalled();
    });

    // Losing focus drops the typed text from the field (UserAutoComplete's
    // own `onBlur` falls back to rendering the `value` prop), so the block
    // has to lift with it - otherwise the field would read as a name OK
    // refuses to apply, with the hint still up and nothing explaining it.
    test('leaving the field unblocks it again, since the text goes with the focus', async () => {
      stubApi();
      const onOwnerFilterChange = fn();
      renderList({
        isAdmin: true,
        ownerFilter: 'someone-else',
        defaultOwner: 'current-user',
        onOwnerFilterChange
      });

      await openOwnerFilter();
      await change(ownerInput(), 'jdoe');
      await flushPromises();
      expect(screen.getByText(OWNER_HINT)).toBeInTheDocument();

      await blur(ownerInput());

      // rc-select 6 runs its blur handling on a 10ms timer, so this lands
      // after the event rather than with it.
      await waitFor(() => expect(screen.queryByText(OWNER_HINT)).not.toBeInTheDocument());
      await click(screen.getByText('OK'));
      expect(onOwnerFilterChange).toHaveBeenCalledWith('someone-else');
    });

    // OK and Enter doing nothing silently would read as broken, so the
    // dropdown says what it is waiting for.
    test('the dropdown explains why, and stops explaining once the field is empty', async () => {
      stubApi();
      renderList({isAdmin: true, ownerFilter: 'someone-else', defaultOwner: 'current-user'});

      await openOwnerFilter();
      expect(screen.queryByText(OWNER_HINT)).not.toBeInTheDocument();

      await change(ownerInput(), 'jdoe');
      await flushPromises();
      expect(screen.getByText(OWNER_HINT)).toBeInTheDocument();

      await clear(ownerInput());
      await flushPromises();
      expect(screen.queryByText(OWNER_HINT)).not.toBeInTheDocument();
    });
  });

  // "Reset to defaults" hands the decision to the parent rather than
  // resolving `defaultOwner` here: it owns what the default is, and what to
  // do while `/whoami` has not answered yet - see
  // downloads-manager.js's own `handleOwnerFilterResetToDefaults`.
  test('Reset to defaults asks the parent, and commits no owner itself', async () => {
    const onOwnerFilterChange = fn();
    const onOwnerFilterResetToDefaults = fn();
    renderList({
      isAdmin: true,
      ownerFilter: 'someone-else',
      defaultOwner: 'current-user',
      onOwnerFilterChange,
      onOwnerFilterResetToDefaults
    });

    await openOwnerFilter();
    await click(screen.getByText('Reset to defaults'));

    expect(onOwnerFilterResetToDefaults).toHaveBeenCalledTimes(1);
    expect(onOwnerFilterChange).not.toHaveBeenCalled();
  });

  // It also closes the dropdown, which is what stops the commit-on-close
  // path firing afterwards and overwriting what the parent has just set.
  // Asserted through that path rather than by reading `ownerFilterVisible`:
  // a second close must be a no-op, which is the behaviour the flag exists
  // for.
  test('Reset to defaults closes the dropdown, so a later close commits nothing', async () => {
    const onOwnerFilterChange = fn();
    let instance;
    renderList({
      isAdmin: true,
      ownerFilter: 'someone-else',
      defaultOwner: 'current-user',
      onOwnerFilterChange,
      onOwnerFilterResetToDefaults: fn(),
      ref: (r) => { instance = r; }
    });

    await openOwnerFilter();
    await click(screen.getByText('Reset to defaults'));
    instance.handleOwnerFilterVisibleChange(false);

    expect(onOwnerFilterChange).not.toHaveBeenCalled();
  });

  // The path OUT of the blocked state above, and the only way this filter can
  // be pointed at another user at all: type, then pick a candidate from the
  // list UserAutoComplete fetches. Covered here rather than in that
  // component's own file because what it proves is this column's own state -
  // that a picked candidate counts as the field's text, so OK commits
  // instead of refusing.
  test('picking a candidate from the list is what lets OK commit that user', async () => {
    const api = stubApi();
    // The `UserFind` request UserAutoComplete issues on every search
    // (models/user/UserFind.js) - matched loosely, since the prefix lands in
    // the query string.
    api.reply(/\/user\/find/, [{id: 42, userName: 'jdoe'}]);
    const onOwnerFilterChange = fn();
    renderList({
      isAdmin: true,
      ownerFilter: 'someone-else',
      defaultOwner: 'current-user',
      onOwnerFilterChange
    });

    await openOwnerFilter();
    await change(ownerInput(), 'jdo');
    // The candidate list is rendered from the lookup's answer, so it only
    // exists once that has landed.
    const candidate = await screen.findByText('jdoe');
    await click(candidate);
    await click(screen.getByText('OK'));

    expect(onOwnerFilterChange).toHaveBeenCalledWith('jdoe');
  });

  test('OK commits the already-selected owner as a string, never an array', async () => {
    const onOwnerFilterChange = fn();
    renderList({isAdmin: true, ownerFilter: 'someone-else', onOwnerFilterChange});

    await openOwnerFilter();
    await click(screen.getByText('OK'));

    expect(onOwnerFilterChange).toHaveBeenCalledWith('someone-else');
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
