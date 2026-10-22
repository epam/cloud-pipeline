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

import {action, computed, observable} from 'mobx';
import AsyncTaskFilter from './AsyncTaskFilter';
import AsyncTaskCancel from './AsyncTaskCancel';
import AsyncTaskConsume from './AsyncTaskConsume';
import DataStorageDownloadArchive from '../dataStorage/DataStorageDownloadArchive';
import {ASYNC_TASK_TYPES, POLLED_STATUSES, taskIsActive, taskIsFinished} from './statuses';
import continuousFetch from '../../utils/continuous-fetch';
import whoAmI from '../user/WhoAmI';
import preferences from '../preferences/PreferencesLoad';

const POLL_INTERVAL_MS = 5000;

// The global, session-long poll of the current user's archive-download tasks.
// It is deliberately scoped to POLLED_STATUSES — the four active statuses plus
// SUCCESS and FAILED, and never CANCELLED or EXPIRED. See ./statuses.js for why:
// nothing here, and nothing this store feeds, ever waits on either of those two.
// The /downloads page does NOT read this store's `tasks` — it issues its own
// unfiltered request, because this poll can never surface a CANCELLED or
// EXPIRED row. This store still backs that page's cancel/consume actions and
// its "too many active downloads" cap.
class CurrentUserAsyncTasks extends AsyncTaskFilter {
  @observable _tasks = [];

  listeners = [];

  poller;

  /**
   * @param {{autoUpdate: boolean}} [options]
   */
  constructor (options = {}) {
    super();
    const {autoUpdate = true} = options;
    if (autoUpdate) {
      this.poller = continuousFetch({
        request: this,
        intervalMS: POLL_INTERVAL_MS,
        identifier: 'current-user-async-tasks'
      });
    }
  }

  @computed
  get tasks () {
    return this._tasks || [];
  }

  @computed
  get activeTasks () {
    return this.tasks.filter(taskIsActive);
  }

  @computed
  get maxUserTasks () {
    return preferences.storageDownloadArchiveConfig.maxUserTasks;
  }

  @computed
  get activeTasksLimitReached () {
    return this.activeTasks.length >= this.maxUserTasks;
  }

  async fetch () {
    await whoAmI.fetchIfNeededOrWait();
    const {userName} = whoAmI.loaded ? (whoAmI.value || {}) : {};
    const body = {
      type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE,
      // The four active statuses plus SUCCESS and FAILED — see the comment on
      // POLLED_STATUSES in ./statuses.js for why CANCELLED and EXPIRED are not
      // in this list, and do not widen it to "fix" that.
      statuses: POLLED_STATUSES
    };
    // `owner` is ACL-guarded server-side (ignored for a regular user, honoured
    // for an admin), so it is sent explicitly whenever it is known, and
    // omitted — not sent as `undefined` — otherwise.
    if (userName) {
      body.owner = userName;
    }
    // Deliberately no `page`/`pageSize`: see the comment above POLLED_STATUSES's
    // usage in the plan this store implements — omitting both is what fetches
    // every matching task without paging.
    await super.send(body);
  }

  @action
  update (value) {
    const previous = new Map((this._tasks || []).map(task => [task.id, task.status]));
    super.update(value);
    if (this.loaded) {
      this._tasks = Array.isArray(this.value) ? this.value : [];
      const transitioned = this._tasks.filter(task => {
        if (!task || !previous.has(task.id)) {
          // A task seen for the first time is not a transition, even if it is
          // already finished — otherwise every page load would re-announce
          // the user's whole history.
          return false;
        }
        return taskIsActive({status: previous.get(task.id)}) && taskIsFinished(task);
      });
      transitioned.forEach(task => this.notifyListeners(task));
    }
    // A task that drops out of this response (e.g. CANCEL_REQUESTED ->
    // CANCELLED, which POLLED_STATUSES excludes) fires nothing. That is
    // correct: a cancellation the user asked for needs no toast, and it
    // simply stops counting against the cap once it leaves `_tasks`.
  }

  notifyListeners (task) {
    (this.listeners || [])
      .filter(listener => typeof listener === 'function')
      .forEach(listener => {
        try {
          listener(task);
        } catch (e) {
          console.warn('CurrentUserAsyncTasks: a status-change listener threw', e);
        }
      });
  }

  addStatusChangeListener = (listener) => {
    this.removeStatusChangeListener(listener);
    this.listeners.push(listener);
  };

  removeStatusChangeListener = (listener) => {
    this.listeners = this.listeners.filter(aListener => aListener !== listener);
  };

  /**
   * @param {string|number} storageId
   * @param {string[]} paths
   * @param {string} archiveName
   * @returns {Promise<*>}
   */
  async startArchiveDownload (storageId, paths, archiveName) {
    await preferences.fetchIfNeededOrWait();
    // UX only: the server enforces this quota for real
    // (DataStorageDownloadArchiveManager.assertUserQuota).
    if (this.activeTasksLimitReached) {
      throw new Error(
        `You already have ${this.maxUserTasks} archive downloads in progress. ` +
        'Wait for one to finish before starting another.'
      );
    }
    const request = new DataStorageDownloadArchive(storageId);
    await request.send({paths, archiveName});
    if (request.error) {
      throw new Error(request.error);
    }
    await this.refresh();
    return request.value;
  }

  /**
   * @param {string|number} id
   * @returns {Promise<*>}
   */
  async cancelTask (id) {
    const request = new AsyncTaskCancel(id);
    await request.send({});
    if (request.error) {
      throw new Error(request.error);
    }
    // The server cancels an untaken task at once but only *asks* a running one
    // to stop, so the status may legitimately sit at CANCEL_REQUESTED or
    // STOPPING for a while after this resolves.
    await this.refresh();
    return request.value;
  }

  /**
   * @param {string|number} id
   * @returns {Promise<string|undefined>}
   */
  async consumeTask (id) {
    const request = new AsyncTaskConsume(id);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    const {result} = request.value || {};
    return (result || {}).url;
  }

  // Kicks a refresh instead of waiting out the 5-second poll interval, so a
  // just-queued or just-cancelled task appears sooner rather than later. With a
  // poller this is fire-and-forget — continuousFetch's own `fetch()` does not
  // return a promise that resolves once the request completes — so the caller
  // only gets a guaranteed-fresh `tasks`/`activeTasks` when there is no poller
  // (every unit test's case, via `{autoUpdate: false}`) and this awaits
  // `this.fetch()` directly.
  refresh () {
    if (this.poller) {
      this.poller.fetch();
      return undefined;
    }
    return this.fetch();
  }

  // `/downloads` (DownloadsManager.js) calls this on mount, while it runs its
  // own, broader poll of the same tasks: this store's poll would be pure
  // duplication for as long as that page is open. `setContinuous(false)`, not
  // `pause()` - the poller's own `pause`/`resume` are wired to the Page
  // Visibility API (continuous-fetch/index.js's `attachDocumentVisibilityHandlers`),
  // which calls `resume()` on every tab-visible event, so a tab switch while
  // still on `/downloads` would silently undo a `pause()`. `setContinuous`
  // has no such wiring. The `if (this.poller)` guard is load-bearing, not
  // defensive noise: an instance built with `{autoUpdate: false}` - every
  // unit test - has no poller, so this must be a safe no-op there.
  pausePolling () {
    if (this.poller) {
      this.poller.setContinuous(false);
    }
  }

  // The counterpart to pausePolling() above, called on `/downloads`'s unmount.
  // Same `setContinuous`, same reasoning, same no-poller guard.
  resumePolling () {
    if (this.poller) {
      this.poller.setContinuous(true);
    }
  }
}

export {CurrentUserAsyncTasks};
export default new CurrentUserAsyncTasks();
