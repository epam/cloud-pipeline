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
  renderWithStores,
  renderWithLocale,
  screen,
  within,
  click,
  waitFor,
  fn,
  stubApi,
  ok
} from '@test';
import DownloadsManager from './DownloadsManager';
import {POLLED_STATUSES} from '../../../models/asyncTask/statuses';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';
// Matches DownloadsManager's own PAGE_SIZE. Not imported: that constant is
// not part of the module's public surface.
const PAGE_SIZE = 15;

// A plain observable object is a sufficient store double (rule 10) — the page
// no longer reads `tasks` off the shared store, only the cap and the two
// actions, so the double carries exactly those and nothing else. No
// pausePolling/resumePolling here any more: nothing calls them.
function asyncTasksDouble (overrides = {}) {
  return observable(Object.assign({
    activeTasksLimitReached: false,
    cancelTask: fn().mockResolvedValue({}),
    consumeTask: fn().mockResolvedValue(undefined)
  }, overrides));
}

// `admin: true` by default: the Owner column (async-tasks-list.js) is
// admin-only, and most of this suite exercises it. `users` is NOT needed any
// more - the Owner column's dropdown embeds UserAutoComplete, which fetches
// its own candidates through UserFind rather than reading a preloaded list.
function authenticatedUserInfoDouble (admin = true) {
  return observable({
    loaded: true,
    value: {userName: USER_NAME, admin}
  });
}

// The page also @injects `dataStorages` (the shared singleton that loads
// every storage with one GET /datastorage/loadAll - see models/dataStorage/
// DataStorages.js) to resolve the Storage column's name. A plain double, per
// rule 10: `{loaded, value}` is all the getter reads.
function dataStoragesDouble (overrides = {}) {
  return observable(Object.assign({
    loaded: true,
    value: [{id: 7, name: 'my-storage'}]
  }, overrides));
}

function stores (overrides = {}) {
  return {
    asyncTasks: asyncTasksDouble(overrides),
    authenticatedUserInfo: authenticatedUserInfoDouble(),
    dataStorages: dataStoragesDouble()
  };
}

function stubFilter (api, tasks) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  api.reply(FILTER_ROUTE, tasks);
}

function filterCalls (api) {
  return api.calls.filter(c => c.path === FILTER_ROUTE);
}

function refreshButton () {
  return screen.getByRole('button', {name: /refresh/i});
}

// Neither the Status nor the Owner column (async-tasks-list.js) supplies a
// custom `filterIcon`, so each is antd's own default icon, found by its
// real English `title` ("Filter menu") - which only renders instead of
// antd's bundled zh-CN default once the tree sits inside a
// `<LocaleProvider>`, which `renderWithLocale` from `@test` provides (see
// the tests below that use it). Scoped to each column's own header cell,
// because both render the identical default icon under the same title when
// both are present (an admin user, the default here).
function statusFilterIcon () {
  return within(screen.getByText('Status').closest('th')).getByTitle('Filter menu');
}

function ownerFilterIcon () {
  return within(screen.getByText('Owner').closest('th')).getByTitle('Filter menu');
}

// A programmatic fixture, per the "build the 20-task fixture
// programmatically rather than by hand" instruction - a full page's worth of
// tasks to drive `hasNextPage`.
function buildTasks (count) {
  return Array.from({length: count}, (_, index) => ({
    id: index + 1,
    status: 'SUCCESS',
    params: {archiveName: `archive-${index + 1}`},
    created: '2026-10-21T13:00:00.000Z'
  }));
}

test(
  'fetches on mount with the owner seeded to the current user, statuses defaulted ' +
  'to POLLED_STATUSES, and page 1 / pageSize 15',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    const call = filterCalls(api)[0];
    expect(call.method).toBe('POST');
    const body = JSON.parse(call.body);
    expect(body.type).toBe('DOWNLOAD_ARCHIVE');
    expect(body.owner).toBe(USER_NAME);
    // The real, visible behaviour change this phase owns: the table hides
    // CANCELLED/EXPIRED by default, because the request now always carries
    // a concrete `statuses` list rather than omitting the key. Compared
    // order-independently against the imported constant, the same way
    // CurrentUserAsyncTasks.test.js pins its own (deliberately different)
    // use of it.
    expect([...body.statuses].sort()).toEqual([...POLLED_STATUSES].sort());
    expect(body.page).toBe(1);
    expect(body.pageSize).toBe(PAGE_SIZE);
  }
);

test('renders the heading and the rows from its own response', async () => {
  const api = stubApi();
  stubFilter(api, [
    {
      id: 1,
      status: 'SUCCESS',
      params: {archiveName: 'archive-one'},
      created: '2026-10-21T13:00:00.000Z'
    }
  ]);

  renderWithStores(<DownloadsManager />, stores());

  expect(screen.getByText('Downloads')).toBeInTheDocument();
  expect(await screen.findByText('archive-one.zip')).toBeInTheDocument();
});

test('renders a row for a CANCELLED task and for an EXPIRED task', async () => {
  const api = stubApi();
  stubFilter(api, [
    {
      id: 1,
      status: 'CANCELLED',
      params: {archiveName: 'archive-cancelled'},
      created: '2026-10-21T13:00:00.000Z'
    },
    {
      id: 2,
      status: 'EXPIRED',
      params: {archiveName: 'archive-expired'},
      created: '2026-10-21T13:00:00.000Z'
    }
  ]);

  renderWithStores(<DownloadsManager />, stores());

  expect(await screen.findByText('archive-cancelled.zip')).toBeInTheDocument();
  expect(screen.getByText('archive-expired.zip')).toBeInTheDocument();
});

test('resolves the Storage column to the dataStorages singleton\'s name', async () => {
  const api = stubApi();
  stubFilter(api, [
    {
      id: 1,
      status: 'SUCCESS',
      params: {archiveName: 'archive-one', storageId: 7},
      created: '2026-10-21T13:00:00.000Z'
    }
  ]);

  renderWithStores(<DownloadsManager />, stores());

  expect(await screen.findByText('my-storage')).toBeInTheDocument();
});

test(
  'falls back to the raw storage id, without throwing, while dataStorages has not loaded',
  async () => {
    const api = stubApi();
    stubFilter(api, [
      {
        id: 1,
        status: 'SUCCESS',
        params: {archiveName: 'archive-one', storageId: 7},
        created: '2026-10-21T13:00:00.000Z'
      }
    ]);

    renderWithStores(<DownloadsManager />, {
      asyncTasks: asyncTasksDouble(),
      authenticatedUserInfo: authenticatedUserInfoDouble(),
      dataStorages: dataStoragesDouble({loaded: false, value: []})
    });

    expect(await screen.findByText('archive-one.zip')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  }
);

test('shows the cap hint only when the injected store reports the limit reached', async () => {
  const api = stubApi();
  stubFilter(api, []);
  const first = renderWithStores(<DownloadsManager />, stores({activeTasksLimitReached: false}));
  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  expect(screen.queryByText(/limit of active archive downloads/i)).not.toBeInTheDocument();
  // Unmount before swapping the stub: the first instance's own async mount
  // fetch must settle against the stub it started with, not the next test's.
  first.unmount();

  const api2 = stubApi();
  stubFilter(api2, []);
  renderWithStores(<DownloadsManager />, stores({activeTasksLimitReached: true}));

  expect(await screen.findByText(/limit of active archive downloads/i)).toBeInTheDocument();
  // Lets a stray zero-delay timeout from Remote's lazy-fetch getters (read by
  // this second instance's own mount fetch) settle inside this test, rather
  // than firing after the suite has torn down.
  await new Promise(resolve => setTimeout(resolve, 0));
});

test(
  'committing the Status column filter re-issues the request with the fuller list',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {stores: stores()});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));

    // The Status column's own filter dropdown (async-tasks-list.js), a
    // custom antd Table column filter - not a bar-level control. CANCELLED
    // is ticked rather than an already-default status (e.g. RUNNING),
    // because the dropdown now opens with POLLED_STATUSES already checked -
    // ticking one of those would UNCHECK it instead.
    await click(statusFilterIcon());
    await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
    await click(screen.getByText('OK'));

    await waitFor(() => expect(filterCalls(api).length).toBe(2));
    const body = JSON.parse(filterCalls(api)[1].body);
    expect([...body.statuses].sort()).toEqual([...POLLED_STATUSES, 'CANCELLED'].sort());
  }
);

// Covers both the "default is the current user" and the "a cleared owner
// sends no `owner` key, and nothing re-seeds it" requirements, now through
// the Owner column's own dropdown (async-tasks-list.js) rather than the
// deleted filter bar. Clearing, not picking a different user, is what this
// drives: unlike the old bare `Select`, the dropdown's `Clear` link is
// reachable under test/AGENTS.md rule 6 (a bare antd Select's clear icon
// is not - see the equivalent test this one replaces, previously blocked by
// exactly that), and it reaches the same `setFilter`/`ownerSeeded` guard
// without needing to drive UserAutoComplete's own UserFind-backed search.
test(
  'the owner filter defaults to the current user, and clearing it sends no owner key, ' +
  'surviving a later Refresh with no automatic refetch in between',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {stores: stores()});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(JSON.parse(filterCalls(api)[0].body).owner).toBe(USER_NAME);

    await click(ownerFilterIcon());
    await click(screen.getByText('Clear'));

    await waitFor(() => expect(filterCalls(api).length).toBe(2));
    expect(JSON.parse(filterCalls(api)[1].body)).not.toHaveProperty('owner');

    // No poller is left to re-seed or to refetch anything on its own - a
    // short real wait is enough to prove nothing fires in the background,
    // the mirror of what used to be proven by ticking a fake clock past a
    // five-second poll interval.
    await expect(waitFor(() => expect(
      filterCalls(api).length
    ).toBe(3), {timeout: 200})).rejects.toThrow();
    expect(filterCalls(api).length).toBe(2);

    await click(refreshButton());

    await waitFor(() => expect(filterCalls(api).length).toBe(3));
    expect(JSON.parse(filterCalls(api)[2].body)).not.toHaveProperty('owner');
  }
);

// "Reset filters" - visible only once either column's own filter differs
// from its default, reusing exactly the comparisons the columns' own filter
// icon highlight is built from (DownloadsManager's `filtersChanged`):
// `statuses` against `POLLED_STATUSES` via `compareArrays`, `owner` against
// the seeded current user. Clicking it restores both in one request and
// re-fetches.
test(
  'Reset filters is hidden at the default, appears once a filter changes, and ' +
  'restores both to their defaults in one request',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {stores: stores()});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(screen.queryByRole('button', {name: /reset filters/i})).not.toBeInTheDocument();

    // Changes the Owner filter away from the seeded default. Scoped with
    // `within` from here on: antd's `Dropdown` does not unmount a popover's
    // content on close (only hides it), so once both the Owner and Status
    // dropdowns have been opened at least once, a bare `getByText('OK')`
    // matches both - one visible, one hidden - and throws for ambiguity.
    await click(ownerFilterIcon());
    await click(within(screen.getByRole('combobox').closest('.filterPopoverContainer'))
      .getByText('Clear'));
    await waitFor(() => expect(filterCalls(api).length).toBe(2));

    expect(screen.getByRole('button', {name: /reset filters/i})).toBeInTheDocument();

    // Also changes the Status filter, so both differ from their defaults.
    await click(statusFilterIcon());
    const cancelledCheckbox = screen.getByRole('checkbox', {name: 'CANCELLED'});
    await click(cancelledCheckbox);
    await click(within(cancelledCheckbox.closest('.filterPopoverContainer')).getByText('OK'));
    await waitFor(() => expect(filterCalls(api).length).toBe(3));

    await click(screen.getByRole('button', {name: /reset filters/i}));

    await waitFor(() => expect(filterCalls(api).length).toBe(4));
    const body = JSON.parse(filterCalls(api)[3].body);
    expect(body.owner).toBe(USER_NAME);
    expect([...body.statuses].sort()).toEqual([...POLLED_STATUSES].sort());
    expect(body.page).toBe(1);
    // The control itself disappears - there is nothing left to reset.
    expect(screen.queryByRole('button', {name: /reset filters/i})).not.toBeInTheDocument();
  }
);

test('cancelling a task calls the store and re-issues the page\'s own request', async () => {
  const api = stubApi();
  stubFilter(api, [
    {
      id: 5,
      status: 'RUNNING',
      params: {archiveName: 'archive-running'},
      created: '2026-10-21T13:00:00.000Z'
    }
  ]);
  const asyncTasks = asyncTasksDouble();

  renderWithStores(<DownloadsManager />, {
    asyncTasks,
    authenticatedUserInfo: authenticatedUserInfoDouble(),
    dataStorages: dataStoragesDouble()
  });

  const row = (await screen.findByText('archive-running.zip')).closest('tr');
  await click(within(row).getByRole('button', {name: /Cancel/i}));

  await waitFor(() => expect(asyncTasks.cancelTask).toHaveBeenCalledWith(5));
  await waitFor(() => expect(filterCalls(api).length).toBe(2));
});

test('disables row actions while a cancel is in flight, re-enabling once it settles', async () => {
  const api = stubApi();
  stubFilter(api, [
    {
      id: 6,
      status: 'RUNNING',
      params: {archiveName: 'archive-in-flight'},
      created: '2026-10-21T13:00:00.000Z'
    }
  ]);
  let resolveCancel;
  const asyncTasks = asyncTasksDouble({
    cancelTask: fn(() => new Promise((resolve) => {
      resolveCancel = resolve;
    }))
  });

  renderWithStores(<DownloadsManager />, {
    asyncTasks,
    authenticatedUserInfo: authenticatedUserInfoDouble(),
    dataStorages: dataStoragesDouble()
  });

  const findRow = () => screen.getByText('archive-in-flight.zip').closest('tr');
  await screen.findByText('archive-in-flight.zip');
  await click(within(findRow()).getByRole('button', {name: /Cancel/i}));

  await waitFor(() => {
    expect(within(findRow()).getByRole('button', {name: /Cancel/i})).toBeDisabled();
  });

  resolveCancel({});

  await waitFor(() => {
    expect(within(findRow()).getByRole('button', {name: /Cancel/i})).not.toBeDisabled();
  });
});

test('does not refetch on its own once mounted - there is no poller left', async () => {
  const api = stubApi();
  stubFilter(api, []);

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(filterCalls(api).length).toBe(1));

  // This replaces phase 15's test of PRESENCE ("keeps refreshing on its own
  // while mounted") with a test of ABSENCE: nothing is scheduled any more,
  // so a short real wait is enough to prove no second call ever arrives -
  // there is no fake clock to advance past, because there is no interval.
  await expect(waitFor(() => expect(
    filterCalls(api).length
  ).toBe(2), {timeout: 200})).rejects.toThrow();
  expect(filterCalls(api).length).toBe(1);
});

test('clicking Refresh re-issues the request with the same page and filters', async () => {
  const api = stubApi();
  stubFilter(api, []);

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  const firstBody = JSON.parse(filterCalls(api)[0].body);

  await click(refreshButton());

  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  expect(JSON.parse(filterCalls(api)[1].body)).toEqual(firstBody);
});

// The regression test for the live-update bug: `this.request` is a
// `RemotePost` (models/basic/RemotePost.js), whose `value` is a plain getter
// backed by a field that is never `@observable` — so without `fetchTick`, a
// fetch past the first one writes fresh data into `this.request` with zero
// re-render, and the row below sits on the FIRST response forever. Driven
// through two manual Refresh clicks rather than poll ticks: Refresh, not a
// background interval, is this phase's only repeat trigger, but the
// underlying reactivity gap - and so this guard - is unrelated to what
// triggers the fetch.
test(
  'live-updates the row when Refresh fetches a changed response, not just on the first load',
  async () => {
    const api = stubApi();
    api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
    let fetchCount = 0;
    // `api.reply`/re-registering the same pattern would not do: stubApi's
    // route table has no "last registration wins" rule (the first matching
    // registration always answers). `api.handle` gives one handler full
    // control to vary its own reply call by call.
    api.handle(FILTER_ROUTE, () => {
      fetchCount += 1;
      const status = fetchCount === 1 ? 'PENDING' : 'SUCCESS';
      return ok([
        {
          id: 1,
          status,
          params: {archiveName: 'archive-live'},
          created: '2026-10-21T13:00:00.000Z'
        }
      ]);
    });

    renderWithStores(<DownloadsManager />, stores());

    expect(await screen.findByText('PENDING')).toBeInTheDocument();

    await click(refreshButton());

    await waitFor(() => expect(screen.queryByText('PENDING')).not.toBeInTheDocument());
    expect(screen.getByText('SUCCESS')).toBeInTheDocument();
  }
);

// There is no longer a "first load only" gate on `pending` (removed along
// with `hasLoadedOnce`, which existed only to serve it) - once polling was
// gone, so was its reason, and the table's loading state now shows for
// every fetch: a manual Refresh, a filter change, or a page change, not
// just the first one. Reads `this.request.pending` directly through
// `wrappedInstance`, the same "no accessible DOM hook" case the regression
// test above used to read `hasLoadedOnce` from - antd's own `Spin` carries
// no ARIA marker this harness could query instead (rule 6).
test('shows the pending state on a manual Refresh, not just the first load', async () => {
  const api = stubApi();
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  let resolveSecond;
  let callCount = 0;
  api.handle(FILTER_ROUTE, () => {
    callCount += 1;
    if (callCount === 1) {
      return ok([]);
    }
    return new Promise((resolve) => {
      resolveSecond = resolve;
    });
  });

  let instance;
  renderWithStores(
    <DownloadsManager ref={(r) => { instance = r; }} />,
    stores()
  );

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  expect(instance.wrappedInstance.request.pending).toBe(false);

  await click(refreshButton());
  // `loadTasks` awaits `whoAmI.fetchIfNeededOrWait()` before `request.send`
  // ever reaches `global.fetch`, so the actual network call - and so
  // `resolveSecond` being assigned - lands a few microtasks after `click`'s
  // own single `await settle()`. Waiting for the call count confirms
  // `resolveSecond` is already assigned before using it.
  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  expect(instance.wrappedInstance.request.pending).toBe(true);

  resolveSecond(ok([]));
  await waitFor(() => expect(instance.wrappedInstance.request.pending).toBe(false));
});

// `hasNextPage` is derived from the current response's length, not rendered
// unconditionally - async-tasks-list.js only shows the pagination row at all
// when `page > 1 || hasNextPage`, so a short first page renders no row to
// query a "disabled" state from. A full page is where there IS a button to
// assert on; a short one is proven by the row being absent altogether, which
// is the other half of the same derivation.
test('a full page renders an enabled Next button', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(PAGE_SIZE));

  renderWithStores(<DownloadsManager />, stores());

  expect(await screen.findByRole('button', {name: /next page/i})).not.toBeDisabled();
});

test('a page shorter than the page size renders no pagination row at all', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(3));

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  await screen.findByText('archive-1.zip');
  expect(screen.queryByRole('button', {name: /next page/i})).not.toBeInTheDocument();
});

test('clicking Next re-issues the request with page: 2', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(PAGE_SIZE));

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(filterCalls(api).length).toBe(1));

  await click(await screen.findByRole('button', {name: /next page/i}));

  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  const body = JSON.parse(filterCalls(api)[1].body);
  expect(body.page).toBe(2);
});

test(
  'changing a filter resets the body\'s page to 1, even after paging forward',
  async () => {
    const api = stubApi();
    stubFilter(api, buildTasks(PAGE_SIZE));

    renderWithLocale(<DownloadsManager />, {stores: stores()});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));

    await click(await screen.findByRole('button', {name: /next page/i}));
    await waitFor(() => expect(filterCalls(api).length).toBe(2));
    expect(JSON.parse(filterCalls(api)[1].body).page).toBe(2);

    // Commits the Status column's own filter dropdown - the other source
    // that funnels through DownloadsManager's `setFilter`, next to the
    // Owner column. CANCELLED, not an already-default status - see the
    // comment on the equivalent test above.
    await click(statusFilterIcon());
    await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
    await click(screen.getByText('OK'));

    await waitFor(() => expect(filterCalls(api).length).toBe(3));
    const body = JSON.parse(filterCalls(api)[2].body);
    expect(body.page).toBe(1);
  }
);

// The regression phase 12 exists to prevent: with `taskSorter` gone, rows
// must render in exactly the order the server sent them, newest-first or
// not - asserting the timestamps merely descend would still pass if the
// client silently re-sorted, since this fixture already happens to be
// oldest-first from the server.
test('renders rows in exactly the order the server returned them', async () => {
  const api = stubApi();
  stubFilter(api, [
    {
      id: 1,
      status: 'SUCCESS',
      params: {archiveName: 'archive-oldest'},
      created: '2026-10-19T10:00:00.000Z'
    },
    {
      id: 2,
      status: 'SUCCESS',
      params: {archiveName: 'archive-middle'},
      created: '2026-10-20T10:00:00.000Z'
    },
    {
      id: 3,
      status: 'SUCCESS',
      params: {archiveName: 'archive-newest'},
      created: '2026-10-21T10:00:00.000Z'
    }
  ]);

  renderWithStores(<DownloadsManager />, stores());

  await screen.findByText('archive-oldest.zip');
  const names = screen.getAllByText(/^archive-(oldest|middle|newest)\.zip$/)
    .map((element) => element.textContent);
  expect(names).toEqual(['archive-oldest.zip', 'archive-middle.zip', 'archive-newest.zip']);
});

test(
  'the Owner column is absent for a non-admin user, and no owner key is ever special',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {
      stores: {
        asyncTasks: asyncTasksDouble(),
        authenticatedUserInfo: authenticatedUserInfoDouble(false),
        dataStorages: dataStoragesDouble()
      }
    });

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(screen.queryByText('Owner')).not.toBeInTheDocument();
    // Only the Status column's own default filter icon remains - no second
    // one for an Owner column that was never rendered at all.
    expect(screen.getAllByTitle('Filter menu')).toHaveLength(1);
    // The seeded owner is still sent - the column controls visibility only,
    // never the request itself.
    expect(JSON.parse(filterCalls(api)[0].body).owner).toBe(USER_NAME);
  }
);
