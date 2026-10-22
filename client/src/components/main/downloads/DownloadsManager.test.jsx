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
  screen,
  within,
  click,
  waitFor,
  fn,
  stubApi,
  ok,
  useFakeTimers,
  useRealTimers
} from '@test';
import DownloadsManager from './DownloadsManager';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';
// Mirrors DownloadsManager's own POLL_INTERVAL_MS. Not imported: that constant
// is not part of the module's public surface, and a mismatch here would just
// make this test fail loudly rather than silently poll at the wrong cadence.
const POLL_INTERVAL_MS = 5000;

// A plain observable object is a sufficient store double (rule 10) — the page
// no longer reads `tasks` off the shared store, only the cap and the two
// actions, so the double carries exactly those and nothing else.
function asyncTasksDouble (overrides = {}) {
  return observable(Object.assign({
    activeTasksLimitReached: false,
    cancelTask: fn().mockResolvedValue({}),
    consumeTask: fn().mockResolvedValue(undefined)
  }, overrides));
}

// The page now renders <AsyncTasksFilters> internally, which @injects these
// two stores (admin controls the Owner control's visibility); every render
// of <DownloadsManager /> needs both, or mobx-react's inject throws "Store
// '...' is not available!".
function authenticatedUserInfoDouble (admin = true) {
  return observable({
    loaded: true,
    value: {userName: USER_NAME, admin}
  });
}

function usersDouble () {
  return observable({
    loaded: true,
    value: [
      {id: 1, userName: USER_NAME},
      {id: 2, userName: 'someone-else'}
    ]
  });
}

// The page now also @injects `dataStorages` (the shared singleton that loads
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
    users: usersDouble(),
    dataStorages: dataStoragesDouble()
  };
}

function stubFilter (api, tasks) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  api.reply(FILTER_ROUTE, tasks);
}

// Finds the filter bar's labelled row by its rendered "Label:" text - the
// same shape async-tasks-filters.test.jsx uses for the component in
// isolation.
function filterRow (label) {
  return screen.getByText(`${label}:`).closest('div');
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
  'fetches on mount with the owner seeded to the current user, no statuses, ' +
  'and page 1 / pageSize 20',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(api.calls.some(c => c.path === FILTER_ROUTE)).toBe(true));
    const call = api.calls.find(c => c.path === FILTER_ROUTE);
    expect(call.method).toBe('POST');
    const body = JSON.parse(call.body);
    expect(body.type).toBe('DOWNLOAD_ARCHIVE');
    // The default owner now comes from the filter bar's state (seeded to the
    // current user on the first load), not a bare whoAmI fallback - see
    // DownloadsManager's loadTasks. Still no statuses: nothing is selected
    // in the filter bar by default.
    expect(body.owner).toBe(USER_NAME);
    expect(body).not.toHaveProperty('statuses');
    // This is a deliberate behaviour change (phase 12), not a weakened test:
    // the page's own request now always pages, unlike the background poll in
    // CurrentUserAsyncTasks.js, which must still send neither key.
    expect(body.page).toBe(1);
    expect(body.pageSize).toBe(20);
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
  expect(await screen.findByText('archive-one')).toBeInTheDocument();
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

  expect(await screen.findByText('archive-cancelled')).toBeInTheDocument();
  expect(screen.getByText('archive-expired')).toBeInTheDocument();
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
      users: usersDouble(),
      dataStorages: dataStoragesDouble({loaded: false, value: []})
    });

    expect(await screen.findByText('archive-one')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  }
);

test('shows the cap hint only when the injected store reports the limit reached', async () => {
  const api = stubApi();
  stubFilter(api, []);
  const first = renderWithStores(<DownloadsManager />, stores({activeTasksLimitReached: false}));
  await waitFor(() => expect(api.calls.some(c => c.path === FILTER_ROUTE)).toBe(true));
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
  'committing the Status column filter re-issues the request with that filter in the body',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(1));

    // The Status column's own filter dropdown (async-tasks-list.js), a
    // custom antd Table column filter - not a bar-level control. Found by
    // its own `aria-label`, not antd's locale-dependent `title` (see
    // async-tasks-list.js's `filterIcon` and async-tasks-list.test.jsx's
    // comment on `openStatusesFilter`).
    await click(screen.getByLabelText('Filter by status'));
    await click(screen.getByRole('checkbox', {name: 'RUNNING'}));
    await click(screen.getByText('OK'));

    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(2));
    const call = api.calls.filter(c => c.path === FILTER_ROUTE)[1];
    const body = JSON.parse(call.body);
    expect(body.statuses).toEqual(['RUNNING']);
  }
);

// Covers the regression the plan this phase follows calls out as "the one
// most likely to regress into re-seeding on the next poll": once the user
// has deliberately chosen an owner, a later background tick must not
// silently overwrite it back to the seeded default. Driven through picking
// a DIFFERENT owner rather than clearing the filter back to "every user":
// rc-select 6.9.8's (the version antd 2.13.14 pins) single-select clear icon
// renders with no text, role, label or title - verified against the
// installed package - so it cannot be reached under test/AGENTS.md rule 6.
//
// This proves the guard holds once `filters.owner` is non-empty; it does
// NOT exercise the narrower race `setFilter`'s own comment documents - an
// admin clearing the owner filter back to undefined while the very first
// `loadTasks` call is still awaiting `whoAmI`. That path is covered by
// marking `this.ownerSeeded` inside `setFilter` itself rather than by a test
// here, for the same reason: there is no accessible way to drive the actual
// "clear" interaction from this suite either.
test(
  'picking a different owner persists across a poll tick, never reseeded back',
  async () => {
    useFakeTimers();
    try {
      const api = stubApi();
      stubFilter(api, []);

      renderWithStores(<DownloadsManager />, stores());

      await waitFor(() => expect(
        api.calls.filter(c => c.path === FILTER_ROUTE).length
      ).toBe(1));
      const firstBody = JSON.parse(
        api.calls.filter(c => c.path === FILTER_ROUTE)[0].body
      );
      expect(firstBody.owner).toBe(USER_NAME);

      await click(within(filterRow('Owner')).getByRole('combobox'));
      await click(screen.getByRole('menuitem', {name: 'someone-else'}));

      await waitFor(() => expect(
        api.calls.filter(c => c.path === FILTER_ROUTE).length
      ).toBe(2));

      // Ticks past one background poll interval. If the seeding guard were
      // keyed on "the first call" rather than "has a userName ever been
      // seeded", this would silently overwrite the chosen owner back to
      // USER_NAME on this very tick.
      await waitFor(() => expect(
        api.calls.filter(c => c.path === FILTER_ROUTE).length
      ).toBe(3), {timeout: POLL_INTERVAL_MS * 2});

      const calls = api.calls.filter(c => c.path === FILTER_ROUTE);
      const lastBody = JSON.parse(calls[calls.length - 1].body);
      expect(lastBody.owner).toBe('someone-else');
    } finally {
      useRealTimers();
    }
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
    users: usersDouble(),
    dataStorages: dataStoragesDouble()
  });

  const row = (await screen.findByText('archive-running')).closest('tr');
  await click(within(row).getByRole('button', {name: /Cancel/i}));

  await waitFor(() => expect(asyncTasks.cancelTask).toHaveBeenCalledWith(5));
  await waitFor(() => expect(
    api.calls.filter(c => c.path === FILTER_ROUTE).length
  ).toBe(2));
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
    users: usersDouble(),
    dataStorages: dataStoragesDouble()
  });

  const findRow = () => screen.getByText('archive-in-flight').closest('tr');
  await screen.findByText('archive-in-flight');
  await click(within(findRow()).getByRole('button', {name: /Cancel/i}));

  await waitFor(() => {
    expect(within(findRow()).getByRole('button', {name: /Cancel/i})).toBeDisabled();
  });

  resolveCancel({});

  await waitFor(() => {
    expect(within(findRow()).getByRole('button', {name: /Cancel/i})).not.toBeDisabled();
  });
});

test('keeps refreshing on its own while mounted, and stops once unmounted', async () => {
  useFakeTimers();
  try {
    const api = stubApi();
    stubFilter(api, []);

    const utils = renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(1));

    // Nothing remounted it, and nothing clicked cancel: the row count going up
    // on its own is the poll this test exists to prove. `waitFor` advances
    // the fake clock itself (dom-testing-library detects it), in its default
    // 50ms steps, so a generous `timeout` is what lets it reach a 5-second
    // poll interval — it costs nothing in real wall-clock time.
    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(2), {timeout: POLL_INTERVAL_MS * 2});

    utils.unmount();
    // Stopped, not merely slow: a bare `advanceTimers` here proves nothing by
    // itself, because nothing then drains the microtasks a still-running poll
    // would need to actually record a third call — the assertion right after
    // it would pass whether or not componentWillUnmount stopped the poller.
    // Asserting through a `waitFor` that is expected to time out does drain
    // them (it is what the earlier `waitFor` calls above use to observe a
    // poll that DOES happen), so it is what actually exercises the stop.
    await expect(waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(3), {timeout: POLL_INTERVAL_MS * 2})).rejects.toThrow();

    expect(api.calls.filter(c => c.path === FILTER_ROUTE).length).toBe(2);
  } finally {
    useRealTimers();
  }
});

// The regression test for the live-update bug: `this.request` is a
// `RemotePost` (models/basic/RemotePost.js), whose `value` is a plain getter
// backed by a field that is never `@observable` — so before this phase's
// `pollTick` fix, a poll past the first one wrote fresh data into
// `this.request` with zero re-render, and the row below sat on the FIRST
// response forever. A test that only checked the first response would pass
// even with that bug, so this one drives TWO sequential poll ticks with two
// DIFFERENT responses and asserts the SECOND one is what ends up on screen.
// Do not "simplify" this into a single-load assertion — that would silently
// remove the regression guard.
test(
  'live-updates the row across a second poll tick, not just the first load',
  async () => {
    useFakeTimers();
    try {
      const api = stubApi();
      api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
      let pollCount = 0;
      // `api.reply`/re-registering the same pattern would not do: stubApi's
      // route table has no "last registration wins" rule (the first matching
      // registration always answers). `api.handle` gives one handler full
      // control to vary its own reply call by call.
      api.handle(FILTER_ROUTE, () => {
        pollCount += 1;
        const status = pollCount === 1 ? 'PENDING' : 'SUCCESS';
        return ok([
          {
            id: 1,
            status,
            params: {archiveName: 'archive-live'},
            created: '2026-10-21T13:00:00.000Z'
          }
        ]);
      });

      let instance;
      renderWithStores(
        <DownloadsManager ref={(r) => { instance = r; }} />,
        stores()
      );

      expect(await screen.findByText('PENDING')).toBeInTheDocument();
      // The first-load spinner flips `hasLoadedOnce` once and must stay true
      // from here on - the phase-9 fix this change must not regress. There is
      // no accessible DOM hook for antd's own loading overlay (rule 6 in
      // client/test/AGENTS.md forbids querying its internals by class), so
      // this reads the component's own state directly through
      // `wrappedInstance`, the handle mobx-react's `inject` HOC exposes for
      // exactly this - not an antd internal.
      expect(instance.wrappedInstance.state.hasLoadedOnce).toBe(true);

      // Ticks past one background poll interval, onto the second, different
      // response.
      await waitFor(() => expect(
        api.calls.filter(c => c.path === FILTER_ROUTE).length
      ).toBe(2), {timeout: POLL_INTERVAL_MS * 2});

      await waitFor(() => expect(screen.queryByText('PENDING')).not.toBeInTheDocument());
      expect(screen.getByText('SUCCESS')).toBeInTheDocument();
      // Still gated to the first load only - never reappears on a later poll.
      expect(instance.wrappedInstance.state.hasLoadedOnce).toBe(true);
    } finally {
      useRealTimers();
    }
  }
);

test('pauses the shared background poll on mount, resumes it on unmount', async () => {
  const api = stubApi();
  stubFilter(api, []);
  const pausePolling = fn();
  const resumePolling = fn();

  const utils = renderWithStores(
    <DownloadsManager />,
    stores({pausePolling, resumePolling})
  );

  await waitFor(() => expect(api.calls.some(c => c.path === FILTER_ROUTE)).toBe(true));
  expect(pausePolling).toHaveBeenCalledTimes(1);
  expect(resumePolling).not.toHaveBeenCalled();

  utils.unmount();

  expect(resumePolling).toHaveBeenCalledTimes(1);
  // Only mount pauses, only unmount resumes - neither fires a second time.
  expect(pausePolling).toHaveBeenCalledTimes(1);
});

// Proves the guards in componentDidMount/componentWillUnmount: every other
// test in this file already renders and unmounts against a double built by
// `asyncTasksDouble()`, which (by design, see that function) carries neither
// method - so this one is explicit about what that absence is proving, rather
// than relying on it as a side effect.
test(
  'mounts and unmounts without throwing when asyncTasks has neither pause nor resume method',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    const utils = renderWithStores(<DownloadsManager />, stores());
    await waitFor(() => expect(api.calls.some(c => c.path === FILTER_ROUTE)).toBe(true));

    expect(() => utils.unmount()).not.toThrow();
  }
);

// `hasNextPage` is derived from the current response's length, not rendered
// unconditionally - async-tasks-list.js only shows the pagination row at all
// when `page > 1 || hasNextPage`, so a short first page renders no row to
// query a "disabled" state from. A full page is where there IS a button to
// assert on; a short one is proven by the row being absent altogether, which
// is the other half of the same derivation.
test('a full page renders an enabled Next button', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(20));

  renderWithStores(<DownloadsManager />, stores());

  expect(await screen.findByRole('button', {name: /next page/i})).not.toBeDisabled();
});

test('a page shorter than the page size renders no pagination row at all', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(3));

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(api.calls.some(c => c.path === FILTER_ROUTE)).toBe(true));
  await screen.findByText('archive-1');
  expect(screen.queryByRole('button', {name: /next page/i})).not.toBeInTheDocument();
});

test('clicking Next re-issues the request with page: 2', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(20));

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(
    api.calls.filter(c => c.path === FILTER_ROUTE).length
  ).toBe(1));

  await click(await screen.findByRole('button', {name: /next page/i}));

  await waitFor(() => expect(
    api.calls.filter(c => c.path === FILTER_ROUTE).length
  ).toBe(2));
  const body = JSON.parse(api.calls.filter(c => c.path === FILTER_ROUTE)[1].body);
  expect(body.page).toBe(2);
});

test(
  'changing a filter resets the body\'s page to 1, even after paging forward',
  async () => {
    const api = stubApi();
    stubFilter(api, buildTasks(20));

    renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(1));

    await click(await screen.findByRole('button', {name: /next page/i}));
    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(2));
    expect(
      JSON.parse(api.calls.filter(c => c.path === FILTER_ROUTE)[1].body).page
    ).toBe(2);

    // Commits the Status column's own filter dropdown - the other source
    // that funnels through DownloadsManager's `setFilter`, next to the owner
    // Select.
    await click(screen.getByLabelText('Filter by status'));
    await click(screen.getByRole('checkbox', {name: 'RUNNING'}));
    await click(screen.getByText('OK'));

    await waitFor(() => expect(
      api.calls.filter(c => c.path === FILTER_ROUTE).length
    ).toBe(3));
    const body = JSON.parse(api.calls.filter(c => c.path === FILTER_ROUTE)[2].body);
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

  await screen.findByText('archive-oldest');
  const names = screen.getAllByText(/^archive-(oldest|middle|newest)$/)
    .map((element) => element.textContent);
  expect(names).toEqual(['archive-oldest', 'archive-middle', 'archive-newest']);
});
