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
import downloadArchiveTask from './download-archive-task';
import styles from './DownloadsManager.css';

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
@inject('asyncTasks', 'preferences')
@observer
export default class DownloadsManager extends React.Component {
  state = {
    // Guards against a double-click firing a second cancel/consume request
    // before the first one's `await` has resolved.
    actionPending: false
  };

  constructor (props) {
    super(props);
    this.request = new AsyncTaskFilter();
  }

  componentDidMount () {
    this.loadTasks();
  }

  loadTasks = async () => {
    try {
      await whoAmI.fetchIfNeededOrWait();
      const {userName} = whoAmI.loaded ? (whoAmI.value || {}) : {};
      const body = {type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE};
      // `owner` is sent explicitly, the same way the background poll does,
      // because it is ACL-guarded server-side rather than a filter a regular
      // user could abuse. Deliberately no `statuses`, no `page`, no
      // `pageSize`: see the comment above this class.
      if (userName) {
        body.owner = userName;
      }
      await this.request.send(body);
      if (this.request.error) {
        message.error(this.request.error, 5);
      }
    } catch (error) {
      message.error(error.message, 5);
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
        <AsyncTasksList
          tasks={this.tasks}
          pending={this.request && this.request.pending}
          onCancel={this.handleCancel}
          onDownload={this.handleDownload}
          disabled={this.state.actionPending}
        />
      </div>
    );
  }
}
