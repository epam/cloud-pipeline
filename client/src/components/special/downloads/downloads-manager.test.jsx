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
  ok,
  fail,
  jsonResponse,
  useFakeTimers,
  useRealTimers
} from '@test';
import DownloadsManager from './downloads-manager';
import {POLLED_STATUSES} from '../../../models/asyncTask/statuses';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';
// Matches DownloadsManager's own PAGE_SIZE. Not imported: that constant is
// not part of the module's public surface.
const PAGE_SIZE = 10;
// Mirrors DownloadsManager's own POLL_INTERVAL_MS, for the same reason.
const POLL_INTERVAL_MS = 5000;

// A plain observable object is a sufficient store double (rule 10) — the page
// no longer reads `tasks` off the shared store, only the cap and the two
// actions, so the double carries exactly those and nothing else.
// `pausePolling`/`resumePolling` are deliberately absent from the default
// double: the page calls both on mount/unmount through a `typeof` guard, so
// every test in this file that does not pass them is also proving that guard
// holds (one test below says so explicitly).
function asyncTasksDouble (overrides = {}) {
  return observable(Object.assign({
    cancelTask: fn().mockResolvedValue({}),
    consumeTask: fn().mockResolvedValue(undefined)
  }, overrides));
}

// `admin: true` by default: the Owner column (async-tasks-list.js) is
// admin-only, and most of this suite exercises it. `users` is NOT needed any
// more - the Owner column's dropdown embeds UserAutoComplete, which fetches
// its own candidates through UserFind rather than reading a preloaded list.
function authenticatedUserInfoDouble (admin = true, roles) {
  const value = {userName: USER_NAME, admin};
  if (roles) {
    value.roles = roles;
  }
  return observable({
    loaded: true,
    value
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

// `POST /async-task/filter` answers `{elements, totalCount}`, the total being
// how many tasks the FILTER matched rather than how many this page carries -
// which is what drives the pagination control. `total` defaults to the number
// of rows given, i.e. "this is the only page".
function stubFilter (api, tasks, total = (tasks || []).length) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  api.reply(FILTER_ROUTE, {elements: tasks, totalCount: total});
}

function filterCalls (api) {
  return api.calls.filter(c => c.path === FILTER_ROUTE);
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
// tasks to drive the pagination control.
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
  'to POLLED_STATUSES, and page 1 / pageSize 10',
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

// No heading assertion any more: the class itself carries no title - the
// Modal shell's own `title` prop is what shows "Downloads" now, covered in
// downloads-modal.test.jsx.
test('renders the rows from its own response', async () => {
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

// The cap hint this component used to render is gone, and so is the store
// member it read: nothing counts the user's active tasks in the background
// any more, and nothing can be queued from this modal for a hint to warn
// about. The cap is counted against the server where an archive is actually
// asked for. The double below deliberately still offers the old member, so
// this stays a guard against someone reviving the hint from it.
test('renders no cap hint, even when the injected store reports the limit reached', async () => {
  const api = stubApi();
  stubFilter(api, []);

  renderWithStores(<DownloadsManager />, stores({activeTasksLimitReached: true}));

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  expect(screen.queryByText(/limit of active archive downloads/i)).not.toBeInTheDocument();
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
  'surviving both a later poll tick and a later filter commit',
  async () => {
    useFakeTimers();
    try {
      const api = stubApi();
      stubFilter(api, []);

      renderWithLocale(<DownloadsManager />, {stores: stores()});

      await waitFor(() => expect(filterCalls(api).length).toBe(1));
      expect(JSON.parse(filterCalls(api)[0].body).owner).toBe(USER_NAME);

      await click(ownerFilterIcon());
      await click(screen.getByText('Clear'));

      await waitFor(() => expect(filterCalls(api).length).toBe(2));
      expect(JSON.parse(filterCalls(api)[1].body)).not.toHaveProperty('owner');

      // The `ownerSeeded` guard, now that a poll tick is what exercises it:
      // a cleared owner must stay cleared on every later fetch, and the poll
      // is the one that fires without the user doing anything. `waitFor`
      // drives the fake clock itself in 50ms steps, so a generous `timeout`
      // is what lets it reach the interval at no wall-clock cost.
      await waitFor(() => expect(filterCalls(api).length).toBe(3), {
        timeout: POLL_INTERVAL_MS * 2
      });
      expect(JSON.parse(filterCalls(api)[2].body)).not.toHaveProperty('owner');

      // Committed at the instant the tick above is still in flight
      // (`waitFor` returns as soon as the call is *recorded*, before its
      // response lands) - the window `RemotePost.send`'s in-flight no-op
      // used to swallow a user action in, hence `refetch`'s wait. Do not
      // "stabilise" this by settling the tick first: that would remove the
      // only guard against that regression. Scoped with `within`, since the
      // Owner dropdown opened above leaves its own OK link in the DOM
      // (antd's Dropdown hides, not unmounts, a closed popover).
      await click(statusFilterIcon());
      const cancelledCheckbox = screen.getByRole('checkbox', {name: 'CANCELLED'});
      await click(cancelledCheckbox);
      await click(within(cancelledCheckbox.closest('.filterPopoverContainer')).getByText('OK'));

      await waitFor(() => expect(filterCalls(api).length).toBe(4));
      expect(JSON.parse(filterCalls(api)[3].body)).not.toHaveProperty('owner');
    } finally {
      useRealTimers();
    }
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

    const utils = renderWithLocale(<DownloadsManager />, {stores: stores()});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(screen.queryByRole('button', {name: /reset filters/i})).not.toBeInTheDocument();
    // The heading row itself, not just the button it holds: at the default,
    // rendering an empty row (with its own margin) would be a visible
    // regression the button's own absence does not catch. `headingRow` is
    // this module's own CSS class (identity-obj-proxy), not antd's - rule 6
    // only forbids querying antd's internals.
    expect(utils.container.querySelector('.headingRow')).not.toBeInTheDocument();

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
    expect(utils.container.querySelector('.headingRow')).toBeInTheDocument();

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
    expect(utils.container.querySelector('.headingRow')).not.toBeInTheDocument();
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

test('keeps refreshing on its own while mounted, and stops once unmounted', async () => {
  useFakeTimers();
  try {
    const api = stubApi();
    stubFilter(api, []);

    const utils = renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(filterCalls(api).length).toBe(1));

    // Nothing remounted it and nothing clicked anything: the call count going
    // up on its own is the poll this test exists to prove. `waitFor` advances
    // the fake clock itself (dom-testing-library detects it), in its default
    // 50ms steps, so a generous `timeout` is what lets it reach a 5-second
    // poll interval — it costs nothing in real wall-clock time.
    await waitFor(() => expect(filterCalls(api).length).toBe(2), {
      timeout: POLL_INTERVAL_MS * 2
    });

    utils.unmount();
    // Stopped, not merely slow: a bare `advanceTimers` here proves nothing by
    // itself, because nothing then drains the microtasks a still-running poll
    // would need to actually record a third call — the assertion right after
    // it would pass whether or not componentWillUnmount stopped the poller.
    // Asserting through a `waitFor` that is expected to time out does drain
    // them (it is what the earlier `waitFor` calls above use to observe a
    // poll that DOES happen), so it is what actually exercises the stop.
    await expect(waitFor(() => expect(
      filterCalls(api).length
    ).toBe(3), {timeout: POLL_INTERVAL_MS * 2})).rejects.toThrow();

    expect(filterCalls(api).length).toBe(2);
  } finally {
    useRealTimers();
  }
});

test('pauses the shared background poll on mount, resumes it on unmount', async () => {
  const api = stubApi();
  stubFilter(api, []);
  const pausePolling = fn();
  const resumePolling = fn();

  const utils = renderWithStores(
    <DownloadsManager />,
    stores({pausePolling, resumePolling})
  );

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  expect(pausePolling).toHaveBeenCalledTimes(1);
  expect(resumePolling).not.toHaveBeenCalled();

  utils.unmount();

  expect(resumePolling).toHaveBeenCalledTimes(1);
  // Only mount pauses, only unmount resumes - neither fires a second time,
  // and in particular a poll tick does not touch either.
  expect(pausePolling).toHaveBeenCalledTimes(1);
});

// Proves the `typeof` guards in componentDidMount/componentWillUnmount: every
// other test in this file already renders and unmounts against a double built
// by `asyncTasksDouble()`, which (by design, see that function) carries
// neither method - so this one is explicit about what that absence is
// proving, rather than relying on it as a side effect.
test(
  'mounts and unmounts without throwing when asyncTasks has neither pause nor resume method',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    const utils = renderWithStores(<DownloadsManager />, stores());
    await waitFor(() => expect(filterCalls(api).length).toBe(1));

    expect(() => utils.unmount()).not.toThrow();
  }
);

// The table's loading overlay has no accessible handle - antd 2.13 renders it
// as a `Spin` with no role and no `aria-busy`, and rule 6 forbids querying
// antd's own class names - so these two read the page's own `state.loading`
// through `wrappedInstance`, the handle mobx-react's `inject` HOC exposes for
// exactly this. It is this component's own state, not an antd internal.
//
// Why it is worth testing at all: `pending` used to be wired straight to
// `this.request.pending`, which `send()` raises on every call. With the poll
// back, that would flash the overlay every five seconds for as long as the
// page stays open.
test('a poll tick does not raise the loading overlay', async () => {
  useFakeTimers();
  try {
    const api = stubApi();
    api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
    let holdSecondResponse;
    let callCount = 0;
    api.handle(FILTER_ROUTE, () => {
      callCount += 1;
      if (callCount === 1) {
        return ok([]);
      }
      // The poll tick's response is held open, so the assertion below runs
      // while that request is genuinely in flight - the exact window the
      // overlay used to appear in.
      return new Promise((resolve) => {
        holdSecondResponse = () => resolve(ok([]));
      });
    });

    let instance;
    renderWithStores(
      <DownloadsManager ref={(r) => { instance = r; }} />,
      stores()
    );

    // The first load DOES raise it and lowers it again once the response
    // lands - the behaviour the poll must not repeat.
    await waitFor(() => expect(instance.wrappedInstance.state.loading).toBe(true));
    await waitFor(() => expect(instance.wrappedInstance.state.loading).toBe(false));

    await waitFor(() => expect(filterCalls(api).length).toBe(2), {
      timeout: POLL_INTERVAL_MS * 2
    });
    expect(instance.wrappedInstance.state.loading).toBe(false);

    // Settled inside the test, and asserted on: a response left in flight
    // would land after the harness has already unmounted the tree.
    holdSecondResponse();
    await waitFor(() => expect(instance.wrappedInstance.state.fetchTick).toBe(2));
    expect(instance.wrappedInstance.state.loading).toBe(false);
  } finally {
    useRealTimers();
  }
});

// A failed fetch must not blank the table: `RemotePost.update` sets `loaded`
// back to false on a failure, so without the `tasks` getter's fallback the
// rows would vanish on any failure and come back on the next success - which,
// now that the page polls, means every five seconds on an endpoint that is
// intermittently failing. The error is still reported; this is about what
// stays on screen. Driven through an explicit filter commit rather than a
// poll tick because the behaviour is the getter's, not the poller's, and a
// failing tick's own interval backoff would still be settling as the test
// ends.
test('keeps the last rows on screen when a fetch fails', async () => {
  const api = stubApi();
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  let callCount = 0;
  api.handle(FILTER_ROUTE, () => {
    callCount += 1;
    if (callCount === 1) {
      return ok([
        {
          id: 1,
          status: 'RUNNING',
          params: {archiveName: 'archive-kept'},
          created: '2026-10-21T13:00:00.000Z'
        }
      ]);
    }
    return jsonResponse(fail('Service unavailable'));
  });

  renderWithLocale(<DownloadsManager />, {stores: stores()});

  expect(await screen.findByText('archive-kept.zip')).toBeInTheDocument();

  await click(statusFilterIcon());
  await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
  await click(screen.getByText('OK'));

  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  expect(screen.getByText('archive-kept.zip')).toBeInTheDocument();
});

// Cancelling is the only remaining explicit trigger that changes neither the
// filters nor the page, so it is what still proves a plain refetch resends
// the exact same request body - a filter commit or a page change would
// change the body by definition and so could not stand in for this one.
test(
  'cancelling a task re-issues the request with the same page and filters',
  async () => {
    const api = stubApi();
    stubFilter(api, [
      {
        id: 9,
        status: 'RUNNING',
        params: {archiveName: 'archive-same'},
        created: '2026-10-21T13:00:00.000Z'
      }
    ]);

    renderWithStores(<DownloadsManager />, stores());

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    const firstBody = JSON.parse(filterCalls(api)[0].body);

    const row = (await screen.findByText('archive-same.zip')).closest('tr');
    await click(within(row).getByRole('button', {name: /Cancel/i}));

    await waitFor(() => expect(filterCalls(api).length).toBe(2));
    expect(JSON.parse(filterCalls(api)[1].body)).toEqual(firstBody);
  }
);

// The regression test for the live-update bug: `this.request` is a
// `RemotePost` (models/basic/RemotePost.js), whose `value` is a plain getter
// backed by a field that is never `@observable` — so without `fetchTick`, a
// fetch past the first one writes fresh data into `this.request` with zero
// re-render, and the row below sits on the FIRST response forever. Driven
// through an explicit filter commit rather than a poll tick, which needs no
// fake clock: the underlying reactivity gap - and so this guard - is
// unrelated to what triggers the fetch, and the poll's own equivalent is
// covered by "keeps refreshing on its own while mounted" above.
test(
  'live-updates the row when an explicit refetch returns a changed response, not just the ' +
  'first load',
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

    renderWithLocale(<DownloadsManager />, {stores: stores()});

    // Captured once, before the filter commit: the row updates in place (the
    // task id doesn't change between fetches), so this stays attached.
    const row = (await screen.findByText('archive-live.zip')).closest('tr');
    expect(within(row).getByText('PENDING')).toBeInTheDocument();

    // Scoped to the row, not the whole screen: the Status filter dropdown
    // this opens lists every status as a checkbox label, including
    // "PENDING" itself, which would otherwise collide with the row's own
    // status text.
    await click(statusFilterIcon());
    await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
    await click(screen.getByText('OK'));

    await waitFor(() => expect(within(row).queryByText('PENDING')).not.toBeInTheDocument());
    expect(within(row).getByText('SUCCESS')).toBeInTheDocument();
  }
);

// The other half of "a poll tick does not raise the loading overlay" above:
// an explicit refetch still does, on every one of them rather than only the
// first load. Asserts on `state.loading` - what `pending` is actually wired
// to - and not on `this.request.pending`, which `send()` raises for a poll
// tick too and so can no longer tell the two cases apart.
test('shows the loading state on an explicit refetch, not just the first load', async () => {
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
  renderWithLocale(
    <DownloadsManager ref={(r) => { instance = r; }} />,
    {stores: stores()}
  );

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  await waitFor(() => expect(instance.wrappedInstance.state.loading).toBe(false));

  await click(statusFilterIcon());
  await click(screen.getByRole('checkbox', {name: 'CANCELLED'}));
  await click(screen.getByText('OK'));
  // `loadTasks` awaits `whoAmI.fetchIfNeededOrWait()` before `request.send`
  // ever reaches `global.fetch`, so the actual network call - and so
  // `resolveSecond` being assigned - lands a few microtasks after the click's
  // own single `await settle()`. Waiting for the call count confirms
  // `resolveSecond` is already assigned before using it.
  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  expect(instance.wrappedInstance.state.loading).toBe(true);

  resolveSecond(ok([]));
  await waitFor(() => expect(instance.wrappedInstance.state.loading).toBe(false));
});

// Paging is antd's own `Pagination` now, driven by the response's
// `totalCount` - so the control knows the real number of pages instead of
// guessing from "this page is full". Its items are `<li>`s carrying a
// `title`, which is how they are found here: a page number by its own title,
// the arrows by "Next Page"/"Previous Page" (rule 6 - a title is a label, not
// a CSS class).
//
// The arrows' titles come from antd's locale, which defaults to its bundled
// zh-CN unless a `LocaleProvider` is in scope - the same trap the filter-icon
// helpers above document. Every test that queries an arrow therefore renders
// through `renderWithLocale`; a page number's title is the number itself and
// needs no such care.
test('renders a page number per page of the total, not of this page', async () => {
  const api = stubApi();
  // One page of rows, four pages' worth of matches.
  stubFilter(api, buildTasks(PAGE_SIZE), PAGE_SIZE * 4);

  renderWithStores(<DownloadsManager />, stores());

  expect(await screen.findByTitle('4')).toBeInTheDocument();
  expect(screen.queryByTitle('5')).not.toBeInTheDocument();
});

test('renders no pagination at all when every match fits on one page', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(3));

  renderWithLocale(<DownloadsManager />, {stores: stores()});

  await waitFor(() => expect(filterCalls(api).length).toBe(1));
  await screen.findByText(/^archive-1\.zip/);
  expect(screen.queryByTitle('Next Page')).not.toBeInTheDocument();
});

// A full page of rows is no longer enough on its own: with a total that says
// there is nothing more, there is nothing to page to.
test('renders no pagination for a full page that is the whole match', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(PAGE_SIZE), PAGE_SIZE);

  renderWithLocale(<DownloadsManager />, {stores: stores()});

  // Waits for a ROW, not merely for the request: asserting the absence of a
  // control before the response has been applied would pass in the state the
  // old `tasks.length === PAGE_SIZE` guess also showed nothing in, proving
  // nothing about the total.
  await screen.findByText(/^archive-1\.zip/);
  expect(screen.queryByTitle('Next Page')).not.toBeInTheDocument();
});

test('clicking Next re-issues the request with page: 2', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(PAGE_SIZE), PAGE_SIZE * 2);

  renderWithLocale(<DownloadsManager />, {stores: stores()});

  await waitFor(() => expect(filterCalls(api).length).toBe(1));

  await click(await screen.findByTitle('Next Page'));

  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  const body = JSON.parse(filterCalls(api)[1].body);
  expect(body.page).toBe(2);
});

// The jump a hand-rolled first/prev/next row could not offer at all.
test('clicking the last page number asks for that page', async () => {
  const api = stubApi();
  stubFilter(api, buildTasks(PAGE_SIZE), PAGE_SIZE * 3);

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(filterCalls(api).length).toBe(1));

  await click(await screen.findByTitle('3'));

  await waitFor(() => expect(filterCalls(api).length).toBe(2));
  expect(JSON.parse(filterCalls(api)[1].body).page).toBe(3);
});

test(
  'changing a filter resets the body\'s page to 1, even after paging forward',
  async () => {
    const api = stubApi();
    stubFilter(api, buildTasks(PAGE_SIZE), PAGE_SIZE * 2);

    renderWithLocale(<DownloadsManager />, {stores: stores()});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));

    await click(await screen.findByTitle('Next Page'));
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

test(
  'the Owner column renders for a ROLE_STORAGE_ADMIN who is not a full admin',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {
      stores: {
        asyncTasks: asyncTasksDouble(),
        authenticatedUserInfo: authenticatedUserInfoDouble(
          false,
          [{name: 'ROLE_STORAGE_ADMIN'}]
        ),
        dataStorages: dataStoragesDouble()
      }
    });

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(screen.getByText('Owner')).toBeInTheDocument();
  }
);

test(
  'the Owner column is absent for a role that is not ROLE_STORAGE_ADMIN',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {
      stores: {
        asyncTasks: asyncTasksDouble(),
        authenticatedUserInfo: authenticatedUserInfoDouble(
          false,
          [{name: 'ROLE_STORAGE_MANAGER'}]
        ),
        dataStorages: dataStoragesDouble()
      }
    });

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(screen.queryByText('Owner')).not.toBeInTheDocument();
  }
);

test(
  'the Owner column is absent for a non-admin user with no roles at all, and does not throw',
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
  }
);

test(
  'the Owner column is absent while authenticatedUserInfo has not loaded yet',
  async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderWithLocale(<DownloadsManager />, {
      stores: {
        asyncTasks: asyncTasksDouble(),
        authenticatedUserInfo: observable({loaded: false, value: undefined}),
        dataStorages: dataStoragesDouble()
      }
    });

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    expect(screen.queryByText('Owner')).not.toBeInTheDocument();
  }
);
