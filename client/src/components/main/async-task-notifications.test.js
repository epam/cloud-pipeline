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

import {fn} from '@test';
import startAsyncTaskNotifications from './async-task-notifications';

function newFakeStore () {
  return {
    handler: undefined,
    registrations: 0,
    addStatusChangeListener (listener) {
      this.handler = listener;
      this.registrations += 1;
    },
    removeStatusChangeListener () {
      this.handler = undefined;
    }
  };
}

function newFakeMessageApi () {
  return {
    success: fn(),
    error: fn()
  };
}

describe('startAsyncTaskNotifications', () => {
  // The module keeps its "already started" flag at module scope (that is the
  // whole point of the idempotency requirement below), so every test tears
  // its own subscription down afterwards - otherwise the next test's call
  // would see the module already started and silently skip registering on
  // its own, fresh fake store.
  let activeTeardown;

  function start (store, messageApi) {
    activeTeardown = startAsyncTaskNotifications(store, messageApi);
    return activeTeardown;
  }

  afterEach(() => {
    if (activeTeardown) {
      activeTeardown();
      activeTeardown = undefined;
    }
  });

  it('calls messageApi.success, naming the archive, for a SUCCESS task', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({id: 1, status: 'SUCCESS', params: {archiveName: 'my-archive'}});

    expect(messageApi.success).toHaveBeenCalledTimes(1);
    expect(messageApi.success.mock.calls[0][0]).toEqual(expect.stringContaining('my-archive'));
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  it('calls messageApi.error with the archive name and errorMessage for a FAILED task', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({
      id: 2,
      status: 'FAILED',
      params: {archiveName: 'broken-archive'},
      errorMessage: 'disk is full'
    });

    expect(messageApi.error).toHaveBeenCalledTimes(1);
    const [text] = messageApi.error.mock.calls[0];
    expect(text).toEqual(expect.stringContaining('broken-archive'));
    expect(text).toEqual(expect.stringContaining('disk is full'));
    expect(messageApi.success).not.toHaveBeenCalled();
  });

  it('calls neither for an active status', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({id: 3, status: 'RUNNING'});

    expect(messageApi.success).not.toHaveBeenCalled();
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  it('falls back to the task id and does not throw when params is missing', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    expect(() => store.handler({id: 4, status: 'SUCCESS'})).not.toThrow();

    expect(messageApi.success).toHaveBeenCalledTimes(1);
    expect(messageApi.success.mock.calls[0][0]).toEqual(expect.stringContaining('4'));
  });

  it('returns a teardown that unregisters the handler', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    const teardown = start(store, messageApi);

    expect(store.handler).toBeDefined();
    teardown();
    expect(store.handler).toBeUndefined();
    activeTeardown = undefined;
  });

  it('registers only one handler when called twice', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();

    start(store, messageApi);
    startAsyncTaskNotifications(store, messageApi);

    expect(store.registrations).toBe(1);
  });
});
