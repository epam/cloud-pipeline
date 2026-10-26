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

import moment from 'moment-timezone';
import {
  stubApi,
  fn,
  ok,
  waitFor,
  flushPromises,
  useFakeTimers,
  useRealTimers
} from '@test';
import {CurrentUserAsyncTasks} from './CurrentUserAsyncTasks';
import {ACTIVE_STATUSES, ASYNC_TASK_STATUSES} from './statuses';
import {loadEntries, saveEntries} from './active-tasks-storage';
import preferences from '../preferences/PreferencesLoad';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';
// Mirrors the store's own POLL_INTERVAL_MS (not exported, and not part of the
// store's public surface).
const POLL_INTERVAL_MS = 5000;

function stubWhoAmI (api) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
}

function taskRoute (id) {
  return `/async-task/${id}`;
}

// `POST /async-task/filter` answers a paged result - `{elements, totalCount}`,
// the total being what the FILTER matched rather than what the page carries.
// Nothing here pages, so the two agree; `AsyncTaskFilter.test.js` is where the
// shape itself, and the older bare-array form, are pinned.
function filterPage (tasks) {
  return {elements: tasks, totalCount: tasks.length};
}

function daysAgo (days) {
  return moment.utc().subtract(days, 'days').toISOString();
}

const YESTERDAY = () => daysAgo(1);
// Far past any cut-off anyone would configure - and still watched, because
// none is configured today. See `ACTIVE_TASK_TTL_DAYS`.
const LONG_AGO = () => daysAgo(400);

// Every store built here is built with `{autoUpdate: false}` - the testability
// seam - so no test kicks off the start-up restore by accident; the tests that
// want it call `restore()` themselves. Each one is still remembered so its
// per-task pollers can be stopped afterwards: a poller left running fires into
// the next test, where the stub is gone and the harness's network guard throws
// from a torn-down jsdom window.
const builtStores = [];

function newStore () {
  const store = new CurrentUserAsyncTasks({autoUpdate: false});
  builtStores.push(store);
  return store;
}

afterEach(async () => {
  builtStores.splice(0).forEach(store => {
    store.pollers.forEach(poller => poller.stop());
    store.pollers.clear();
  });
  // `stop()` cancels the next tick but not a request already in flight, whose
  // `pollTask` carries on and - on a failure - reaches continuousFetch's
  // interval backoff, which reads `window.crypto`. Draining here, while the
  // stub is still installed, keeps that from landing after jsdom has torn the
  // window down. This hook is declared after the harness's own stub teardown
  // (registered when `@test` is imported) and so runs before it: jest runs
  // `afterEach` hooks in reverse declaration order.
  await flushPromises();
  localStorage.clear();
});

async function setMaxUserTasks (maxUserTasks) {
  stubApi({
    '/preferences': [
      {
        name: 'storage.download.archive.config',
        value: JSON.stringify({maxUserTasks})
      }
    ]
  });
  preferences.invalidateCache();
  await preferences.fetch();
}

describe('trackTask', () => {
  it('persists the task and starts polling it', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: YESTERDAY()});
    const store = newStore();

    store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});

    expect(loadEntries()).toEqual([
      {id: '1', created: expect.any(String), owner: USER_NAME}
    ]);
    expect(store.pollers.has('1')).toBe(true);
    await waitFor(() => expect(api.calls.some(c => c.path === taskRoute(1))).toBe(true));
  });

  // "Three clicks, three polls" - each queued task is watched on its own,
  // which is the whole point of dropping the single list poll.
  it('watches each task separately', () => {
    const api = stubApi();
    stubWhoAmI(api);
    [1, 2, 3].forEach(id => api.reply(taskRoute(id), {id, status: 'PENDING'}));
    const store = newStore();

    [1, 2, 3].forEach(id => store.trackTask({id, created: YESTERDAY(), owner: USER_NAME}));

    expect(loadEntries().map(entry => entry.id)).toEqual(['1', '2', '3']);
    expect(store.pollers.size).toBe(3);
  });

  it('is idempotent for a task already being watched', () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'PENDING'});
    const store = newStore();

    store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});
    const poller = store.pollers.get('1');
    store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});

    expect(loadEntries()).toHaveLength(1);
    expect(store.pollers.get('1')).toBe(poller);
  });

  // With the age cut-off switched off (`ACTIVE_TASK_TTL_DAYS` is `undefined`,
  // see active-tasks-storage.js), age is no reason not to watch a task. The
  // cut-off's own logic is tested in that module, with a number of days passed
  // in; here the point is that nothing in this store applies one today.
  it('watches a task of any age while the cut-off is switched off', () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: LONG_AGO()});
    const store = newStore();

    store.trackTask({id: 1, created: LONG_AGO(), owner: USER_NAME});

    expect(loadEntries().map(entry => entry.id)).toEqual(['1']);
    expect(store.pollers.has('1')).toBe(true);
  });
});

describe('polling one task', () => {
  it('announces a task that comes back SUCCESS, and stops watching it', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    const task = {id: 1, status: 'SUCCESS', created: YESTERDAY()};
    api.reply(taskRoute(1), task);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({status: 'SUCCESS'}));
    expect(loadEntries()).toEqual([]);
  });

  it('announces a task that comes back FAILED', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'FAILED', created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('keeps watching a task that is still active', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).not.toHaveBeenCalled();
    expect(loadEntries()).toHaveLength(1);
  });

  // A cancellation the user asked for needs no toast, and an expiry is not an
  // event they are waiting on - but both are terminal, so both stop the watch.
  it.each([
    ASYNC_TASK_STATUSES.CANCELLED,
    ASYNC_TASK_STATUSES.EXPIRED
  ])('stops watching a %s task without announcing it', async (status) => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status, created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).not.toHaveBeenCalled();
    expect(loadEntries()).toEqual([]);
  });

  // The "closed the application for a week" case, as it behaves with the age
  // cut-off switched off: the task is asked about and announced however long
  // ago it was queued. Turning `ACTIVE_TASK_TTL_DAYS` into a number of days
  // is what makes this case silent instead - the cut-off's own logic is
  // covered in active-tasks-storage.test.js.
  it('announces a long-forgotten task while the cut-off is switched off', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'SUCCESS', created: LONG_AGO()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: LONG_AGO(), owner: USER_NAME}]);

    await store.pollTask({id: '1', created: LONG_AGO()});

    expect(api.calls.some(c => c.path === taskRoute(1))).toBe(true);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(loadEntries()).toEqual([]);
  });

  // The server answered, and the answer was no: a stale id is never going to
  // become valid, so asking every five seconds for ever is the wrong answer.
  it('stops watching a task the server will not answer for', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.fail(taskRoute(1), 'Async task 1 was not found');
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).not.toHaveBeenCalled();
    expect(loadEntries()).toEqual([]);
  });

  // A network failure says nothing about the task, so it must not drop it -
  // and it re-throws, which is what makes continuousFetch widen the interval.
  it('keeps watching, and re-throws, when the server cannot be reached', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.networkError(taskRoute(1), 'Failed to fetch');
    const store = newStore();
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await expect(store.pollTask({id: '1', created: YESTERDAY()}))
      .rejects.toThrow(/Failed to fetch/);
    expect(loadEntries()).toHaveLength(1);
  });

  it('keeps watching when the response carries no status', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).not.toHaveBeenCalled();
    expect(loadEntries()).toHaveLength(1);
  });

  // Proves the poller itself is wired, not just `pollTask`: nothing here asks
  // twice, so the second request is the interval firing.
  it('keeps asking on an interval while the task is active', async () => {
    useFakeTimers();
    try {
      const api = stubApi();
      stubWhoAmI(api);
      api.reply(taskRoute(5), {id: 5, status: 'RUNNING', created: YESTERDAY()});
      const store = newStore();

      store.trackTask({id: 5, created: YESTERDAY(), owner: USER_NAME});

      await waitFor(() => expect(
        api.calls.filter(c => c.path === taskRoute(5)).length
      ).toBe(2), {timeout: POLL_INTERVAL_MS * 2});
    } finally {
      useRealTimers();
    }
  });
});

describe('restore', () => {
  // The reload case, and the reason the ids are persisted at all: the entry
  // itself is the record that the task was still running, so the first
  // response after a restart IS the transition.
  it('polls what was stored and announces a task that finished while away', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([]));
    api.reply(taskRoute(1), {id: 1, status: 'SUCCESS', created: YESTERDAY()});
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);

    await store.restore();

    await waitFor(() => expect(listener).toHaveBeenCalledTimes(1));
    expect(loadEntries()).toEqual([]);
  });

  it('still picks up a long-forgotten entry while the cut-off is switched off', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([]));
    api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: LONG_AGO()});
    saveEntries([{id: '1', created: LONG_AGO(), owner: USER_NAME}]);
    const store = newStore();

    await store.restore();

    expect(store.pollers.has('1')).toBe(true);
    expect(loadEntries()).toHaveLength(1);
  });

  // Two people on one machine: the other user's ids are neither polled nor
  // kept. This user cannot be told anything useful about them, and the API
  // would refuse the request anyway.
  it('deletes another user\'s entries instead of polling them', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([]));
    saveEntries([{id: '1', created: YESTERDAY(), owner: 'someone-else'}]);
    const store = newStore();

    await store.restore();

    expect(api.calls.some(c => c.path === taskRoute(1))).toBe(false);
    expect(store.pollers.size).toBe(0);
    expect(loadEntries()).toEqual([]);
  });

  // The several-machines case: three archives queued on one computer and
  // three on another leave nothing in a third browser's `localStorage`, so
  // start-up asks the server once for what is active and watches those too.
  it('picks up tasks queued elsewhere from the server\'s active list', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([
      {id: 11, status: 'RUNNING', created: YESTERDAY(), owner: USER_NAME},
      {id: 12, status: 'PENDING', created: YESTERDAY(), owner: USER_NAME}
    ]));
    api.reply(taskRoute(11), {id: 11, status: 'RUNNING', created: YESTERDAY()});
    api.reply(taskRoute(12), {id: 12, status: 'PENDING', created: YESTERDAY()});
    const store = newStore();

    await store.restore();

    expect(loadEntries().map(entry => entry.id).sort()).toEqual(['11', '12']);
    expect(store.pollers.size).toBe(2);
    const body = JSON.parse(api.calls.find(c => c.path === FILTER_ROUTE).body);
    expect([...body.statuses].sort()).toEqual([...ACTIVE_STATUSES].sort());
    expect(body.owner).toBe(USER_NAME);
  });

  // A task that had already finished before start-up is not in the active
  // list, so it is not picked up and not announced - whoever queued it was
  // told on the machine they queued it from.
  it('does not announce anything from the sync itself', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([]));
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);

    await store.restore();

    expect(listener).not.toHaveBeenCalled();
    expect(store.pollers.size).toBe(0);
  });

  it('keeps polling the stored entries when the sync fails', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.networkError(FILTER_ROUTE, 'Failed to fetch');
    api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: YESTERDAY()});
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);
    const store = newStore();

    await store.restore();

    expect(store.pollers.has('1')).toBe(true);
    expect(loadEntries()).toHaveLength(1);
  });
});

describe('status-change listeners', () => {
  it('stops firing once removeStatusChangeListener is called', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'SUCCESS', created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    store.removeStatusChangeListener(listener);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).not.toHaveBeenCalled();
  });

  it('a listener that throws does not stop the others', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'SUCCESS', created: YESTERDAY()});
    const store = newStore();
    const throwing = fn(() => { throw new Error('nope'); });
    const listener = fn();
    store.addStatusChangeListener(throwing);
    store.addStatusChangeListener(listener);

    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).toHaveBeenCalledTimes(1);
  });
});

describe('fetchActiveTasks / fetchActiveTasksCount', () => {
  it('asks for type, owner and ACTIVE_STATUSES only, with no page or pageSize', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([{id: 1, status: 'RUNNING'}]));
    const store = newStore();

    const count = await store.fetchActiveTasksCount();

    expect(count).toBe(1);
    const call = api.calls.filter(c => c.path === FILTER_ROUTE).pop();
    const body = JSON.parse(call.body);
    expect(call.method).toBe('POST');
    expect(body.type).toBe('DOWNLOAD_ARCHIVE');
    expect(body.owner).toBe(USER_NAME);
    // Order-independent: it is a set.
    expect([...body.statuses].sort()).toEqual([...ACTIVE_STATUSES].sort());
    expect(body.statuses).not.toContain(ASYNC_TASK_STATUSES.SUCCESS);
    expect(body).not.toHaveProperty('page');
    expect(body).not.toHaveProperty('pageSize');
  });

  it('counts only the tasks that are really active', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([
      {id: 1, status: 'RUNNING'},
      {id: 2, status: 'SUCCESS'}
    ]));
    const store = newStore();

    await expect(store.fetchActiveTasksCount()).resolves.toBe(1);
  });
});

describe('checkActiveTasksLimit', () => {
  it('allows when the server reports fewer active tasks than the cap', async () => {
    await setMaxUserTasks(2);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([{id: 1, status: 'RUNNING'}]));
    const store = newStore();

    await expect(store.checkActiveTasksLimit()).resolves.toEqual({allowed: true});
  });

  it('blocks, naming the cap, when the server reports it reached', async () => {
    await setMaxUserTasks(2);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([
      {id: 1, status: 'RUNNING'},
      {id: 2, status: 'PENDING'}
    ]));
    const store = newStore();

    const result = await store.checkActiveTasksLimit();

    expect(result.allowed).toBe(false);
    expect(result.reason).toMatch(/2 archive downloads/);
  });

  // A check that cannot run must not be the thing that stops a legitimate
  // download: the server enforces this quota itself
  // (DataStorageDownloadArchiveManager.assertUserQuota).
  it('allows when the count request fails', async () => {
    await setMaxUserTasks(1);
    const api = stubApi();
    stubWhoAmI(api);
    api.fail(FILTER_ROUTE, 'Service unavailable');
    const store = newStore();

    await expect(store.checkActiveTasksLimit()).resolves.toEqual({allowed: true});
  });
});

describe('startArchiveDownload', () => {
  it('rejects, naming the limit, without posting when the cap is reached', async () => {
    await setMaxUserTasks(1);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([{id: 1, status: 'RUNNING'}]));
    const store = newStore();

    await expect(store.startArchiveDownload(7, ['a/'], 'archive-1'))
      .rejects.toThrow(/1 archive download/);
    expect(
      api.calls.some(c => c.path === '/datastorage/7/download/archive')
    ).toBe(false);
    expect(loadEntries()).toEqual([]);
  });

  it('posts paths and archiveName, and returns the created task, below the cap', async () => {
    await setMaxUserTasks(5);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([]));
    const createdTask = {
      id: 99,
      status: 'PENDING',
      created: YESTERDAY(),
      owner: USER_NAME,
      params: {archiveName: 'archive-1'}
    };
    api.reply('/datastorage/7/download/archive', createdTask);
    api.reply(taskRoute(99), createdTask);
    const store = newStore();

    const result = await store.startArchiveDownload(7, ['a/', 'b.txt'], 'archive-1');

    const call = api.calls.find(c => c.path === '/datastorage/7/download/archive');
    expect(call).toBeDefined();
    expect(call.method).toBe('POST');
    expect(JSON.parse(call.body)).toEqual({paths: ['a/', 'b.txt'], archiveName: 'archive-1'});
    expect(result).toEqual(createdTask);
  });

  // Queueing is what starts the watch, and it is persisted immediately - a
  // reload a second later must not lose the task.
  it('starts watching the task it queued, and persists it', async () => {
    await setMaxUserTasks(5);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([]));
    const createdTask = {id: 99, status: 'PENDING', created: YESTERDAY(), owner: USER_NAME};
    api.reply('/datastorage/7/download/archive', createdTask);
    api.reply(taskRoute(99), createdTask);
    const store = newStore();

    await store.startArchiveDownload(7, ['a/'], 'archive-1');

    expect(loadEntries().map(entry => entry.id)).toEqual(['99']);
    expect(store.pollers.has('99')).toBe(true);
  });

  // The regression test for the bug the server-counted gate was built for: a
  // local figure can be out of date by a whole poll interval - or know nothing
  // at all about a task queued on another machine - so the count comes from
  // the server every time.
  it('refuses on the server\'s count even with nothing watched locally', async () => {
    await setMaxUserTasks(2);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, filterPage([
      {id: 1, status: 'RUNNING'},
      {id: 2, status: 'PENDING'}
    ]));
    api.reply('/datastorage/7/download/archive', {id: 99});
    const store = newStore();
    expect(store.pollers.size).toBe(0);

    await expect(store.startArchiveDownload(7, ['a/'], 'archive-1'))
      .rejects.toThrow(/2 archive downloads/);
    expect(
      api.calls.some(c => c.path === '/datastorage/7/download/archive')
    ).toBe(false);
  });
});

describe('cancelTask', () => {
  it('posts to /async-task/{id}/cancel and returns the task', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    const cancelledTask = {id: 42, status: 'CANCEL_REQUESTED'};
    api.reply('/async-task/42/cancel', cancelledTask);
    const store = newStore();

    const result = await store.cancelTask(42);

    const call = api.calls.find(c => c.path === '/async-task/42/cancel');
    expect(call).toBeDefined();
    expect(call.method).toBe('POST');
    expect(result).toEqual(cancelledTask);
  });

  // Nothing is untracked here on purpose: the server only *asks* a running
  // task to stop, so the task's own poll is what sees CANCELLED - and stops
  // watching then, with no toast.
  it('leaves the task being watched', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(42), {id: 42, status: 'RUNNING', created: YESTERDAY()});
    api.reply('/async-task/42/cancel', {id: 42, status: 'CANCEL_REQUESTED'});
    const store = newStore();
    store.trackTask({id: 42, created: YESTERDAY(), owner: USER_NAME});

    await store.cancelTask(42);

    expect(store.pollers.has('42')).toBe(true);
    expect(loadEntries().map(entry => entry.id)).toEqual(['42']);
  });
});

describe('pausePolling / resumePolling', () => {
  it('are safe no-ops when nothing is being watched', () => {
    stubApi();
    const store = newStore();

    expect(() => store.pausePolling()).not.toThrow();
    expect(() => store.resumePolling()).not.toThrow();
  });

  // What `/downloads` relies on: while that page is open, a task finishing is
  // visible in its table, and a toast about it would be noise.
  it('announces nothing while paused, and stops watching the finished task', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'SUCCESS', created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});

    store.pausePolling();
    await store.pollTask({id: '1', created: YESTERDAY()});

    expect(listener).not.toHaveBeenCalled();
    expect(loadEntries()).toEqual([]);
  });

  it('pausing stops the scheduled polls, and resuming restarts them', async () => {
    useFakeTimers();
    try {
      const api = stubApi();
      stubWhoAmI(api);
      api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: YESTERDAY()});
      const store = newStore();
      store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});
      const calls = () => api.calls.filter(c => c.path === taskRoute(1)).length;

      await waitFor(() => expect(calls()).toBe(1));

      store.pausePolling();

      // Proves the polls stopped rather than merely slowed: a `waitFor`
      // expected to time out is what drains the fake clock far enough to tell
      // the two apart.
      await expect(waitFor(() => expect(calls()).toBe(2), {
        timeout: POLL_INTERVAL_MS * 2
      })).rejects.toThrow();
      expect(calls()).toBe(1);

      store.resumePolling();

      // `setContinuous(true)` fetches immediately, so this needs no further
      // interval.
      await waitFor(() => expect(calls()).toBe(2));
    } finally {
      useRealTimers();
    }
  });

  // Resuming re-asks about every watched task at once, and THAT answer is the
  // one that would announce a task the user has just watched finish in the
  // `/downloads` table. Driven entirely through the poller: the silence is one
  // answer per task, and the resume's own immediate fetch is the answer it is
  // meant for.
  it('silences the answer the resume itself fetches', async () => {
    useFakeTimers();
    try {
      const api = stubApi();
      stubWhoAmI(api);
      let status = 'RUNNING';
      api.handle(taskRoute(1), () => ok({id: 1, status, created: YESTERDAY()}));
      const store = newStore();
      const listener = fn();
      store.addStatusChangeListener(listener);
      const calls = () => api.calls.filter(c => c.path === taskRoute(1)).length;

      store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});
      await waitFor(() => expect(calls()).toBe(1));
      expect(listener).not.toHaveBeenCalled();

      // The task finishes while the page holds the tracker paused.
      status = 'SUCCESS';
      store.pausePolling();
      store.resumePolling();

      await waitFor(() => expect(calls()).toBe(2));
      expect(listener).not.toHaveBeenCalled();
      // Still untracked, just not announced.
      await waitFor(() => expect(loadEntries()).toEqual([]));
    } finally {
      useRealTimers();
    }
  });

  // The silence is per task and consumed once, so a task that starts being
  // watched AFTER a resume is announced normally even though another task is
  // still holding its own silence token. Both tasks are really watched here -
  // a version of this test that skipped `trackTask` would pass even if
  // `resumePolling` silenced everything for ever, since there would be
  // nothing in the pollers map for it to silence.
  it('does not silence a task that starts being watched after the resume', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'RUNNING', created: YESTERDAY()});
    api.reply(taskRoute(2), {id: 2, status: 'SUCCESS', created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);

    store.trackTask({id: 1, created: YESTERDAY(), owner: USER_NAME});
    store.pausePolling();
    store.resumePolling();
    expect(store.silenced.has('1')).toBe(true);

    // Queued after the resume, and finished: announced.
    store.trackTask({id: 2, created: YESTERDAY(), owner: USER_NAME});

    await waitFor(() => expect(listener).toHaveBeenCalledTimes(1));
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({id: 2}));
    // Task 1 is still watched: its silenced answer said RUNNING, which is not
    // terminal, so the only thing the silence cost was an announcement it had
    // nothing to announce.
    expect(store.pollers.has('1')).toBe(true);
  });

  // The duplicate-toast guard: `continuousFetch`'s Page-Visibility `resume()`
  // fetches with no in-flight check, so two responses for one task can arrive
  // at once - each from its own `AsyncTaskLoad`, so nothing lower down
  // de-duplicates them.
  it('announces a finished task once even if two answers arrive for it', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(taskRoute(1), {id: 1, status: 'SUCCESS', created: YESTERDAY()});
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    saveEntries([{id: '1', created: YESTERDAY(), owner: USER_NAME}]);

    await Promise.all([
      store.pollTask({id: '1', created: YESTERDAY()}),
      store.pollTask({id: '1', created: YESTERDAY()})
    ]);

    expect(listener).toHaveBeenCalledTimes(1);
  });
});

describe('consumeTask', () => {
  it('returns the signed URL out of {result: {url}}', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply('/async-task/55/consume', {
      type: 'DOWNLOAD_ARCHIVE',
      result: {url: 'https://example.com/archive.zip'}
    });
    const store = newStore();

    await expect(store.consumeTask(55)).resolves
      .toBe('https://example.com/archive.zip');
  });

  it('rejects when the request fails', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.fail('/async-task/55/consume', 'Archive has expired');
    const store = newStore();

    await expect(store.consumeTask(55)).rejects.toThrow(/expired/);
  });
});
