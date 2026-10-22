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
import {ASYNC_TASK_TYPES, POLLED_STATUSES} from '../../../models/asyncTask/statuses';
import compareArrays from '../../../utils/compareArrays';
import AsyncTasksList from './async-tasks-list';
import downloadArchiveTask from './download-archive-task';
import styles from './DownloadsManager.css';

// A page's worth of rows per request. Matches the request body's `pageSize`
// and `hasNextPage`'s `length === PAGE_SIZE` check below - both read this
// constant, nothing else does.
const PAGE_SIZE = 15;

// This page issues its OWN `/async-task/filter` request rather than reading
// the shared `asyncTasks` store's `tasks`. That store's background poll is
// deliberately scoped to POLLED_STATUSES (see models/asyncTask/statuses.js)
// and can therefore never surface a CANCELLED or EXPIRED task; this page can
// still show them, on request, via the Status column's own filter - see
// `state.filters` below. The injected store still backs `cancelTask`,
// `consumeTask` and the active-task cap - only the read path moves. There is
// no poller here any more: the page fetches on mount, on a manual Refresh,
// and whenever a filter, the page number, or a cancellation changes it -
// see "the three ways this table refetches" on `loadTasks` below.
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
  // who deliberately clears the owner filter must not be re-seeded on a later
  // refresh, and state would offer no way to tell "never seeded" apart from
  // "seeded, then cleared".
  ownerSeeded = false;

  // The current user's username, once `loadTasks` has learned it - the
  // "default" `filtersChanged` (below) compares the committed owner against.
  // `undefined` until the first successful `/whoami`, which is also why the
  // "Reset filters" control (`render`) can briefly fail to appear on a very
  // slow first load even with a non-default owner already set - an edge
  // case, not a bug: there is nothing to reset *to* until this is known.
  defaultOwner = undefined;

  // Guards `loadTasks`'s now-unconditional `setState` call (see its `finally`
  // block) against firing after unmount: a `loadTasks()` call already in
  // flight - from the mount fetch, a manual Refresh, a filter/page change, or
  // a cancellation - can still be awaiting `this.request.send(body)` at the
  // exact instant the page unmounts, and its `finally` still runs once that
  // resolves. Before `fetchTick` existed, the same `setState` call was gated
  // to fire once ever (`hasLoadedOnce`), so this race existed only around the
  // very first load; bumping `fetchTick` on every call widens the window to
  // every one of them, so it earns its own guard.
  mounted = false;

  componentDidMount () {
    this.mounted = true;
    this.loadTasks();
  }

  componentWillUnmount () {
    this.mounted = false;
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

  // `RemotePost.send` (src/models/basic/RemotePost.js) silently no-ops while
  // a previous `send()` on the same instance is still in flight. This method
  // is now the single caller of `this.request.send` - see "the three ways
  // this table refetches" below - but those three can still overlap each
  // other (a manual Refresh clicked while a filter change's own `loadTasks()`
  // is still in flight, for instance). In that narrow window the second call
  // is dropped on the floor: `this.state.filters`/`this.state.page` already
  // hold the new value, but nothing re-sends it automatically any more, since
  // there is no poller to pick it up within a few seconds the way there used
  // to be. The user sees it on the next manual Refresh. A known, accepted
  // tradeoff, not an oversight - but a real behaviour change from before this
  // phase, worth remembering if a dropped filter change is ever reported.
  //
  // There are exactly three ways this table refetches: the mount call above,
  // `handleRefresh` below, and the existing `setFilter`/`handlePageChange`
  // (which already call this from their own `setState` callback) and
  // `handleCancel` (which already awaits it). This phase adds no new wiring
  // to any of the latter three - say so here so a reader does not go hunting
  // for a fourth mechanism.
  loadTasks = async () => {
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
        // Unlike CurrentUserAsyncTasks's background poll (which must keep
        // sending neither key - see that file), this page's own request
        // always pages: `page` is 1-based state, `pageSize` is fixed. See
        // the comment above this class for why `owner`/`statuses` are
        // conditional while these two are not.
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
      }
    } catch (error) {
      this.reportError(error.message);
    } finally {
      // `fetchTick` is bumped unconditionally on every call, success or
      // failure, no matter which of the three triggers fired it: see its
      // comment in the state initialiser for why - it is the only thing
      // forcing this @observer component to re-render on a later fetch, since
      // `this.request.value` is not itself MobX-reactive. Guarded by
      // `this.mounted` - see that field's own comment - since this runs on
      // every call to this method, not just the first, widening the window
      // in which an in-flight call's completion can land after unmount.
      if (this.mounted) {
        this.setState(previous => ({
          fetchTick: previous.fetchTick + 1
        }));
      }
    }
  };

  // The server already sorts every response newest-first, unconditionally,
  // in both its paged and unpaged branches (`AsyncTaskManager.loadByFilter`
  // on origin/java21_issue_4570_download_archive), with no sort parameter in
  // the request. Passed through verbatim: a client-side re-sort would only
  // reorder the current page among itself, which becomes an inconsistency
  // across pages the moment anything makes the two orderings differ.
  get tasks () {
    if (!this.request || !this.request.loaded) {
      return [];
    }
    return this.request.value || [];
  }

  // `true` when the current response holds exactly PAGE_SIZE tasks - read
  // off the request's own value, not the (identical) `tasks` getter, so the
  // meaning stays "what the server returned" even if a future change makes
  // `tasks` derive something else from it. A full page *might* mean more
  // exist; a short page definitely means this is the last one. Deliberately
  // optimistic: the next button can be enabled on an exactly-full last page,
  // and clicking it then shows an empty page - recoverable, since first/prev
  // stay enabled.
  get hasNextPage () {
    const length = this.request && this.request.loaded
      ? (this.request.value || []).length
      : 0;
    return length === PAGE_SIZE;
  }

  // Maps a task's numeric storage id to its name, for the Storage column.
  // `dataStorages` is the shared singleton already in Root.js's Provider
  // (one GET /datastorage/loadAll for every storage the caller can see, see
  // models/dataStorage/DataStorages.js) - nothing here re-fetches it. A
  // plain object, not a Map, so it travels to async-tasks-list.js as plain
  // data and that file stays free of @inject and model imports. A plain
  // getter rather than @computed: `tasks`/`hasNextPage` above are plain
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
    // Required, not optional: this page's rows come from its own request,
    // so nothing else refreshes this view after a cancellation.
    await this.loadTasks();
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
  // method's comment for why. One of the three refetch triggers named on
  // `loadTasks`: this is a plain `loadTasks()` call from `setState`'s own
  // callback, with no poller left to otherwise pick the change up.
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
      this.loadTasks();
    });
  };

  // async-tasks-list.js's Owner column filter dropdown (admin-only).
  handleOwnerFilterChange = (owner) => this.setFilter('owner', owner);

  // async-tasks-list.js's Status column filter dropdown.
  handleStatusesFilterChange = (statuses) => this.setFilter('statuses', statuses);

  // Guards against a page below 1 (the first-page button already disables
  // itself there, but a stray call should still be a no-op rather than send
  // page: 0). `setState`'s callback is what lets `loadTasks` see the new
  // page - the same shape `setFilter` above uses.
  handlePageChange = (page) => {
    const nextPage = Math.max(1, page);
    this.setState({page: nextPage}, () => {
      this.loadTasks();
    });
  };

  // Re-fetches the current page with the current filters - no page or filter
  // reset. One of the three refetch triggers named on `loadTasks`.
  handleRefresh = () => {
    this.loadTasks();
  };

  // Whether either filter differs from its default - the single comparison
  // that decides the "Reset filters" control's visibility below. Deliberately
  // reuses the same two comparisons the two columns' own header highlight is
  // built from (`compareArrays` against `POLLED_STATUSES` for statuses,
  // `async-tasks-list.js`'s `statusesFiltered`; a plain inequality against
  // `this.defaultOwner` for owner, since the Owner column's own `ownerFiltered`
  // deliberately means something else - "is any owner set at all", true from
  // the first load onward - and answers a different question than "has
  // anything actually changed from the out-of-the-box view"). Computed here
  // rather than threaded down from `async-tasks-list.js`, since this control
  // renders beside the heading, not inside that table.
  get filtersChanged () {
    const {filters} = this.state;
    const statusesChanged = !compareArrays(filters.statuses || [], POLLED_STATUSES);
    const ownerChanged = this.defaultOwner !== undefined &&
      (filters.owner || undefined) !== this.defaultOwner;
    return statusesChanged || ownerChanged;
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
      this.loadTasks();
    });
  };

  // `async-tasks-list.js`'s Owner column is admin-only and is omitted
  // entirely (not merely hidden) for anyone else - see that file's `columns`
  // getter. Copies the check the deleted `async-tasks-filters.js` used to
  // make for the same reason.
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    return !!(
      authenticatedUserInfo &&
      authenticatedUserInfo.loaded &&
      authenticatedUserInfo.value &&
      authenticatedUserInfo.value.admin
    );
  }

  render () {
    const {asyncTasks} = this.props;
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
        {
          asyncTasks && asyncTasks.activeTasksLimitReached ? (
            <span className={classNames('cp-text-not-important', styles.limitHint)}>
              {'You have reached the limit of active archive downloads. ' +
                'Wait for one to finish before starting another.'}
            </span>
          ) : null
        }
        <AsyncTasksList
          tasks={this.tasks}
          pending={!!(this.request && this.request.pending)}
          onCancel={this.handleCancel}
          onDownload={this.handleDownload}
          disabled={this.state.actionPending}
          isAdmin={this.isAdmin}
          ownerFilter={this.state.filters.owner}
          onOwnerFilterChange={this.handleOwnerFilterChange}
          statusesFilter={this.state.filters.statuses}
          onStatusesFilterChange={this.handleStatusesFilterChange}
          page={this.state.page}
          hasNextPage={this.hasNextPage}
          onPageChange={this.handlePageChange}
          storageNamesById={this.storageNamesById}
        />
      </div>
    );
  }
}
