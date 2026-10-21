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

// The exact `statuses` filter the background poll (CurrentUserAsyncTasks) sends:
// the active statuses plus SUCCESS and FAILED, the transition the completion
// notification announces. CANCELLED and EXPIRED are deliberately left out —
// both are already terminal and nothing watches for them (no cap counts them,
// no notification fires for them), so asking for them on a 5-second tick would
// be permanent weight for rows nobody reads. A later reader completing this
// list to "all eight statuses" would put that weight back — don't.
export const POLLED_STATUSES = [
  ...ACTIVE_STATUSES,
  ASYNC_TASK_STATUSES.SUCCESS,
  ASYNC_TASK_STATUSES.FAILED
];

export const ASYNC_TASK_TYPES = {
  DOWNLOAD_ARCHIVE: 'DOWNLOAD_ARCHIVE'
};

function statusOf (task) {
  return task && task.status ? String(task.status).toUpperCase() : undefined;
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
