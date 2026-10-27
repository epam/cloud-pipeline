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

import compareArrays from '../../utils/compareArrays';

// The shared async-task status vocabulary, so nothing else in this feature
// hard-codes a status string.

export const ASYNC_TASK_STATUSES = {
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  CANCEL_REQUESTED: 'CANCEL_REQUESTED',
  STOPPING: 'STOPPING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  EXPIRED: 'EXPIRED'
};

export const ACTIVE_STATUSES = [
  ASYNC_TASK_STATUSES.PENDING,
  ASYNC_TASK_STATUSES.RUNNING,
  ASYNC_TASK_STATUSES.CANCEL_REQUESTED,
  ASYNC_TASK_STATUSES.STOPPING
];

// The Downloads modal's default Status filter: the active statuses plus
// SUCCESS and FAILED. CANCELLED and EXPIRED are deliberately left out, so the
// table hides them until the user ticks them back on — both are terminal and
// neither is something anyone is waiting on.
//
// This used to be the `statuses` a background poll of the whole task list
// sent; there is no such poll any more (CurrentUserAsyncTasks watches the
// user's own queued tasks one by one, asking for ACTIVE_STATUSES when it needs
// a count). The list is unchanged, but its one remaining job is that default.
export const POLLED_STATUSES = [
  ...ACTIVE_STATUSES,
  ASYNC_TASK_STATUSES.SUCCESS,
  ASYNC_TASK_STATUSES.FAILED
];

export const ASYNC_TASK_TYPES = {
  DOWNLOAD_ARCHIVE: 'DOWNLOAD_ARCHIVE'
};

// The single place that upper-cases a task's status before comparing it
// against ASYNC_TASK_STATUSES. Exported so nothing outside this module
// re-derives its own case-insensitive comparison, or skips normalizing
// entirely, against the same strings.
export function normalizeStatus (status) {
  return status ? String(status).toUpperCase() : undefined;
}

function statusOf (task) {
  return normalizeStatus(task && task.status);
}

export function taskIsActive (task) {
  const status = statusOf(task);
  if (!status) {
    return false;
  }
  return ACTIVE_STATUSES.some(active => active.toUpperCase() === status);
}

export function taskIsFinished (task) {
  const status = statusOf(task);
  if (!status) {
    return false;
  }
  return status === ASYNC_TASK_STATUSES.SUCCESS || status === ASYNC_TASK_STATUSES.FAILED;
}

export function taskCanBeCancelled (task) {
  return taskIsActive(task);
}

export function taskArchiveIsAvailable (task) {
  return statusOf(task) === ASYNC_TASK_STATUSES.SUCCESS;
}

// The archive-name display fallback shared by the Downloads modal's list and
// its completion toast: the name the user chose, or the task id when it
// (or `params` itself) is missing.
export function archiveNameOf (task) {
  return (task && task.params && task.params.archiveName) || (task && task.id);
}

// The single "differs from default" comparison for the Status column's
// filter - shared by async-tasks-list.js (the header icon's highlight and
// `filteredValue`) and DownloadsManager.js (the "Reset filters" control's
// visibility), so the two can never drift apart the way the Owner column's
// equivalent once did. `compareArrays` is order-independent, which is what
// makes this correct regardless of checkbox order. `defaultStatuses`
// defaults to POLLED_STATUSES because that is every caller's own default
// today, but it is a parameter rather than a hard-coded reference so a
// future caller with a different default is not forced to fake one up.
export function statusesAreFiltered (statuses, defaultStatuses = POLLED_STATUSES) {
  return !compareArrays(statuses || [], defaultStatuses);
}

// The single "differs from default" comparison for the Owner column's
// filter, the owner equivalent of `statusesAreFiltered` above - shared by
// the same two call sites for the same reason. `defaultOwner` is the
// current user, known only once `/whoami` has resolved (DownloadsManager's
// own `defaultOwner` field): while it is still unknown, there is nothing to
// compare against yet, so this reports "not filtered" rather than treating
// an as-yet-unseeded `owner` as a deliberate change.
export function ownerIsFiltered (owner, defaultOwner) {
  if (defaultOwner === undefined) {
    return false;
  }
  return (owner || undefined) !== defaultOwner;
}
