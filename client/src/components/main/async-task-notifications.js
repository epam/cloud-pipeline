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

import {message} from 'antd';
import asyncTasks from '../../models/asyncTask/CurrentUserAsyncTasks';
import {ASYNC_TASK_STATUSES} from '../../models/asyncTask/statuses';

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

function archiveNameOf (task) {
  return (task && task.params && task.params.archiveName) || (task && task.id);
}

function notify (task, messageApi) {
  const status = task && task.status
    ? String(task.status).toUpperCase()
    : undefined;
  const archiveName = archiveNameOf(task);
  if (status === ASYNC_TASK_STATUSES.SUCCESS) {
    messageApi.success(
      `Archive "${archiveName}" is ready. Download it from the Downloads page.`,
      5
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
 * @returns {function(): void} a teardown function
 */
export default function startAsyncTaskNotifications (store = asyncTasks, messageApi = message) {
  if (started) {
    return teardown;
  }
  if (!store || typeof store.addStatusChangeListener !== 'function') {
    return () => {};
  }
  const handler = (task) => notify(task, messageApi);
  store.addStatusChangeListener(handler);
  started = true;
  teardown = () => {
    store.removeStatusChangeListener(handler);
    started = false;
    teardown = () => {};
  };
  return teardown;
}
