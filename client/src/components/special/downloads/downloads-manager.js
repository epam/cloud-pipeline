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
import {inject, observer} from 'mobx-react';
import {message, Button} from 'antd';
import AsyncTaskFilter from '../../../models/asyncTask/AsyncTaskFilter';
import whoAmI from '../../../models/user/WhoAmI';
import {
  ASYNC_TASK_TYPES,
  POLLED_STATUSES,
  statusesAreFiltered,
  ownerIsFiltered
} from '../../../models/asyncTask/statuses';
import AsyncTasksList from './async-tasks-list';
import downloadArchiveTask from './download-archive-task';
import continuousFetch from '../../../utils/continuous-fetch';
import roleModel from '../../../utils/roleModel';
import styles from './downloads-manager.css';

// The request body's `pageSize`, and the pagination control's own
// `pageSize`, so the server's page and the control's page agree by
// construction.
const PAGE_SIZE = 10;

// Matches CurrentUserAsyncTasks's own interval - no shared constant, just two
// pollers that happen to agree; the other one is paused while this runs.
const POLL_INTERVAL_MS = 5000;

// Issues its own `/async-task/filter` request - the shared `asyncTasks` store
// holds no list of its own; it backs this component's `cancelTask`/
// `consumeTask` calls, and has its per-task watching paused while this is
// mounted (componentDidMount). `refetch()` below is the one path a filter
// change, a page change and a cancellation all take; the poller is the
// other.
@inject('asyncTasks', 'dataStorages', 'authenticatedUserInfo')
@observer
class DownloadsManager extends React.Component {
  state = {
    // Guards a double-click firing a second cancel/consume before the first
    // settles.
    actionPending: false,
    // `this.request` is a `RemotePost` whose `value` getter sits on a field
    // that is never `@observable`, so a later fetch never re-renders this
    // @observer on its own. Bumped (never read) on every outcome purely to
    // force that re-render - not unused, despite appearances.
    fetchTick: 0,
    // Not wired to `this.request.pending`: `send()` raises that on every
    // call, poll ticks included, which would flash this overlay every
    // POLL_INTERVAL_MS. Raised only for the first load or an explicit
    // refetch; a tick updates the rows silently through `fetchTick` instead.
    loading: false,
    // Request-scoped state, not a store (client/AGENTS.md). `statuses`
    // always has a real, visible default (POLLED_STATUSES - every status but
    // CANCELLED/EXPIRED) rather than being left unset, so the first request
    // already sends a concrete list.
    filters: {statuses: POLLED_STATUSES},
    // 1-based, matching the API, so nothing adds one at the request boundary.
    page: 1
  };

  constructor (props) {
    super(props);
    this.request = new AsyncTaskFilter();
  }

  // Not state: an admin who clears the owner filter must not be re-seeded on
  // the next tick, and state can't tell "never seeded" from "seeded, then
  // cleared".
  ownerSeeded = false;

  // The current user, once known - what `filtersChanged` compares against.
  defaultOwner = undefined;

  // Guards `loadTasks`'s unconditional `setState` against firing after
  // unmount: stopping the poller cancels a scheduled fetch, not one already
  // in flight when the modal closes.
  mounted = false;

  // Gates the first-load spinner to the poller's first (immediate) tick.
  loadedOnce = false;

  // The last page that actually loaded - what `loadedPage` falls back to.
  lastLoadedPage = {};

  componentDidMount () {
    this.mounted = true;
    // A distinct `identifier` keeps this out of the tracker's own per-task
    // registry; `fetchImmediate` (continuousFetch's default) covers the
    // mount fetch.
    this.poller = continuousFetch({
      call: this.pollTasks,
      intervalMS: POLL_INTERVAL_MS,
      identifier: 'downloads-modal-async-tasks'
    });
    // Pauses the tracker's per-task polling (and its toasts, which would
    // otherwise announce rows already on screen) - after this poller's own
    // immediate fetch, so something always watches. `typeof`-guarded so a
    // minimal test double without `pausePolling` does not throw.
    const {asyncTasks} = this.props;
    if (asyncTasks && typeof asyncTasks.pausePolling === 'function') {
      asyncTasks.pausePolling();
    }
  }

  componentWillUnmount () {
    this.shutdown();
  }

  // Idempotent: both React's own unmount and the Modal shell's
  // `componentWillUnmount` (downloads-modal.js) call this without
  // coordinating, since rc-dialog defers a closed modal's portal removal to a
  // leave-animation callback that never fires once the shell is gone - this
  // is the only synchronous signal there is.
  shutdown = () => {
    if (!this.mounted) {
      return;
    }
    this.mounted = false;
    // Resumes the tracker BEFORE stopping the poller, mirroring
    // componentDidMount. `resumePolling` re-fetches and silences that first
    // answer, so the toast fires only for what happens from here on, not for
    // what the user just watched finish here (see `silenced` in
    // CurrentUserAsyncTasks.js).
    const {asyncTasks} = this.props;
    if (asyncTasks && typeof asyncTasks.resumePolling === 'function') {
      asyncTasks.resumePolling();
    }
    if (this.poller) {
      this.poller.stop();
    }
  };

  // Not reset on success alone - de-duplication bookkeeping for the toast
  // below.
  lastErrorMessage = undefined;

  // Collapses repeated identical failures into one toast; a new message
  // still gets its own.
  reportError = (text) => {
    if (text && text !== this.lastErrorMessage) {
      this.lastErrorMessage = text;
      message.error(text, 5);
    }
  };

  // The tail of every fetch so far, so the next queues behind it. Never
  // rejects - `sendFilterRequest` catches everything.
  fetchQueue = Promise.resolve();

  // Serialises every fetch: `RemotePost.send`'s in-flight no-op would
  // otherwise drop one of an overlapping tick and refetch. Checking
  // `request.pending` would miss the same window, since `sendFilterRequest`
  // awaits `whoAmI.fetchIfNeededOrWait()` before `pending` is ever set.
  loadTasks = (options = {}) => {
    const run = () => this.sendFilterRequest(options);
    this.fetchQueue = this.fetchQueue.then(run, run);
    return this.fetchQueue;
  };

  // `explicit` separates a user-driven fetch from a poll tick - a plain tick
  // never passes it. It is not the whole story for the overlay, though: see
  // `ownsLoading` below, which raises it for the very first load too.
  sendFilterRequest = async ({explicit = false} = {}) => {
    // Whether THIS call owns the overlay, and so may clear it in `finally` -
    // otherwise a tick resolving mid-refetch would clear an overlay the
    // user's own request is still waiting on. `mounted` because `handleCancel`
    // awaits a store call first, so this can run after the modal has closed.
    const ownsLoading = (explicit || !this.loadedOnce) && this.mounted;
    if (ownsLoading) {
      this.setState({loading: true});
    }
    try {
      await whoAmI.fetchIfNeededOrWait();
      const {userName} = whoAmI.loaded ? (whoAmI.value || {}) : {};
      let {filters} = this.state;
      // Seeds the owner to the current user once `userName` is actually
      // known, rather than on "the first call" - so a slow/failing `/whoami`
      // retries on a later refresh. `ownerSeeded` then stays true, so a
      // deliberate later clear is never undone.
      if (!this.ownerSeeded && userName) {
        this.ownerSeeded = true;
        if (filters.owner === undefined) {
          filters = {...filters, owner: userName};
          this.setState({filters});
        }
      }
      if (userName) {
        this.defaultOwner = userName;
      }
      const body = {
        type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE,
        page: this.state.page,
        pageSize: PAGE_SIZE
      };
      // Added only when present, never an empty array/string, so a cleared
      // filter reads as "unfiltered" to the backend, not "match nothing".
      // Once seeded, a cleared owner must NOT fall back to `userName` - that
      // only applies before the first seed, in the `if` above.
      if (filters.owner) {
        body.owner = filters.owner;
      }
      if (Array.isArray(filters.statuses) && filters.statuses.length > 0) {
        body.statuses = filters.statuses;
      }
      await this.request.send(body);
      if (this.request.error) {
        this.reportError(this.request.error);
      } else {
        this.lastErrorMessage = undefined;
        this.lastLoadedPage = this.request.value || {};
      }
    } catch (error) {
      this.reportError(error.message);
    } finally {
      this.loadedOnce = true;
      // `loading` clears only if this call raised it; `fetchTick` always
      // bumps (see its own comment above). `this.mounted`-guarded since a
      // call still in flight at unmount must not `setState` afterwards.
      if (this.mounted) {
        this.setState(previous => ({
          fetchTick: previous.fetchTick + 1,
          loading: ownsLoading ? false : previous.loading
        }));
      }
    }
  };

  // Re-thrown because `continuousFetch` backs its interval off from whether
  // `call` rejects, and `sendFilterRequest` itself swallows failures into a
  // toast - without this a failing endpoint would be re-polled every 5
  // seconds forever instead of backing off like every other poller.
  pollTasks = async () => {
    await this.loadTasks();
    const error = this.request.networkError || this.request.error;
    if (error) {
      throw new Error(error);
    }
  };

  // The one path every user-driven refetch takes. `poller.reset()` re-spaces
  // the interval so a tick doesn't land right behind it; returns the promise
  // so `handleCancel` can await it.
  refetch = () => {
    if (this.poller) {
      this.poller.reset();
    }
    return this.loadTasks({explicit: true});
  };

  // `RemotePost.update` clears `loaded` on failure, so without this fallback
  // a single failed poll would blank the table every five seconds instead of
  // just reporting through `reportError` and leaving the rows be.
  get loadedPage () {
    if (!this.request || !this.request.loaded) {
      return this.lastLoadedPage;
    }
    return this.request.value || {};
  }

  // The server already sorts newest-first unconditionally, with no sort
  // parameter in the request - passed through verbatim, since a client-side
  // re-sort would disagree with the server the moment the orderings differ
  // across pages.
  get tasks () {
    return this.loadedPage.elements || [];
  }

  // How many tasks match in total, not how many this page carries - what
  // lets the pagination control show a real page count.
  get totalCount () {
    return this.loadedPage.totalCount || 0;
  }

  // `dataStorages` is the shared Provider singleton; nothing here re-fetches
  // it. A plain object (not a Map) and a plain getter (not `@computed`): it
  // travels to async-tasks-list.js as plain data, keeping that file free of
  // `@inject`/model imports - this class's own `@observer` is what re-renders
  // the column once `dataStorages` resolves.
  get storageNamesById () {
    const {dataStorages} = this.props;
    if (!dataStorages || !dataStorages.loaded) {
      return {};
    }
    return (dataStorages.value || []).reduce((result, storage) => ({
      ...result,
      [storage.id]: storage.name
    }), {});
  }

  handleCancel = async (task) => {
    const {asyncTasks} = this.props;
    this.setState({actionPending: true});
    try {
      await asyncTasks.cancelTask(task.id);
      // The server only asks a task to stop, so it may still be
      // CANCEL_REQUESTED/STOPPING once this resolves.
      message.info('Cancellation requested', 5);
    } catch (error) {
      message.error(error.message, 5);
      this.setState({actionPending: false});
      return;
    }
    await this.refetch();
    this.setState({actionPending: false});
  };

  handleDownload = async (task) => {
    const {asyncTasks} = this.props;
    this.setState({actionPending: true});
    try {
      await downloadArchiveTask(asyncTasks, task);
    } finally {
      this.setState({actionPending: false});
    }
  };

  // Shared by both filter sources below: a falsy/empty value deletes its key
  // rather than storing `[]`/`undefined` - the same rule `sendFilterRequest`
  // applies to the request body. Refetches from `setState`'s callback so the
  // new filter is live at once, not on the next tick.
  setFilter = (key, value) => {
    const next = {...this.state.filters};
    const isEmpty = !value || (Array.isArray(value) && value.length === 0);
    if (isEmpty) {
      delete next[key];
    } else {
      next[key] = value;
    }
    // Marks the owner seeded the moment it's touched at all - closes a race
    // `sendFilterRequest`'s own guard can't: clearing it while the very
    // first load still awaits `/whoami` would otherwise get seeded right
    // back once that resolves.
    if (key === 'owner') {
      this.ownerSeeded = true;
    }
    this.setState({filters: next, page: 1}, () => {
      this.refetch();
    });
  };

  // async-tasks-list.js's Owner column filter dropdown (admin-only).
  handleOwnerFilterChange = (owner) => this.setFilter('owner', owner);

  // async-tasks-list.js's Status column filter dropdown.
  handleStatusesFilterChange = (statuses) => this.setFilter('statuses', statuses);

  // A no-op below page 1, even though antd's own pagination will not ask for
  // one. antd hands `(page, pageSize)`; the size is fixed, so the second
  // argument is ignored.
  handlePageChange = (page) => {
    const nextPage = Math.max(1, page);
    this.setState({page: nextPage}, () => {
      this.refetch();
    });
  };

  // Whether either filter differs from its default. Reuses the same two
  // comparisons (models/asyncTask/statuses.js) async-tasks-list.js's own
  // filter-icon highlight is built from, so the two can't drift apart. Also
  // what gates the heading row itself in `render()` below, now that "Reset
  // filters" is its only content.
  get filtersChanged () {
    const {filters} = this.state;
    return statusesAreFiltered(filters.statuses, POLLED_STATUSES) ||
      ownerIsFiltered(filters.owner, this.defaultOwner);
  }

  // Leaves `filters.owner` alone when `defaultOwner` isn't known yet (a
  // statuses-only change can show the Reset filters button, via
  // `filtersChanged` above, before the first `/whoami` resolves), rather
  // than clobbering it with a default not actually known.
  handleResetFilters = () => {
    const filters = {statuses: POLLED_STATUSES};
    if (this.defaultOwner !== undefined) {
      filters.owner = this.defaultOwner;
    } else if (this.state.filters.owner !== undefined) {
      filters.owner = this.state.filters.owner;
    }
    this.setState({filters, page: 1}, () => {
      this.refetch();
    });
  };

  // The Owner column (async-tasks-list.js) is admin-only and omitted, not
  // merely hidden, for anyone else. A `ROLE_STORAGE_ADMIN` also sees it, even
  // though the server still restricts what it shows:
  // `AsyncTaskApiService.restrictToCaller` (origin/java21_issue_4570_
  // download_archive, not this branch) overwrites `owner` with the caller's
  // own name unless they are a full `ROLE_ADMIN` - a server-side limitation,
  // not a bug here.
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (!authenticatedUserInfo || !authenticatedUserInfo.loaded || !authenticatedUserInfo.value) {
      return false;
    }
    const user = authenticatedUserInfo.value;
    return !!(user.admin || roleModel.userIs.storageAdmin(user));
  }

  // No cap hint here or anywhere else: nothing counts active tasks any more,
  // and nothing can be queued from this modal. The cap is checked against the
  // server where an archive is actually requested (download-storage-archive.js).
  render () {
    return (
      <div className={styles.container}>
        {
          this.filtersChanged ? (
            <div className={styles.headingRow}>
              <div className={styles.headingRowButtons}>
                <Button
                  id="downloads-reset-filters"
                  size="small"
                  disabled={this.state.actionPending}
                  onClick={this.handleResetFilters}
                >
                  {'Reset filters'}
                </Button>
              </div>
            </div>
          ) : null
        }
        <AsyncTasksList
          tasks={this.tasks}
          pending={this.state.loading}
          onCancel={this.handleCancel}
          onDownload={this.handleDownload}
          disabled={this.state.actionPending}
          isAdmin={this.isAdmin}
          ownerFilter={this.state.filters.owner}
          onOwnerFilterChange={this.handleOwnerFilterChange}
          defaultOwner={this.defaultOwner}
          statusesFilter={this.state.filters.statuses}
          onStatusesFilterChange={this.handleStatusesFilterChange}
          page={this.state.page}
          pageSize={PAGE_SIZE}
          total={this.totalCount}
          onPageChange={this.handlePageChange}
          storageNamesById={this.storageNamesById}
        />
      </div>
    );
  }
}

export default DownloadsManager;
