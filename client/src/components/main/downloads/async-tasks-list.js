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
import PropTypes from 'prop-types';
import {Table, Button, Icon, Tooltip} from 'antd';
import classNames from 'classnames';
import displayDate from '../../../utils/displayDate';
import {
  ASYNC_TASK_STATUSES,
  taskCanBeCancelled,
  taskArchiveIsAvailable,
  normalizeStatus,
  archiveNameOf
} from '../../../models/asyncTask/statuses';

// Purely presentational: no @inject, no model import, no store. Everything it
// needs arrives as props, so it is testable without a Provider or a router,
// and reusable from a future modal that renders it directly.

function statusClassName (status) {
  switch (normalizeStatus(status)) {
    case ASYNC_TASK_STATUSES.FAILED:
      return 'cp-danger';
    case ASYNC_TASK_STATUSES.CANCELLED:
    case ASYNC_TASK_STATUSES.EXPIRED:
      return 'cp-text-not-important';
    case ASYNC_TASK_STATUSES.SUCCESS:
      return 'cp-primary';
    default:
      return undefined;
  }
}

class AsyncTasksList extends React.Component {
  get columns () {
    const {onCancel, onDownload, disabled} = this.props;
    return [
      {
        title: 'Archive',
        key: 'archive',
        render: (task) => archiveNameOf(task)
      },
      {
        title: 'Storage',
        key: 'storage',
        render: (task) => task.params && task.params.storageId
      },
      {
        title: 'Items',
        key: 'items',
        render: (task) => (task.params && task.params.paths ? task.params.paths.length : 0)
      },
      {
        title: 'Status',
        key: 'status',
        render: (task) => {
          const {status, errorMessage} = task;
          const content = (
            <span className={classNames(statusClassName(status))}>
              {status}
            </span>
          );
          if (normalizeStatus(status) === ASYNC_TASK_STATUSES.FAILED && errorMessage) {
            return (
              <Tooltip title={errorMessage}>
                {content}
              </Tooltip>
            );
          }
          return content;
        }
      },
      {
        title: 'Created',
        key: 'created',
        render: (task) => displayDate(task.created)
      },
      {
        title: 'Finished',
        key: 'finished',
        render: (task) => displayDate(task.finished)
      },
      {
        title: 'Actions',
        key: 'actions',
        render: (task) => (
          <span>
            {taskArchiveIsAvailable(task) ? (
              <Button
                id={`async-task-${task.id}-download`}
                size="small"
                disabled={disabled}
                onClick={() => onDownload(task)}
              >
                <Icon type="download" />
                {'Download'}
              </Button>
            ) : null}
            {taskCanBeCancelled(task) ? (
              <Button
                id={`async-task-${task.id}-cancel`}
                size="small"
                disabled={disabled}
                onClick={() => onCancel(task)}
              >
                <Icon type="close" />
                {'Cancel'}
              </Button>
            ) : null}
          </span>
        )
      }
    ];
  }

  render () {
    const {tasks, pending} = this.props;
    return (
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        loading={pending}
        dataSource={tasks || []}
        columns={this.columns}
      />
    );
  }
}

AsyncTasksList.propTypes = {
  tasks: PropTypes.array,
  pending: PropTypes.bool,
  onCancel: PropTypes.func,
  onDownload: PropTypes.func,
  disabled: PropTypes.bool
};

AsyncTasksList.defaultProps = {
  tasks: [],
  pending: false,
  onCancel: () => {},
  onDownload: () => {},
  disabled: false
};

export default AsyncTasksList;
