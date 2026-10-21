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
import {renderWithStores, screen, within, click, waitFor, fn, stubApi} from '@test';
import DownloadsManager from './DownloadsManager';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';

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

function stores (overrides = {}) {
  // `preferences` is only here because DownloadsManager is `@inject`ed with it;
  // mobx-react's injector throws unless the key is present in the Provider, so
  // an empty double is enough to satisfy it without the test needing any of
  // its behaviour.
  return {asyncTasks: asyncTasksDouble(overrides), preferences: {}};
}

function stubFilter (api, tasks) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  api.reply(FILTER_ROUTE, tasks);
}

test('fetches its own unfiltered task list on mount, with no statuses/page/pageSize', async () => {
  const api = stubApi();
  stubFilter(api, []);

  renderWithStores(<DownloadsManager />, stores());

  await waitFor(() => expect(api.calls.some(c => c.path === FILTER_ROUTE)).toBe(true));
  const call = api.calls.find(c => c.path === FILTER_ROUTE);
  expect(call.method).toBe('POST');
  const body = JSON.parse(call.body);
  expect(body.type).toBe('DOWNLOAD_ARCHIVE');
  expect(body.owner).toBe(USER_NAME);
  expect(body).not.toHaveProperty('statuses');
  expect(body).not.toHaveProperty('page');
  expect(body).not.toHaveProperty('pageSize');
});

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

  renderWithStores(<DownloadsManager />, {asyncTasks, preferences: {}});

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

  renderWithStores(<DownloadsManager />, {asyncTasks, preferences: {}});

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
