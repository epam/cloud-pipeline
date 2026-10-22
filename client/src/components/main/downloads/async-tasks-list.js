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
import {Table, Button, Icon, Tooltip, Checkbox} from 'antd';
import classNames from 'classnames';
import displayDate from '../../../utils/displayDate';
import displaySize from '../../../utils/displaySize';
import {
  ASYNC_TASK_STATUSES,
  ACTIVE_STATUSES,
  taskIsActive,
  taskCanBeCancelled,
  taskArchiveIsAvailable,
  normalizeStatus,
  archiveNameOf
} from '../../../models/asyncTask/statuses';
import styles from './async-tasks-list.css';

// All eight statuses, in the vocabulary's own declaration order - the
// checkbox list the Status column's filter dropdown offers.
const ALL_STATUSES = Object.keys(ASYNC_TASK_STATUSES);

// Purely presentational: no @inject, no model import, no store. Everything it
// needs arrives as props, so it is testable without a Provider or a router,
// and reusable from a future modal that renders it directly.

// Mirrors the COLORS lookup in
// client/src/components/pipelines/browser/forms/life-cycle-rules/components/restore-status-icon.js:
// a status-keyed class map. The four "still working" entries are built from
// ACTIVE_STATUSES rather than listed by hand, so this grouping cannot drift
// from the one the cap and the Cancel button already use.
const STATUS_CLASS_NAMES = ACTIVE_STATUSES.reduce((result, status) => ({
  ...result,
  [status]: 'cp-primary'
}), {
  [ASYNC_TASK_STATUSES.SUCCESS]: 'cp-success',
  [ASYNC_TASK_STATUSES.FAILED]: 'cp-danger',
  [ASYNC_TASK_STATUSES.CANCELLED]: 'cp-text-not-important',
  [ASYNC_TASK_STATUSES.EXPIRED]: 'cp-text-not-important'
});

function statusClassName (status) {
  return STATUS_CLASS_NAMES[normalizeStatus(status)];
}

function archiveSizeOf (task) {
  const bytes = task.result && task.result.archiveSizeBytes;
  if (bytes === undefined || bytes === null || Number.isNaN(Number(bytes))) {
    return undefined;
  }
  return bytes;
}

class AsyncTasksList extends React.Component {
  // Pure UI mechanics: whether the Status column's filter dropdown is open,
  // and which boxes are ticked while it is - "pending" until OK or Clear
  // commits them. The *committed* filter (`this.props.statusesFilter`) is
  // owned by the parent, which is what actually drives the server request -
  // this component stays free of @inject and model imports either way.
  state = {
    statusesFilterVisible: false,
    pendingStatuses: []
  };

  get committedStatuses () {
    return this.props.statusesFilter || [];
  }

  openStatusesFilter = () => {
    this.setState({
      statusesFilterVisible: true,
      pendingStatuses: this.committedStatuses.slice()
    });
  };

  toggleStatus = (status) => (event) => {
    const {pendingStatuses} = this.state;
    const nextStatuses = new Set(pendingStatuses);
    if (event.target.checked) {
      nextStatuses.add(status);
    } else {
      nextStatuses.delete(status);
    }
    this.setState({pendingStatuses: [...nextStatuses]});
  };

  // Commits `statuses` as the filter - an empty array becomes `undefined`,
  // never sent on as `[]`: the key-omission rule this feature's request
  // bodies follow throughout (never an empty array, never an empty string).
  commitStatusesFilter = (statuses) => {
    const {onStatusesFilterChange} = this.props;
    this.setState({statusesFilterVisible: false, pendingStatuses: []});
    if (onStatusesFilterChange) {
      onStatusesFilterChange(statuses && statuses.length ? statuses : undefined);
    }
  };

  handleStatusesFilterOk = () => {
    this.commitStatusesFilter(this.state.pendingStatuses);
  };

  handleStatusesFilterClear = () => {
    this.commitStatusesFilter(undefined);
  };

  // Called by antd's Table filter Dropdown itself - on open (seed the
  // pending checkboxes from the committed filter) and on close by any means
  // other than our own OK/Clear links, e.g. a click outside the dropdown,
  // which commits whatever is currently ticked. Copies
  // client/src/components/runs/run-table/columns/state-utilities.js's
  // onDropdownVisibilityChanged "commit on close" shape, reimplemented
  // locally for this single-parameter case rather than imported: that file's
  // helpers are shaped around a host table's own `{filters, filtersState}`
  // state (and a `search` sub-feature this column does not need), built for
  // column-run.js's two-parameter statuses+regions combo.
  handleStatusesFilterVisibleChange = (visible) => {
    if (visible) {
      this.openStatusesFilter();
    } else {
      this.commitStatusesFilter(this.state.pendingStatuses);
    }
  };

  // Copies the Checkbox-list-plus-OK/Clear shape of
  // client/src/components/runs/run-table/columns/column-run.js's
  // StatusesRegionsFilterComponent (lines 31-143), minus the regions half
  // this column has no equivalent of. `cp-filter-popover-container` is the
  // existing theme class that component also reuses - no new `cp-*` class.
  get statusesFilterDropdown () {
    const {pendingStatuses} = this.state;
    const pendingSet = new Set(pendingStatuses);
    return (
      <div
        className={classNames(styles.filterPopoverContainer, 'cp-filter-popover-container')}
      >
        {
          ALL_STATUSES.map((status) => (
            <div key={status} className={styles.filterItem}>
              <Checkbox
                checked={pendingSet.has(status)}
                onChange={this.toggleStatus(status)}
              >
                {status}
              </Checkbox>
            </div>
          ))
        }
        <div className={styles.filterActionsButtonsContainer}>
          <a onClick={this.handleStatusesFilterOk}>{'OK'}</a>
          <a onClick={this.handleStatusesFilterClear}>{'Clear'}</a>
        </div>
      </div>
    );
  }

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
        title: 'Size',
        key: 'size',
        render: (task) => {
          const bytes = archiveSizeOf(task);
          return bytes === undefined ? '—' : displaySize(bytes);
        }
      },
      {
        title: 'Status',
        key: 'status',
        // A custom column filter, the same shape
        // client/src/components/runs/run-table/columns/column-run.js uses:
        // `filterDropdown` is our own JSX, `filterDropdownVisible` and
        // `onFilterDropdownVisibleChange` make it a controlled dropdown, and
        // `filteredValue` (an antd-recognised Column prop - see
        // node_modules/antd/lib/table/Column.d.ts) lights the header's
        // filter icon blue once a status is committed.
        filterDropdown: this.statusesFilterDropdown,
        filterDropdownVisible: this.state.statusesFilterVisible,
        onFilterDropdownVisibleChange: this.handleStatusesFilterVisibleChange,
        filteredValue: this.committedStatuses.length ? ['filtered'] : [],
        // A custom `filterIcon`, visually identical to antd's default
        // (`type="filter"`) but carrying our own `aria-label` - antd's
        // `FilterMenu.renderFilterIcon` (node_modules/antd/lib/table/
        // filterDropdown.js) *always* overwrites this element's `title` to
        // the current locale's `filterTitle` (no LocaleProvider is
        // configured in the test harness, so that title renders in antd's
        // default zh-CN today), but leaves every other prop - this
        // `aria-label` included - untouched. Querying by that locale- and
        // antd-version-dependent title would be exactly the antd-internals
        // coupling client/test/AGENTS.md rule 6 warns against; this label is
        // ours, so the test queries it instead.
        filterIcon: <Icon type="filter" aria-label="Filter by status" />,
        render: (task) => {
          const {status, errorMessage} = task;
          const content = (
            <span className={classNames(statusClassName(status))}>
              {taskIsActive(task) ? (
                <Icon type="loading" title="In progress" style={{marginRight: 5}} />
              ) : null}
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
  disabled: PropTypes.bool,
  statusesFilter: PropTypes.array,
  onStatusesFilterChange: PropTypes.func
};

AsyncTasksList.defaultProps = {
  tasks: [],
  pending: false,
  onCancel: () => {},
  onDownload: () => {},
  disabled: false,
  onStatusesFilterChange: () => {}
};

export default AsyncTasksList;
