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
import {fn, render, screen, click} from '@test';
import startAsyncTaskNotifications from './async-task-notifications';

// Both toasts now carry links, so they are ReactNodes rather than strings.
// Rendering what the fake `messageApi` was handed is how their text and their
// links are asserted - there is no DOM for a toast that antd never showed.
function renderToast (node) {
  render(<span>{node}</span>);
}

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

  function start (store, messageApi, downloadTask) {
    activeTeardown = startAsyncTaskNotifications(store, messageApi, downloadTask);
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
    renderToast(messageApi.success.mock.calls[0][0]);
    expect(screen.getByText(/my-archive/)).toBeInTheDocument();
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  // The ask: "Download it" fetches the archive from the toast itself, the way
  // the Download button on the /downloads page does.
  it('downloads the task when the success toast\'s link is clicked', async () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    const downloadTask = fn();
    start(store, messageApi, downloadTask);
    const task = {id: 7, status: 'SUCCESS', params: {archiveName: 'my-archive'}};

    store.handler(task);
    renderToast(messageApi.success.mock.calls[0][0]);
    await click(screen.getByText('Download it'));

    expect(downloadTask).toHaveBeenCalledTimes(1);
    expect(downloadTask).toHaveBeenCalledWith(task);
  });

  // Without this, the production wiring - `downloadArchiveTask(store, task)`,
  // the default for the parameter the test above replaces - has no coverage
  // at all, and a swapped argument order would ship green. `consumeTask`
  // answering nothing is what keeps this away from `window.open`: that helper
  // turns a missing URL into its own error toast.
  it('its default download asks the store to consume that task', async () => {
    const store = newFakeStore();
    store.consumeTask = fn().mockResolvedValue(undefined);
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({id: 9, status: 'SUCCESS', params: {archiveName: 'my-archive'}});
    renderToast(messageApi.success.mock.calls[0][0]);
    await click(screen.getByText('Download it'));

    expect(store.consumeTask).toHaveBeenCalledWith(9);
  });

  it('offers the Downloads page as a hash link, which needs no router', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi, fn());

    store.handler({id: 8, status: 'SUCCESS', params: {archiveName: 'my-archive'}});
    renderToast(messageApi.success.mock.calls[0][0]);

    expect(screen.getByText('Downloads page')).toHaveAttribute('href', '#/downloads');
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
    renderToast(messageApi.success.mock.calls[0][0]);
    expect(screen.getByText(/"4"/)).toBeInTheDocument();
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
