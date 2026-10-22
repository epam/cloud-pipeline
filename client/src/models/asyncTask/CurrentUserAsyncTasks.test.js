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

import {stubApi, fn} from '@test';
import {CurrentUserAsyncTasks} from './CurrentUserAsyncTasks';
import {POLLED_STATUSES, ASYNC_TASK_STATUSES} from './statuses';
import preferences from '../preferences/PreferencesLoad';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';

function stubWhoAmI (api) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
}

// Every test constructs the store with `{autoUpdate: false}` — the testability
// seam the store exists for — so no test starts a real 5-second poll loop.
function newStore () {
  return new CurrentUserAsyncTasks({autoUpdate: false});
}

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

describe('fetch', () => {
  it('sends type, owner and POLLED_STATUSES, with no page or pageSize', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, []);
    const store = newStore();

    await store.fetch();

    const call = api.calls.find(c => c.path === FILTER_ROUTE);
    expect(call).toBeDefined();
    expect(call.method).toBe('POST');
    const body = JSON.parse(call.body);
    expect(body.type).toBe('DOWNLOAD_ARCHIVE');
    expect(body.owner).toBe(USER_NAME);
    // Order-independent: it is a set, and nothing depends on its order.
    expect([...body.statuses].sort()).toEqual([...POLLED_STATUSES].sort());
    // The opposite of the /downloads page's own request (phase 4): that one
    // sends no `statuses` key at all. This one always sends POLLED_STATUSES.
    expect(body.statuses).not.toContain(ASYNC_TASK_STATUSES.CANCELLED);
    expect(body.statuses).not.toContain(ASYNC_TASK_STATUSES.EXPIRED);
    expect(body).not.toHaveProperty('page');
    expect(body).not.toHaveProperty('pageSize');
  });
});

describe('tasks / activeTasks', () => {
  it('splits a mixed payload into tasks and activeTasks', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [
      {id: 1, status: 'RUNNING'},
      {id: 2, status: 'SUCCESS'},
      {id: 3, status: 'PENDING'}
    ]);
    const store = newStore();

    await store.fetch();

    expect(store.tasks).toHaveLength(3);
    expect(store.activeTasks).toHaveLength(2);
    expect(store.activeTasks.map(task => task.id).sort()).toEqual([1, 3]);
  });
});

describe('status-change notifications', () => {
  it('fires a listener once when a task transitions from active to SUCCESS', async () => {
    let api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 1, status: 'RUNNING'}]);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);

    await store.fetch();
    expect(listener).not.toHaveBeenCalled();

    // A fresh stub is how the second fetch gets a different response: stubApi's
    // route table has no "last registration wins" rule, so re-registering the
    // same path on the same stub would not override the first reply.
    api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 1, status: 'SUCCESS'}]);

    await store.fetch();

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({id: 1, status: 'SUCCESS'})
    );
  });

  it('fires nothing for a task that is already SUCCESS on the first fetch', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 10, status: 'SUCCESS'}]);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);

    await store.fetch();

    expect(listener).not.toHaveBeenCalled();
  });

  it('fires nothing for a transition to CANCELLED', async () => {
    let api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 5, status: 'RUNNING'}]);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    await store.fetch();

    api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 5, status: 'CANCELLED'}]);
    await store.fetch();

    expect(listener).not.toHaveBeenCalled();
  });

  it('fires nothing for a transition to EXPIRED', async () => {
    let api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 6, status: 'RUNNING'}]);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    await store.fetch();

    api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 6, status: 'EXPIRED'}]);
    await store.fetch();

    expect(listener).not.toHaveBeenCalled();
  });

  it('stops firing once removeStatusChangeListener is called', async () => {
    let api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 7, status: 'RUNNING'}]);
    const store = newStore();
    const listener = fn();
    store.addStatusChangeListener(listener);
    store.removeStatusChangeListener(listener);
    await store.fetch();

    api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 7, status: 'FAILED'}]);
    await store.fetch();

    expect(listener).not.toHaveBeenCalled();
  });
});

describe('activeTasksLimitReached', () => {
  it('is false below the cap', async () => {
    await setMaxUserTasks(2);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 1, status: 'RUNNING'}]);
    const store = newStore();

    await store.fetch();

    expect(store.activeTasksLimitReached).toBe(false);
  });

  it('is true at the cap', async () => {
    await setMaxUserTasks(2);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [
      {id: 1, status: 'RUNNING'},
      {id: 2, status: 'PENDING'}
    ]);
    const store = newStore();

    await store.fetch();

    expect(store.activeTasksLimitReached).toBe(true);
  });
});

describe('startArchiveDownload', () => {
  it('rejects, naming the limit, without posting when the cap is reached', async () => {
    await setMaxUserTasks(1);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, [{id: 1, status: 'RUNNING'}]);
    const store = newStore();
    await store.fetch();

    await expect(store.startArchiveDownload(7, ['a/'], 'archive-1'))
      .rejects.toThrow(/1 archive download/);
    expect(
      api.calls.some(c => c.path === '/datastorage/7/download/archive')
    ).toBe(false);
  });

  it('posts paths and archiveName, and returns the created task, below the cap', async () => {
    await setMaxUserTasks(5);
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, []);
    const createdTask = {id: 99, status: 'PENDING', params: {archiveName: 'archive-1'}};
    api.reply('/datastorage/7/download/archive', createdTask);
    const store = newStore();
    await store.fetch();

    const result = await store.startArchiveDownload(7, ['a/', 'b.txt'], 'archive-1');

    const call = api.calls.find(c => c.path === '/datastorage/7/download/archive');
    expect(call).toBeDefined();
    expect(call.method).toBe('POST');
    expect(JSON.parse(call.body)).toEqual({paths: ['a/', 'b.txt'], archiveName: 'archive-1'});
    expect(result).toEqual(createdTask);
  });
});

describe('cancelTask', () => {
  it('posts to /async-task/{id}/cancel and returns the task', async () => {
    const api = stubApi();
    stubWhoAmI(api);
    api.reply(FILTER_ROUTE, []);
    const cancelledTask = {id: 42, status: 'CANCEL_REQUESTED'};
    api.reply('/async-task/42/cancel', cancelledTask);
    const store = newStore();
    await store.fetch();

    const result = await store.cancelTask(42);

    const call = api.calls.find(c => c.path === '/async-task/42/cancel');
    expect(call).toBeDefined();
    expect(call.method).toBe('POST');
    expect(result).toEqual(cancelledTask);
  });
});

describe('consumeTask', () => {
  it('returns the signed URL out of {result: {url}}', async () => {
    const api = stubApi();
    api.reply('/async-task/42/consume', {
      type: 'DOWNLOAD_ARCHIVE',
      result: {url: 'https://example.com/archive.zip', expires: '2026-10-22T00:00:00.000Z'}
    });
    const store = newStore();

    const url = await store.consumeTask(42);

    expect(url).toBe('https://example.com/archive.zip');
  });
});
