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

// The success toast carries a link, so it is a ReactNode rather than a
// string. Rendering what the fake `messageApi` was handed is how its text and
// its link are asserted - there is no DOM for a toast that antd never showed.
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

// Both messages are download-shaped, so the handler announces that type only.
function downloadTaskFixture (task) {
  return {type: 'DOWNLOAD_ARCHIVE', status: 'SUCCESS', ...task};
}

function newFakeMessageApi () {
  return {
    // antd's `message.success` returns a hide function
    // (node_modules/antd/lib/message/index.js); the production success
    // branch captures it to close the toast on a click, so the fake must
    // return one too.
    success: fn().mockReturnValue(fn()),
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

    store.handler(downloadTaskFixture({id: 1, params: {archiveName: 'my-archive'}}));

    expect(messageApi.success).toHaveBeenCalledTimes(1);
    renderToast(messageApi.success.mock.calls[0][0]);
    expect(screen.getByText(/my-archive/)).toBeInTheDocument();
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  // `duration: 0` means the toast never dismisses itself
  // (node_modules/rc-notification/lib/Notice.js, which only schedules its
  // auto-close timer when `duration` is truthy) - it waits for the click
  // instead.
  it('calls messageApi.success with 0 as its duration', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler(downloadTaskFixture({id: 1, params: {archiveName: 'my-archive'}}));

    expect(messageApi.success.mock.calls[0][1]).toBe(0);
  });

  // The link to the now-removed /downloads route is gone; "Download it" is
  // the only link left in the toast.
  it('carries no "Downloads page" text, and "Download it" is its only link', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi, fn());

    store.handler(downloadTaskFixture({id: 8, params: {archiveName: 'my-archive'}}));
    renderToast(messageApi.success.mock.calls[0][0]);

    expect(screen.queryByText(/Downloads page/)).not.toBeInTheDocument();
    expect(screen.getByText('Download it').tagName).toBe('A');
    expect(screen.queryAllByText(/^Download/).map((node) => node.textContent))
      .toEqual(['Download it']);
  });

  // Clicking anywhere on the toast's content dismisses it - the whole `<span>`
  // carries the handler, not just the link.
  it('closes the toast when its content is clicked', async () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi, fn());

    store.handler(downloadTaskFixture({id: 1, params: {archiveName: 'my-archive'}}));
    const hide = messageApi.success.mock.results[0].value;
    renderToast(messageApi.success.mock.calls[0][0]);
    await click(screen.getByText(/my-archive/));

    expect(hide).toHaveBeenCalledTimes(1);
  });

  // The ask: "Download it" fetches the archive from the toast itself, the way
  // the Download button in the Downloads modal does, and its own click
  // bubbles into the content's handler, so the toast closes too.
  it('downloads the task and closes the toast when its link is clicked', async () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    const downloadTask = fn();
    start(store, messageApi, downloadTask);
    const task = downloadTaskFixture({id: 7, params: {archiveName: 'my-archive'}});

    store.handler(task);
    const hide = messageApi.success.mock.results[0].value;
    renderToast(messageApi.success.mock.calls[0][0]);
    await click(screen.getByText('Download it'));

    expect(downloadTask).toHaveBeenCalledTimes(1);
    expect(downloadTask).toHaveBeenCalledWith(task);
    expect(hide).toHaveBeenCalledTimes(1);
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

    store.handler(downloadTaskFixture({id: 9, params: {archiveName: 'my-archive'}}));
    renderToast(messageApi.success.mock.calls[0][0]);
    await click(screen.getByText('Download it'));

    expect(store.consumeTask).toHaveBeenCalledWith(9);
  });

  it('calls messageApi.error with the archive name and errorMessage for a FAILED task', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler(downloadTaskFixture({
      id: 2,
      status: 'FAILED',
      params: {archiveName: 'broken-archive'},
      errorMessage: 'disk is full'
    }));

    expect(messageApi.error).toHaveBeenCalledTimes(1);
    const [text] = messageApi.error.mock.calls[0];
    expect(text).toEqual(expect.stringContaining('broken-archive'));
    expect(text).toEqual(expect.stringContaining('disk is full'));
    expect(messageApi.success).not.toHaveBeenCalled();
  });

  // Named by the leaf of `sourcePath`: an extraction has no `archiveName`.
  it('announces a finished extraction, with no download link', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({
      id: 7,
      type: 'UNPACK_ARCHIVE',
      status: 'SUCCESS',
      params: {sourcePath: 'folder/a.zip'}
    });

    expect(messageApi.success).toHaveBeenCalledTimes(1);
    const [content, duration] = messageApi.success.mock.calls[0];
    // A plain string, not a node: there is nothing to click.
    expect(content).toBe('Archive "a.zip" has been extracted.');
    // Not 0: unlike a ready download, this toast has no action to wait for.
    expect(duration).toBe(5);
  });

  it('reports a failed extraction, with its reason', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({
      id: 8,
      type: 'UNPACK_ARCHIVE',
      status: 'FAILED',
      errorMessage: 'boom',
      params: {sourcePath: 'a.zip'}
    });

    expect(messageApi.error).toHaveBeenCalledWith('Extraction of "a.zip" has failed. boom', 5);
    expect(messageApi.success).not.toHaveBeenCalled();
  });

  // Neither message is download-shaped, so no link is ever offered for one.
  it('calls neither for a task of an unknown type', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler({id: 9, type: 'SOMETHING_ELSE', status: 'SUCCESS'});

    expect(messageApi.success).not.toHaveBeenCalled();
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  it('calls neither for an active status', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    store.handler(downloadTaskFixture({id: 3, status: 'RUNNING'}));

    expect(messageApi.success).not.toHaveBeenCalled();
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  it('falls back to the task id and does not throw when params is missing', () => {
    const store = newFakeStore();
    const messageApi = newFakeMessageApi();
    start(store, messageApi);

    expect(() => store.handler(downloadTaskFixture({id: 4}))).not.toThrow();

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
