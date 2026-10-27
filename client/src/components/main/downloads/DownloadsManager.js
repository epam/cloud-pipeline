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
import classNames from 'classnames';
import {message, Button, Icon} from 'antd';
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
import styles from './DownloadsManager.css';

// A page's worth of rows per request: the request body's `pageSize`, and the
// `pageSize` the pagination control is given, so the page the server cuts and
// the page the control counts in are the same size by construction.
const PAGE_SIZE = 15;

// Matches CurrentUserAsyncTasks's own POLL_INTERVAL_MS: there is no shared
// behaviour to factor out, just two pollers that happen to agree on a
// cadence - and while this page is mounted the other one is paused (see
// componentDidMount), so its cadence stops rather than running alongside
// this one.
const POLL_INTERVAL_MS = 5000;

// This page issues its OWN `/async-task/filter` request: it is the one view
// of the whole task list, with its own filters and paging, and the shared
// `asyncTasks` store holds no list at all - it watches the user's own queued
// tasks one by one, to announce them (models/asyncTask/
// CurrentUserAsyncTasks.js). The injected store backs this page's
// `cancelTask` and `consumeTask`, and has its watching paused while the page
// is open - see componentDidMount.
//
// The table refetches two ways: its own poller, every POLL_INTERVAL_MS while
// the page is mounted, and `refetch()` below - the single path a manual
// Refresh, a filter change, a page change and a cancellation all take, so
// none of them has to wait out the interval.
@inject('asyncTasks', 'dataStorages', 'authenticatedUserInfo')
@observer
export default class DownloadsManager extends React.Component {
  state = {
    // Guards against a double-click firing a second cancel/consume request
    // before the first one's `await` has resolved.
    actionPending: false,
    // Bumped, never read, on every `loadTasks` outcome - see the comment in
    // `loadTasks`'s `finally` block for why: `this.request` is a `RemotePost`
    // (models/basic/RemotePost.js), whose `value` is a plain getter backed by
    // a field that is never `@observable`, so MobX never notices a fetch
    // updating it and this @observer component never re-renders from it on
    // its own, no matter what triggered that fetch. This counter's only job
    // is forcing that re-render so `render()` re-reads `this.request.value`
    // on every fetch, not just the first. Do not "clean up" this field for
    // being unused in `render()` - that is the point.
    fetchTick: 0,
    // The table's loading overlay, and the reason `pending` is NOT wired
    // straight to `this.request.pending` any more: `send()` sets that on
    // every call, poll ticks included, so the overlay would flash every
    // POLL_INTERVAL_MS for as long as the page stays open. Raised for the
    // first load and for an explicit refetch (Refresh, a filter, a page, a
    // cancellation) - never for a poll tick, which updates the rows silently
    // through `fetchTick` above.
    loading: false,
    // Page-local UI state, not a store: client/AGENTS.md calls for anything
    // request-scoped to be instantiated in the component rather than added to
    // the global Provider. Shape: {owner, statuses}; `owner` is optional and
    // `statuses` is always populated (see `POLLED_STATUSES` below). Both come
    // from the two filterable columns in async-tasks-list.js - the Owner
    // column (admin-only) and the Status column's own filter dropdown.
    // `statuses` defaults to POLLED_STATUSES (every status except CANCELLED
    // and EXPIRED) rather than being left unset: unlike `owner`, this has no
    // async dependency to wait on, so the very first `loadTasks()` call
    // already sends a concrete list instead of omitting the key. This is a
    // real, visible default - the table hides CANCELLED/EXPIRED tasks until
    // the user ticks them back on in the Status filter.
    filters: {statuses: POLLED_STATUSES},
    // 1-based, matching the API (`AsyncTaskFilterVO`: "Counted from 1.
    // Ignored unless pageSize is set."). Keeping it 1-based here means
    // nothing has to add one at the request boundary.
    page: 1
  };

  constructor (props) {
    super(props);
    this.request = new AsyncTaskFilter();
  }

  // Seeds `filters.owner` to the current user on the first load only - see the
  // comment inside `loadTasks`. A plain instance field, not state: an admin
  // who deliberately clears the owner filter must not be re-seeded on the
  // next poll tick, and state would offer no way to tell "never seeded" apart
  // from "seeded, then cleared".
  ownerSeeded = false;

  // The current user's username, once `loadTasks` has learned it - the
  // "default" `filtersChanged` (below) compares the committed owner against.
  // `undefined` until the first successful `/whoami`, which is also why the
  // "Reset filters" control (`render`) can briefly fail to appear on a very
  // slow first load even with a non-default owner already set - an edge
  // case, not a bug: there is nothing to reset *to* until this is known.
  defaultOwner = undefined;

  // Guards `loadTasks`'s now-unconditional `setState` call (see its `finally`
  // block) against firing after unmount: `componentWillUnmount` stops this
  // page's own poller, but that only cancels a *scheduled* fetch, not one
  // already in flight - a poll tick, a manual Refresh, a filter/page change
  // or a cancellation can still be awaiting `this.request.send(body)` at the
  // exact instant the page unmounts, and its `finally` still runs once that
  // resolves. Before `fetchTick` existed, the same `setState` call was gated
  // to fire once ever, so this race existed only around the very first load;
  // bumping `fetchTick` on every call widens the window to every one of them,
  // so it earns its own guard.
  mounted = false;

  // Whether a response has ever landed. Gates the first-load spinner, so the
  // poller's very first tick (its immediate one, from the constructor) shows
  // it even though no tick after that does - see `sendFilterRequest`. A plain
  // field, not state: nothing renders from it directly.
  loadedOnce = false;

  // The last response that actually succeeded - `{elements, totalCount}`,
  // what the table keeps showing when a later fetch fails. See the `tasks`
  // and `totalCount` getters.
  lastLoadedPage = {};

  componentDidMount () {
    this.mounted = true;
    // Polls this page's own request while it is mounted, so a task that
    // finishes while the page is open updates its row instead of sitting at
    // PENDING until something asks again. A distinct identifier from the
    // tracker's per-task pollers ('async-task-<id>') keeps them from
    // colliding in continuousFetch's registry. `fetchImmediate`
    // (continuousFetch's default) covers the initial mount fetch, so there is
    // no separate `loadTasks()` call here.
    this.poller = continuousFetch({
      call: this.pollTasks,
      intervalMS: POLL_INTERVAL_MS,
      identifier: 'downloads-page-async-tasks'
    });
    // One request for this whole table covers every task the tracker watches
    // one by one (CurrentUserAsyncTasks.js), so its polls stop for as long as
    // this page stays mounted - and with them the toasts, which would be
    // announcing rows the user is looking at. Started AFTER this page's own
    // poller, deliberately: that one fetches immediately on construction, so
    // at no instant is nothing watching these tasks. Guarded for a store
    // double with no `pausePolling` method (a minimal test double), so
    // rendering with one does not throw.
    const {asyncTasks} = this.props;
    if (asyncTasks && typeof asyncTasks.pausePolling === 'function') {
      asyncTasks.pausePolling();
    }
  }

  componentWillUnmount () {
    this.mounted = false;
    // Resumes the tracker BEFORE stopping this page's own poller - the mirror
    // of componentDidMount's ordering, so there is still never an instant with
    // nothing watching. `resumePolling` re-fetches each watched task at once
    // and silences that first answer, so the toast is armed for what happens
    // from here on and deliberately NOT for what finished while the page was
    // open - see `silenced` in CurrentUserAsyncTasks.js, without which
    // leaving this page would fire a toast for every task the user just
    // watched finish in the table.
    const {asyncTasks} = this.props;
    if (asyncTasks && typeof asyncTasks.resumePolling === 'function') {
      asyncTasks.resumePolling();
    }
    if (this.poller) {
      this.poller.stop();
    }
  }

  // Not observable, and deliberately not reset between calls on success alone
  // (see `reportError`): plain bookkeeping for de-duplicating the toast below.
  lastErrorMessage = undefined;

  // De-dupes against a user clicking Refresh repeatedly into the exact same
  // failure. A genuinely new error (a different message, or the first one)
  // still gets its own toast.
  reportError = (text) => {
    if (text && text !== this.lastErrorMessage) {
      this.lastErrorMessage = text;
      message.error(text, 5);
    }
  };

  // The tail of every fetch this page has made, so the next one can queue
  // behind it - see `loadTasks`. Never rejects: `sendFilterRequest` catches
  // everything it can fail with.
  fetchQueue = Promise.resolve();

  // Serialises every fetch, which is what keeps `RemotePost.send`'s in-flight
  // no-op (src/models/basic/RemotePost.js) from silently dropping one: a poll
  // tick and a user-driven refetch would otherwise overlap, and the second of
  // the two would return without sending anything at all. Checking
  // `request.pending` instead would not do it - `sendFilterRequest` awaits
  // `whoAmI.fetchIfNeededOrWait()` before it ever calls `send`, so there is a
  // window in which a fetch is under way with nothing pending yet.
  //
  // The queue is one deep in practice (one poller, and user actions one at a
  // time), and nothing waits on a tick other than the fetch right behind it.
  loadTasks = (options = {}) => {
    const run = () => this.sendFilterRequest(options);
    this.fetchQueue = this.fetchQueue.then(run, run);
    return this.fetchQueue;
  };

  // `explicit` is what separates a user-driven fetch from a poll tick: it
  // raises the table's loading overlay (`state.loading`), which a tick must
  // never do - see that field's comment. Everything user-driven comes through
  // `refetch()` below; the poller calls `pollTasks`, which passes nothing.
  sendFilterRequest = async ({explicit = false} = {}) => {
    // Whether THIS call owns the overlay: it raises it, and so it is also the
    // only one allowed to lower it again in the `finally` block - otherwise a
    // poll tick resolving mid-refetch would clear an overlay the user's own
    // request is still waiting behind. `this.mounted` for the same reason the
    // `finally` block checks it: `handleCancel` awaits a store call before it
    // refetches, so this can be reached after the page is gone.
    const ownsLoading = (explicit || !this.loadedOnce) && this.mounted;
    if (ownsLoading) {
      this.setState({loading: true});
    }
    try {
      await whoAmI.fetchIfNeededOrWait();
      const {userName} = whoAmI.loaded ? (whoAmI.value || {}) : {};
      let {filters} = this.state;
      // Seeds `filters.owner` to the current user, once, the first time a
      // userName is actually known - the explicit "current user by default"
      // ask. Gating on `userName` rather than merely "the first call" lets a
      // slow or momentarily failing `/whoami` retry on a later refresh
      // instead of seeding forever skipped. Once seeded, `this.ownerSeeded`
      // stays true for the rest of the session, so an admin who deliberately
      // clears the owner filter afterwards is never re-seeded by a later one.
      if (!this.ownerSeeded && userName) {
        this.ownerSeeded = true;
        if (filters.owner === undefined) {
          filters = {...filters, owner: userName};
          this.setState({filters});
        }
      }
      // A plain instance field, not state: `filtersChanged` (below) reads it
      // to decide the "Reset filters" control's visibility, and that control
      // must never flicker because of a render that happens to run before
      // this field updates. Kept current on every call, not just the first,
      // since `userName` cannot itself change mid-session but this keeps the
      // two sources (this field and the seeding above) from drifting apart
      // for no reason.
      if (userName) {
        this.defaultOwner = userName;
      }
      const body = {
        type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE,
        // This page's request always pages - it is the only view of the whole
        // list: `page` is 1-based state, `pageSize` is fixed. See the comment
        // above this class for why `owner`/`statuses` are conditional while
        // these two are not.
        page: this.state.page,
        pageSize: PAGE_SIZE
      };
      // `owner`/`statuses` are added only when the filter bar/column has a
      // value for them - never an empty array, never an empty string - so a
      // cleared filter reads to the backend as "unfiltered" rather than
      // "match nothing". In particular, once the owner has been seeded
      // above, a cleared `filters.owner` must NOT fall back to `userName`:
      // that fallback only ever applies before the first seed, which is why
      // it lives inside the `if` above rather than here.
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
        // The page to keep showing if a later fetch fails - see `tasks` and
        // `totalCount`.
        this.lastLoadedPage = this.request.value || {};
      }
    } catch (error) {
      this.reportError(error.message);
    } finally {
      this.loadedOnce = true;
      // `fetchTick` is bumped unconditionally on every call, success or
      // failure, whether a poll tick or an explicit refetch fired it: see its
      // comment in the state initialiser for why - it is the only thing
      // forcing this @observer component to re-render on a later fetch, since
      // `this.request.value` is not itself MobX-reactive. `loading` is only
      // cleared by the call that raised it (`ownsLoading` above), but it is
      // cleared whether that call succeeded or failed, so a failed refetch
      // cannot leave the overlay up. Guarded by `this.mounted` - see that
      // field's own comment - since this runs on every call to this method,
      // not just the first, widening the window in which an in-flight call's
      // completion can land after unmount.
      if (this.mounted) {
        this.setState(previous => ({
          fetchTick: previous.fetchTick + 1,
          loading: ownsLoading ? false : previous.loading
        }));
      }
    }
  };

  // What the poller calls, rather than `loadTasks` directly: `continuousFetch`
  // decides whether to back its interval off from whether `call` rejects
  // (utils/continuous-fetch/index.js), and `sendFilterRequest` deliberately
  // swallows every failure to turn it into a toast. Without this re-throw a
  // 500-ing or unreachable endpoint would be re-requested every five seconds
  // for as long as the page stayed open, instead of the interval growing the
  // way it does for every other poller in the app.
  pollTasks = async () => {
    await this.loadTasks();
    const error = this.request.networkError || this.request.error;
    if (error) {
      throw new Error(error);
    }
  };

  // The single path every user-driven refetch takes - a manual Refresh, a
  // filter change, a page change, a cancellation. `poller.reset()` re-spaces
  // the interval from now, so a tick does not land right behind this fetch.
  // Not `poller.fetch()`, which is `doSingleFetch(true)` and returns undefined
  // rather than a promise: `handleCancel` has to await the refetch before it
  // re-enables the row's buttons. Queueing - so that an overlapping tick
  // cannot swallow this - is `loadTasks`'s job, for every caller.
  refetch = () => {
    if (this.poller) {
      this.poller.reset();
    }
    return this.loadTasks({explicit: true});
  };

  // The response's `{elements, totalCount}` - named for the page of results
  // it is, and not to be confused with `state.page`, the page NUMBER asked
  // for. Both the rows and the total come from here, so they can never
  // disagree about which response they describe.
  //
  // `RemotePost.update` sets `loaded` back to false on a failure, so without
  // the fallback to the last successful page a single failed poll would blank
  // the whole table until a later one succeeded - every five seconds,
  // unprompted, where before polling only an explicit click could do that.
  // The error is still reported, by `reportError`; the rows just stay as they
  // were.
  get loadedPage () {
    if (!this.request || !this.request.loaded) {
      return this.lastLoadedPage;
    }
    return this.request.value || {};
  }

  // The server already sorts every response newest-first, unconditionally, in
  // both its paged and unpaged branches (`AsyncTaskManager.loadByFilter` on
  // origin/java21_issue_4570_download_archive), with no sort parameter in the
  // request. Passed through verbatim: a client-side re-sort would only
  // reorder the current page among itself, which becomes an inconsistency
  // across pages the moment anything makes the two orderings differ.
  get tasks () {
    return this.loadedPage.elements || [];
  }

  // How many tasks the filter matches in total, which is what the server
  // answers in `totalCount` - not how many this page carries. That is what
  // lets the pagination below show a real number of pages instead of
  // guessing from whether this page came back full.
  get totalCount () {
    return this.loadedPage.totalCount || 0;
  }

  // Maps a task's numeric storage id to its name, for the Storage column.
  // `dataStorages` is the shared singleton already in Root.js's Provider
  // (one GET /datastorage/loadAll for every storage the caller can see, see
  // models/dataStorage/DataStorages.js) - nothing here re-fetches it. A
  // plain object, not a Map, so it travels to async-tasks-list.js as plain
  // data and that file stays free of @inject and model imports. A plain
  // getter rather than @computed: `tasks`/`totalCount` above are plain
  // getters too, and nothing else in this file imports `computed` from
  // mobx - the class is already @observer, which is what makes the column
  // re-render once `dataStorages` resolves.
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
      // The server only *asks* a running task to stop, so it may legitimately
      // still be CANCEL_REQUESTED/STOPPING once this resolves.
      message.info('Cancellation requested', 5);
    } catch (error) {
      message.error(error.message, 5);
      this.setState({actionPending: false});
      return;
    }
    // Immediately, rather than waiting out the poll interval: the server only
    // *asks* a running task to stop, so the row's status may not change for a
    // while yet, but whatever it is now is what the user should see as soon
    // as the request they made resolves.
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

  // The single contract both filter sources below go through: a falsy or
  // empty-array value deletes its key rather than storing it as `[]`/
  // `undefined` - the same "a cleared filter omits its key entirely" rule
  // `loadTasks` applies when building the request body. In practice only the
  // owner filter still hits that branch: the Status column's own
  // `commitStatusesFilter` (async-tasks-list.js) already maps an empty
  // selection to `POLLED_STATUSES` before it ever reaches here - see that
  // method's comment for why. Refetches through `refetch()` from `setState`'s
  // own callback, so the new filter is live on screen at once instead of on
  // the next poll tick.
  setFilter = (key, value) => {
    const next = {...this.state.filters};
    const isEmpty = !value || (Array.isArray(value) && value.length === 0);
    if (isEmpty) {
      delete next[key];
    } else {
      next[key] = value;
    }
    // Closes the one gap `loadTasks`'s own seeding guard cannot close by
    // itself: "cleared" and "never touched" are both `filters.owner ===
    // undefined", so if the admin clears the Owner filter while the very
    // first `loadTasks` call is still awaiting `whoAmI.fetchIfNeededOrWait()`,
    // that call would otherwise see an undefined `owner`, not yet know
    // `ownerSeeded`, and seed it right back to the current user once it
    // resolves - silently undoing a deliberate clear. Marking it seeded here,
    // the moment the owner filter is touched at all (cleared or set), closes
    // that race regardless of timing.
    if (key === 'owner') {
      this.ownerSeeded = true;
    }
    // Resets to page 1 in the same setState call: a filter change that kept
    // an earlier page would show an empty table for no visible reason.
    this.setState({filters: next, page: 1}, () => {
      this.refetch();
    });
  };

  // async-tasks-list.js's Owner column filter dropdown (admin-only).
  handleOwnerFilterChange = (owner) => this.setFilter('owner', owner);

  // async-tasks-list.js's Status column filter dropdown.
  handleStatusesFilterChange = (statuses) => this.setFilter('statuses', statuses);

  // Guards against a page below 1: antd's own pagination will not ask for one,
  // but a stray call should be a no-op rather than send `page: 0`.
  // `setState`'s callback is what lets `loadTasks` see the new page - the same
  // shape `setFilter` above uses. antd hands `(page, pageSize)`; the second
  // argument is ignored, since the size is fixed here.
  handlePageChange = (page) => {
    const nextPage = Math.max(1, page);
    this.setState({page: nextPage}, () => {
      this.refetch();
    });
  };

  // Re-fetches the current page with the current filters - no page or filter
  // reset. The poll makes this a convenience rather than the only way to see
  // fresh rows, and it stays for exactly that: it answers "now", instead of
  // up to POLL_INTERVAL_MS from now.
  handleRefresh = () => {
    this.refetch();
  };

  // Whether either filter differs from its default - the single comparison
  // that decides the "Reset filters" control's visibility below. Calls the
  // exact same two shared comparisons (models/asyncTask/statuses.js) that
  // async-tasks-list.js's own `statusesFiltered`/`ownerFiltered` getters are
  // built from, rather than re-deriving a third copy of either check - the
  // two can no longer drift apart the way the owner one once did. Computed
  // here rather than threaded down from `async-tasks-list.js`, since this
  // control renders beside the heading, not inside that table.
  get filtersChanged () {
    const {filters} = this.state;
    return statusesAreFiltered(filters.statuses, POLLED_STATUSES) ||
      ownerIsFiltered(filters.owner, this.defaultOwner);
  }

  // Resets both filters to their defaults in one `setState`, resets to page
  // 1 and re-fetches - the same shape `setFilter`/`handlePageChange` already
  // use. `filtersChanged` only requires `this.defaultOwner` to be known when
  // *owner* is the reason it is true (see that getter); a statuses-only
  // change can show this control before the first `/whoami` resolves, so
  // this leaves `filters.owner` exactly as it is rather than clobbering it
  // with an owner default this instance does not actually know yet.
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

  // `async-tasks-list.js`'s Owner column is admin-only and is omitted
  // entirely (not merely hidden) for anyone else - see that file's `columns`
  // getter. Copies the check the deleted `async-tasks-filters.js` used to
  // make for the same reason.
  //
  // A `ROLE_STORAGE_ADMIN` also sees the column, even though the server
  // still restricts what the column (and its filter) can actually show:
  // `AsyncTaskApiService.restrictToCaller` overwrites the filter's `owner`
  // with the caller's own name unless the caller is a full `ROLE_ADMIN`
  // (`AuthManager.isAdmin()`). So a storage admin's Owner column reads their
  // own name on every row, and picking another user in the filter still
  // returns their own tasks - that is a server-side limitation, not a bug
  // in this getter.
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (!authenticatedUserInfo || !authenticatedUserInfo.loaded || !authenticatedUserInfo.value) {
      return false;
    }
    const user = authenticatedUserInfo.value;
    return !!(user.admin || roleModel.userIs.storageAdmin(user));
  }

  // No cap hint here, deliberately - and nowhere else either. Any such hint
  // would need a running count of the user's active tasks, which nothing
  // keeps any more: the cap is counted against the server at the moment an
  // archive is asked for (components/special/download-storage-archive.js),
  // and reported then. Nothing can be queued from this page in any case.
  render () {
    return (
      <div className={styles.container}>
        <div className={styles.headingRow}>
          <h2 className={classNames('cp-title', styles.heading)}>
            Downloads
          </h2>
          <div className={styles.headingRowButtons}>
            {
              this.filtersChanged ? (
                <Button
                  id="downloads-reset-filters"
                  size="small"
                  disabled={this.state.actionPending}
                  onClick={this.handleResetFilters}
                >
                  {'Reset filters'}
                </Button>
              ) : null
            }
            <Button
              id="downloads-refresh"
              size="small"
              disabled={this.state.actionPending}
              onClick={this.handleRefresh}
            >
              <Icon type="reload" />
              {'Refresh'}
            </Button>
          </div>
        </div>
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
