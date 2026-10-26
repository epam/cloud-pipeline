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

import AsyncTaskFilter from './AsyncTaskFilter';
import AsyncTaskLoad from './AsyncTaskLoad';
import AsyncTaskCancel from './AsyncTaskCancel';
import AsyncTaskConsume from './AsyncTaskConsume';
import DataStorageDownloadArchive from '../dataStorage/DataStorageDownloadArchive';
import {
  ACTIVE_STATUSES,
  ASYNC_TASK_TYPES,
  taskIsActive,
  taskIsFinished
} from './statuses';
import {
  addEntry,
  entriesForUser,
  entryIsExpired,
  removeEntry
} from './active-tasks-storage';
import continuousFetch from '../../utils/continuous-fetch';
import whoAmI from '../user/WhoAmI';
import preferences from '../preferences/PreferencesLoad';

const POLL_INTERVAL_MS = 5000;

// Watches the current user's archive-download tasks one poll per task
// (`GET /async-task/{id}`), and announces each one when it finishes. There is
// no poll of the task list any more: that asked for every task the user had,
// every five seconds, for as long as the application was open, whether or not
// anything was waiting on the answer.
//
// The tasks being watched are kept in `localStorage`
// (./active-tasks-storage.js), so a task queued before a reload is still
// followed afterwards, and one that finished while the application was closed
// is still announced on the next visit - however long afterwards that is. An
// age cut-off exists for exactly that case but ships switched off; see
// `ACTIVE_TASK_TTL_DAYS` for what turning it on changes, and note what being
// off means here: a task that never reaches a terminal status is watched for
// as long as the tab is open, and again after every reload, since nothing
// else ends the watch. Since `localStorage` is per browser, start-up also
// asks the server once for the tasks that are active now and merges them in,
// so a user who queued archives on another machine is still told when those
// finish - see `restore`.
//
// What this store does NOT do:
//  - it does not know every task the user has. The `/downloads` page asks for
//    those itself, with its own filters and paging.
//  - it does not hold a cap count. `checkActiveTasksLimit` below counts the
//    user's active tasks against the server at the moment one is queued, which
//    is the only count that can be trusted, and nothing renders a hint from a
//    background figure any more.
// Nothing here is observable for that reason: its consumers call its methods
// and listen for finished tasks; none of them render its state. It is also no
// longer an `AsyncTaskFilter` subclass - it used to BE the list request - and
// instantiates that model where it needs it, like any other caller.
//
// A watched task is active when it starts being watched, both when it is
// queued and when it is restored from `localStorage`, which only ever holds
// tasks still being waited on. That is what makes a single finished response
// enough to announce one: there is no "previous status" to compare against,
// and a task that finished while the application was closed is exactly the
// case this has to get right.
class CurrentUserAsyncTasks {
  listeners = [];

  // id -> the continuousFetch handle polling it, for every task being watched.
  pollers = new Map();

  // ids whose next response must not be announced - see `resumePolling`.
  silenced = new Set();

  // ids already handled as terminal, so a second response for the same task
  // cannot announce it twice. Two responses for one task overlap more easily
  // than it looks: `continuousFetch`'s Page-Visibility `resume()` fetches
  // immediately with no in-flight check (utils/continuous-fetch/index.js), so
  // hiding and re-showing the tab while a tick is in flight gives two
  // concurrent `GET /async-task/{id}` - and each builds its own
  // `AsyncTaskLoad`, so nothing lower down de-duplicates them. One string per
  // finished task per session; `untrack` deliberately does NOT clear it.
  settled = new Set();

  // Whether `/downloads` currently has this tracker paused.
  pollPaused = false;

  /**
   * @param {{autoUpdate: boolean}} [options]
   */
  constructor (options = {}) {
    const {autoUpdate = true} = options;
    if (autoUpdate) {
      // Fire and forget, and never allowed to reject: this runs while the
      // application is starting up, from module scope, and a browser that
      // cannot reach `/whoami` must lose its notifications, not its session.
      this.restore().catch(error => {
        console.warn('CurrentUserAsyncTasks: could not restore watched tasks', error);
      });
    }
  }

  get maxUserTasks () {
    return preferences.storageDownloadArchiveConfig.maxUserTasks;
  }

  // The signed-in user's name, with one retry. `fetchIfNeededOrWait` clears
  // `_loadRequired` on its first attempt whether that attempt worked or not
  // (models/basic/Remote.js), so a `/whoami` that fails while the application
  // is starting up - which is exactly when this runs - would otherwise stay
  // failed for the rest of the session, and with it the whole restore. The
  // retry is a fresh `fetch()`, which does go to the server again.
  async currentUserName () {
    await whoAmI.fetchIfNeededOrWait();
    const firstAttempt = whoAmI.loaded ? (whoAmI.value || {}) : {};
    if (firstAttempt.userName) {
      return firstAttempt.userName;
    }
    await whoAmI.fetch();
    const retry = whoAmI.loaded ? (whoAmI.value || {}) : {};
    return retry.userName;
  }

  /**
   * Start-up, in two steps.
   *
   * First, what this browser was already watching: `localStorage` entries for
   * this user, expired ones dropped by `entriesForUser` with neither a request
   * nor a notification. These are polled even though the server may well
   * consider them finished - that is the point of persisting them, and the
   * only way a task that finished while the application was closed gets
   * announced at all.
   *
   * Then, one request for the tasks the server says are active now, merged in.
   * `localStorage` is per browser, so without this a user who queued three
   * archives on one machine and three on another would open the application
   * on a third and be told about none of them. The sync only ADDS: a task that
   * had already finished before this ran is not in the answer, and is
   * deliberately not announced - someone was told about it on the machine that
   * queued it, and re-announcing the user's history on every visit is what the
   * old list poll already got wrong once.
   *
   * @returns {Promise<void>}
   */
  async restore () {
    const userName = await this.currentUserName();
    if (!userName) {
      // Nothing is watched and nothing is deleted: which entries belong to
      // this user cannot be judged without knowing who they are, and guessing
      // would either poll a stranger's task or throw away someone's pending
      // notification. The session carries on - a task queued from here is
      // still watched, through `trackTask` - and the next page load tries
      // again.
      console.warn('CurrentUserAsyncTasks: no current user, not watching any stored task');
      return;
    }
    entriesForUser(userName).forEach(entry => this.startPolling(entry));
    let active = [];
    try {
      active = await this.fetchActiveTasks();
    } catch (error) {
      // The browser keeps watching whatever it already knew about; a failed
      // sync costs this visit's knowledge of other machines, nothing more.
      console.warn('CurrentUserAsyncTasks: could not sync active tasks', error);
      return;
    }
    // `trackTask` is idempotent: it replaces the stored entry and leaves an
    // existing poller alone, so the overlap between the two steps costs
    // nothing.
    active.forEach(task => this.trackTask(task));
  }

  /**
   * Starts watching a task - persisted first, so a reload a second later still
   * knows about it. `created` comes from the task the API answered with, not
   * from the clock here: the TTL is measured from when the task was made.
   *
   * @param {{id: string|number, created: string, owner: string}} task
   */
  trackTask (task) {
    if (!task || task.id === undefined || task.id === null) {
      return;
    }
    const entry = {
      id: String(task.id),
      created: task.created,
      owner: task.owner
    };
    // A task past the age cut-off is not watched at all, however it got here.
    // Inert while the cut-off ships switched off (`ACTIVE_TASK_TTL_DAYS`), and
    // kept so that turning it on needs no change here; a task just queued
    // could never be past it anyway, so this is for the ones arriving from the
    // start-up sync.
    if (entryIsExpired(entry)) {
      return;
    }
    addEntry(entry);
    this.startPolling(entry);
  }

  startPolling (entry) {
    const {id} = entry;
    if (this.pollers.has(id)) {
      return;
    }
    // One poller per task, each with its own identifier so continuousFetch's
    // registry keeps them apart (an identifier collision unregisters the
    // previous holder). `call` re-throws a network failure so the interval
    // backs off, the way every other poller in the application does.
    this.pollers.set(id, continuousFetch({
      call: () => this.pollTask(entry),
      intervalMS: POLL_INTERVAL_MS,
      identifier: `async-task-${id}`,
      continuous: !this.pollPaused
    }));
  }

  /**
   * One tick for one task.
   *
   * @param {{id: string, created: string}} entry
   * @returns {Promise<void>}
   */
  async pollTask (entry) {
    const {id} = entry;
    // Checked on every tick and not only at start-up, so that turning the age
    // cut-off on would end a task that never finishes rather than waiting for
    // the next reload. Inert while it ships switched off - which is precisely
    // why nothing ends such a task today.
    if (entryIsExpired(entry)) {
      this.untrack(id);
      return;
    }
    const request = new AsyncTaskLoad(id);
    await request.fetch();
    if (request.networkError) {
      // The server was not reached, so nothing is known about the task: keep
      // watching, and let continuousFetch widen the interval.
      throw new Error(request.networkError);
    }
    if (request.error) {
      // The server answered, and the answer was no - the id is unknown to it,
      // or no longer the caller's to see. Nothing will change that, so stop
      // rather than ask every five seconds for ever.
      console.warn(`async task ${id} is no longer available: ${request.error}`);
      this.untrack(id);
      return;
    }
    this.handleTask(entry, request.value || {});
  }

  handleTask (entry, task) {
    const {id} = entry;
    if (this.settled.has(id)) {
      // A second response for a task already dealt with - see `settled`. It
      // would otherwise announce the same finished archive twice, and could
      // also spend the `silenced` token meant for its twin.
      return;
    }
    // Consumed on the first response after a resume, whatever it says - see
    // `resumePolling`.
    const silenced = this.silenced.delete(id);
    if (!task.status) {
      // An OK response with nothing in it says nothing about the task. Keep
      // watching rather than treat "unknown" as "finished and gone".
      return;
    }
    if (taskIsActive(task)) {
      return;
    }
    // Everything below is terminal, so the task stops being watched either
    // way; the only question is whether it is announced.
    const announce = taskIsFinished(task) &&
      !this.pollPaused &&
      !silenced &&
      !entryIsExpired({...entry, created: entry.created || task.created});
    this.settled.add(id);
    this.untrack(id);
    if (announce) {
      this.notifyListeners(task);
    }
    // A CANCELLED or EXPIRED task is never announced: a cancellation the user
    // asked for needs no toast, and an expiry is not an event they are waiting
    // on. `taskIsFinished` is SUCCESS/FAILED only, which is what makes that
    // fall out rather than needing its own branch.
  }

  /**
   * Stops watching a task: its poller, its remembered status, and its entry in
   * `localStorage` all go together. Safe to call for an id that is not being
   * watched.
   *
   * @param {string|number} id
   */
  untrack (id) {
    const key = String(id);
    const poller = this.pollers.get(key);
    if (poller) {
      poller.stop();
      this.pollers.delete(key);
    }
    this.silenced.delete(key);
    removeEntry(key);
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

  // The archive-download tasks the user has in progress *right now*, asked of
  // the server: the only answer worth gating on, and the reason this store
  // keeps no running total of its own. A task queued in another browser or
  // another tab is in here and could not possibly be in anything local.
  //
  // Two callers, both one-off rather than polled: `checkActiveTasksLimit`
  // (below) counts them, and `restore` (above) starts watching them.
  //
  // `ACTIVE_STATUSES`, the four active ones and nothing else. No
  // `page`/`pageSize` - this needs every match, and the cap means there are at
  // most a handful.
  async fetchActiveTasks () {
    await whoAmI.fetchIfNeededOrWait();
    const {userName} = whoAmI.loaded ? (whoAmI.value || {}) : {};
    const body = {
      type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE,
      statuses: ACTIVE_STATUSES
    };
    // Sent when known, omitted otherwise, never sent as `undefined`. It is
    // ACL-guarded server-side anyway (ignored for a regular user, honoured for
    // an admin), but an admin must count their own tasks, not everyone's.
    if (userName) {
      body.owner = userName;
    }
    const request = new AsyncTaskFilter();
    await request.send(body);
    if (request.error) {
      throw new Error(request.error);
    }
    // `elements`, with the answer's `totalCount` deliberately ignored: this
    // request does not page, so the two agree on the ordinary path, and where
    // they disagree the elements are the safer number to trust. The count
    // below gates a download, so counting one task too many stops something
    // the user is entitled to do, while counting one too few only leaves the
    // refusal to the server, which enforces the quota anyway.
    const {elements = []} = request.value || {};
    // `taskIsActive` on top of the server's own `statuses` scope: belt and
    // braces, and the one place that would absorb a status arriving in a
    // different case (see `normalizeStatus` in ./statuses.js).
    return elements.filter(taskIsActive);
  }

  /**
   * @returns {Promise<number>}
   */
  async fetchActiveTasksCount () {
    const tasks = await this.fetchActiveTasks();
    return tasks.length;
  }

  /**
   * The cap check both entry points share - the "Download archive" click
   * (components/special/download-storage-archive.js) and
   * `startArchiveDownload` below, which is the last gate before the request
   * goes out and so cannot be skipped by a modal left open for ten minutes.
   *
   * Returns a result rather than throwing, because the two failure modes must
   * not be treated alike: being AT the cap blocks with a reason to show the
   * user, while a check that could not run at all must NOT block - the server
   * re-enforces this quota for real
   * (DataStorageDownloadArchiveManager.assertUserQuota), so a momentary
   * failure of this request is not allowed to be the thing that stops a
   * legitimate download.
   *
   * @returns {Promise<{allowed: boolean, reason: string|undefined}>}
   */
  async checkActiveTasksLimit () {
    await preferences.fetchIfNeededOrWait();
    let activeTasksCount;
    try {
      activeTasksCount = await this.fetchActiveTasksCount();
    } catch (error) {
      console.warn(
        `CurrentUserAsyncTasks: could not count active tasks (${error.message}); ` +
        'letting the server enforce the quota'
      );
      return {allowed: true};
    }
    if (activeTasksCount >= this.maxUserTasks) {
      return {
        allowed: false,
        // The measured count, not the cap: the two are equal in the ordinary
        // case, but an administrator lowering the preference leaves users
        // above it, and telling someone with five downloads that they have
        // three is worse than saying nothing.
        reason: `You already have ${activeTasksCount} archive downloads in progress, ` +
          `and the limit is ${this.maxUserTasks}. ` +
          'Wait for one to finish before starting another.'
      };
    }
    return {allowed: true};
  }

  /**
   * @param {string|number} storageId
   * @param {string[]} paths
   * @param {string} archiveName
   * @returns {Promise<*>}
   */
  async startArchiveDownload (storageId, paths, archiveName) {
    const {allowed, reason} = await this.checkActiveTasksLimit();
    if (!allowed) {
      throw new Error(reason);
    }
    const request = new DataStorageDownloadArchive(storageId);
    await request.send({paths, archiveName});
    if (request.error) {
      throw new Error(request.error);
    }
    // Queueing a task is what starts watching it: one more click, one more
    // entry in `localStorage`, one more poll - until it finishes.
    this.trackTask(request.value || {});
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
    // STOPPING for a while after this resolves. Nothing is untracked here: the
    // task's own poll sees CANCELLED when it arrives and stops then, with no
    // toast. A task that never reports a terminal status is watched until the
    // tab closes, and picked up again on the next load - with the age cut-off
    // switched off there is nothing that ends it (`ACTIVE_TASK_TTL_DAYS`).
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

  // `/downloads` (DownloadsManager.js) calls this on mount: while that page is
  // open it polls the same tasks itself, in one request for the whole table,
  // and a toast about a row the user is looking at is noise. Both halves
  // matter - the polls stop, and `pollPaused` keeps `handleTask` from
  // announcing anything that lands anyway (a task that finishes between the
  // pause and the last in-flight response).
  //
  // `setContinuous(false)`, not `pause()`: the poller's own `pause`/`resume`
  // are wired to the Page Visibility API (continuous-fetch/index.js's
  // `attachDocumentVisibilityHandlers`), which calls `resume()` on every
  // tab-visible event, so a tab switch while still on `/downloads` would
  // silently undo a `pause()`. `setContinuous` is not wired to anything, and
  // with `continuous` false `scheduleNext` returns early - so a tab return
  // costs at most the single stray fetch `resume()` makes directly, and never
  // restarts the cadence.
  pausePolling () {
    this.pollPaused = true;
    this.pollers.forEach(poller => poller.setContinuous(false));
  }

  // The counterpart, called on `/downloads`'s unmount. Each task's first
  // response after this is silenced: `setContinuous(true)` fetches
  // immediately, and that response is the one that would otherwise announce a
  // task the user has just watched finish in that page's own table.
  resumePolling () {
    this.pollPaused = false;
    this.pollers.forEach((poller, id) => {
      this.silenced.add(id);
      poller.setContinuous(true);
    });
  }
}

export {CurrentUserAsyncTasks};
export default new CurrentUserAsyncTasks();
