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
import moment from 'moment-timezone';
import classNames from 'classnames';
import {message} from 'antd';
import AsyncTaskFilter from '../../../models/asyncTask/AsyncTaskFilter';
import whoAmI from '../../../models/user/WhoAmI';
import {ASYNC_TASK_TYPES} from '../../../models/asyncTask/statuses';
import AsyncTasksList from './async-tasks-list';
import AsyncTasksFilters from './async-tasks-filters';
import downloadArchiveTask from './download-archive-task';
import continuousFetch from '../../../utils/continuous-fetch';
import styles from './DownloadsManager.css';

// Matches CurrentUserAsyncTasks's own POLL_INTERVAL_MS: there is no shared
// behaviour to factor out, just two independent pollers that happen to agree
// on a cadence.
const POLL_INTERVAL_MS = 5000;

function taskSorter (a, b) {
  const dateA = moment.utc(a.created).valueOf();
  const dateB = moment.utc(b.created).valueOf();
  if (dateA === dateB) {
    return 0;
  } else if (dateA > dateB) {
    return -1;
  } else {
    return 1;
  }
}

// This page issues its OWN unfiltered `/async-task/filter` request rather
// than reading the shared `asyncTasks` store's `tasks`. That store's
// background poll is deliberately scoped to POLLED_STATUSES (see
// models/asyncTask/statuses.js) and can therefore never surface a CANCELLED
// or EXPIRED task; the complete history, those two included, is exactly what
// this page exists to show. The injected store still backs `cancelTask`,
// `consumeTask` and the active-task cap - only the read path moves.
@inject('asyncTasks')
@observer
export default class DownloadsManager extends React.Component {
  state = {
    // Guards against a double-click firing a second cancel/consume request
    // before the first one's `await` has resolved.
    actionPending: false,
    // Flips once and stays true: the loading spinner is for the first load
    // only. Without it, `request.pending` (set by every `send()`, success or
    // failure - see RemotePost) would flash it on every 5-second background
    // poll for as long as the page stays open.
    hasLoadedOnce: false,
    // Page-local UI state, not a store: client/AGENTS.md calls for anything
    // request-scoped to be instantiated in the component rather than added to
    // the global Provider. Shape: {owner, statuses}, every key optional.
    // `owner` comes from the admin-only Owner Select in async-tasks-filters.js;
    // `statuses` comes from the Status column's own filter dropdown in
    // async-tasks-list.js - two different UI surfaces, one filter object.
    filters: {}
  };

  constructor (props) {
    super(props);
    this.request = new AsyncTaskFilter();
  }

  // Seeds `filters.owner` to the current user on the first load only - see the
  // comment inside `loadTasks`. A plain instance field, not state: an admin
  // who deliberately clears the owner filter must not be re-seeded on the
  // next 5-second poll, and state would offer no way to tell "never seeded"
  // apart from "seeded, then cleared".
  ownerSeeded = false;

  componentDidMount () {
    // Polls this page's own request continuously while it is mounted, so a
    // task that finishes while the page is open updates its row instead of
    // sitting at PENDING until a reload. A distinct identifier from
    // CurrentUserAsyncTasks's own poller ('current-user-async-tasks') keeps
    // the two from colliding in continuousFetch's registry. `fetchImmediate`
    // (continuousFetch's default) covers the initial mount fetch, so there is
    // no separate `loadTasks()` call here.
    this.poller = continuousFetch({
      call: this.loadTasks,
      intervalMS: POLL_INTERVAL_MS,
      identifier: 'downloads-page-async-tasks'
    });
  }

  componentWillUnmount () {
    if (this.poller) {
      this.poller.stop();
    }
  }

  // Not observable, and deliberately not reset between polls on success alone
  // (see `reportError`): plain bookkeeping for de-duplicating the toast below.
  lastErrorMessage = undefined;

  // De-dupes against a poll repeating the exact same failure every 5 seconds.
  // A genuinely new error (a different message, or the first one) still gets
  // its own toast.
  reportError = (text) => {
    if (text && text !== this.lastErrorMessage) {
      this.lastErrorMessage = text;
      message.error(text, 5);
    }
  };

  // Deliberately never rejects and never resolves to `{failed: true}`: doing
  // either would hand this to continuousFetch's own backoff (increaseInterval
  // in src/utils/continuous-fetch), which is the `request:`-shaped form's
  // behaviour, not this one's. A persistently failing endpoint is still
  // retried at the fixed interval instead of backing off - a known, accepted
  // tradeoff here, not an oversight.
  //
  // One other known, accepted tradeoff: the poller and `setFilter` share this
  // one `AsyncTaskFilter` instance, and `RemotePost.send` (src/models/basic/
  // RemotePost.js) silently no-ops while a previous `send()` on the same
  // instance is still in flight. So a filter committed in the narrow window
  // while a background poll's request is already in flight does not reach
  // the server on this call - but `this.state.filters` already holds the new
  // value, so the very next poll tick (at most POLL_INTERVAL_MS later) reads
  // it fresh and sends it. A momentary, bounded delay, not a lost filter.
  loadTasks = async () => {
    try {
      await whoAmI.fetchIfNeededOrWait();
      const {userName} = whoAmI.loaded ? (whoAmI.value || {}) : {};
      let {filters} = this.state;
      // Seeds `filters.owner` to the current user, once, the first time a
      // userName is actually known - the explicit "current user by default"
      // ask. Gating on `userName` rather than merely "the first call" lets a
      // slow or momentarily failing `/whoami` retry on a later poll instead
      // of seeding forever skipped. Once seeded, `this.ownerSeeded` stays
      // true for the rest of the session, so an admin who deliberately
      // clears the owner filter afterwards is never re-seeded by the next
      // 5-second poll.
      if (!this.ownerSeeded && userName) {
        this.ownerSeeded = true;
        if (filters.owner === undefined) {
          filters = {...filters, owner: userName};
          this.setState({filters});
        }
      }
      const body = {type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE};
      // Deliberately no `page`, no `pageSize`: see the comment above this
      // class. `owner`/`statuses` are added only when the filter bar/column
      // has a value for them - never an empty array, never an empty string -
      // so a cleared filter reads to the backend as "unfiltered" rather than
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
      if (!this.state.hasLoadedOnce) {
        this.setState({hasLoadedOnce: true});
      }
    }
  };

  get tasks () {
    if (!this.request || !this.request.loaded) {
      return [];
    }
    return [...(this.request.value || [])].sort(taskSorter);
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
  // `loadTasks` applies when building the request body. Re-fetches directly
  // rather than through `this.poller.fetch()`: a plain `loadTasks()` call is
  // simpler and the poller keeps its own 5-second schedule regardless.
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
    this.setState({filters: next}, () => {
      this.loadTasks();
    });
  };

  // async-tasks-filters.js's admin-only Owner Select.
  handleOwnerFilterChange = (owner) => this.setFilter('owner', owner);

  // async-tasks-list.js's Status column filter dropdown.
  handleStatusesFilterChange = (statuses) => this.setFilter('statuses', statuses);

  render () {
    const {asyncTasks} = this.props;
    return (
      <div className={styles.container}>
        <h2 className={classNames('cp-title', styles.heading)}>
          Downloads
        </h2>
        {
          asyncTasks && asyncTasks.activeTasksLimitReached ? (
            <span className={classNames('cp-text-not-important', styles.limitHint)}>
              {'You have reached the limit of active archive downloads. ' +
                'Wait for one to finish before starting another.'}
            </span>
          ) : null
        }
        <AsyncTasksFilters
          owner={this.state.filters.owner}
          onChange={this.handleOwnerFilterChange}
        />
        <AsyncTasksList
          tasks={this.tasks}
          pending={!this.state.hasLoadedOnce && !!(this.request && this.request.pending)}
          onCancel={this.handleCancel}
          onDownload={this.handleDownload}
          disabled={this.state.actionPending}
          statusesFilter={this.state.filters.statuses}
          onStatusesFilterChange={this.handleStatusesFilterChange}
        />
      </div>
    );
  }
}
