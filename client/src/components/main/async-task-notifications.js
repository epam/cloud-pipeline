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
import {message} from 'antd';
import asyncTasks from '../../models/asyncTask/CurrentUserAsyncTasks';
import {ASYNC_TASK_STATUSES, normalizeStatus, archiveNameOf} from '../../models/asyncTask/statuses';
import downloadArchiveTask from './downloads/download-archive-task';

// Announces, with an antd `message`, that one of the current user's
// archive-download tasks has finished - whatever page the user is on. This is
// the completion toast only: the "archive download queued" toast fires
// elsewhere, as soon as the task is created, not here.
//
// This lives outside both the store (CurrentUserAsyncTasks) and the
// server-driven NotificationCenter: `message` is imported only under
// `client/src/components/`, never in `client/src/models/` or
// `client/src/utils/`, and NotificationCenter has no channel for a
// client-derived event. So this small module subscribes to the store's
// listener API and is started once, from Root.js module scope.

let started = false;
let teardown = () => {};

function notify (task, messageApi, download) {
  const status = normalizeStatus(task && task.status);
  const archiveName = archiveNameOf(task);
  if (status === ASYNC_TASK_STATUSES.SUCCESS) {
    // The content is passed to the call that produces its own dismisser, so
    // the handler it needs cannot be built from that return value - it has
    // to exist first, as a mutable binding `success` fills in once it
    // returns. `close` is what both a click anywhere on the toast and a
    // click on "Download it" (which bubbles into it) end up calling.
    let hide = () => {};
    const close = () => hide();
    // "Download it" fetches the archive from the toast itself - the same
    // `downloadArchiveTask` the Download button on the /downloads page
    // calls, so the signed URL is requested at the moment of the click and
    // the browser downloads it. `duration: 0` plus `close` means the toast
    // stays until the user dismisses it, rather than vanishing after five
    // seconds whether or not it was read.
    hide = messageApi.success(
      <span onClick={close}>
        {`Archive "${archiveName}" is ready. `}
        <a onClick={() => download(task)}>
          {'Download it'}
        </a>
        {' now.'}
      </span>,
      0
    );
  } else if (status === ASYNC_TASK_STATUSES.FAILED) {
    const reason = task && task.errorMessage ? ` ${task.errorMessage}` : '';
    messageApi.error(
      `Archive "${archiveName}" download has failed.${reason}`,
      5
    );
  }
  // Any other status is not announced: the store only ever fires this
  // listener for a SUCCESS/FAILED transition (see `taskIsFinished` in
  // `client/src/models/asyncTask/statuses.js`), but this handler does not
  // assume that.
}

/**
 * Subscribes to the given store's status-change listener and raises an antd
 * `message` on a SUCCESS or FAILED transition. Idempotent: a second call is a
 * no-op that returns the first call's teardown, so a hot reload re-running
 * Root.js module scope cannot double-subscribe and double every toast.
 * @param {*} [store] - defaults to the CurrentUserAsyncTasks singleton
 * @param {*} [messageApi] - defaults to antd's `message`, taken as a
 *   parameter so this is testable without module mocking
 * @param {function(*): Promise<void>} [downloadTask] - what the success
 *   toast's "Download it" link does, defaulting to the same helper the
 *   /downloads page's Download button uses; a parameter for the same reason
 *   as `messageApi`, since it raises toasts of its own
 * @returns {function(): void} a teardown function
 */
export default function startAsyncTaskNotifications (
  store = asyncTasks,
  messageApi = message,
  downloadTask = (task) => downloadArchiveTask(store, task)
) {
  if (started) {
    return teardown;
  }
  if (!store || typeof store.addStatusChangeListener !== 'function') {
    return () => {};
  }
  const handler = (task) => notify(task, messageApi, downloadTask);
  store.addStatusChangeListener(handler);
  started = true;
  teardown = () => {
    store.removeStatusChangeListener(handler);
    started = false;
    teardown = () => {};
  };
  return teardown;
}
