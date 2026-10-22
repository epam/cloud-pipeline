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
import {Table, Button, Icon, Tooltip, Checkbox, Popover} from 'antd';
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
    const {onCancel, onDownload, disabled, storageNamesById} = this.props;
    return [
      {
        title: 'Archive',
        key: 'archive',
        render: (task) => archiveNameOf(task)
      },
      {
        title: 'Storage',
        key: 'storage',
        // Resolves the id to the storage's name via the lookup the container
        // builds from the `dataStorages` singleton, falling back to the raw
        // id when the storage is unknown (deleted, or the list has not
        // loaded yet) - never nothing, and never a throw either way. The
        // `Number(...)` coercion is defensive, not load-bearing against a
        // plain object (whose keys stringify either way): a task's
        // storageId may arrive as a string or a number, and coercing first
        // keeps the lookup correct if `storageNamesById` is ever built as a
        // `Map`, where a numeric and a string key are genuinely distinct.
        render: (task) => {
          const storageId = task.params && task.params.storageId;
          if (storageId === undefined || storageId === null) {
            return storageId;
          }
          const name = storageNamesById ? storageNamesById[Number(storageId)] : undefined;
          return name || storageId;
        }
      },
      {
        title: 'Items',
        key: 'items',
        // A count with no popover at all when there is nothing to list - the
        // same guard the count itself already applies for a missing
        // `params`/`paths`. An empty popover that opens on hover and shows
        // nothing would be worse than no affordance.
        render: (task) => {
          const paths = (task.params && task.params.paths) || [];
          const count = paths.length;
          if (!count) {
            return count;
          }
          return (
            <Popover
              mouseEnterDelay={1}
              trigger="hover"
              content={
                <div className={styles.archivePathsPopoverContainer}>
                  {paths.map((path, index) => (
                    <div key={`${path}-${index}`}>{path}</div>
                  ))}
                </div>
              }
            >
              <span>{count}</span>
            </Popover>
          );
        }
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

  // `/async-task/filter` returns a bare list with no total count and no page
  // metadata, so there is no number from which to render antd's own page
  // numbers or a last-page jump - only "is there a previous page" and "might
  // there be a next one". Copies the first/prev/next-with-no-total shape of
  // client/src/components/pipelines/browser/data-storage/components/
  // storage-pagination.js:40-75, rendered only when there is something to
  // navigate: a single short first page would otherwise show a pointless row.
  get pagination () {
    const {page, hasNextPage, onPageChange} = this.props;
    if (!(page > 1 || hasNextPage)) {
      return null;
    }
    return (
      <div className={styles.pagination}>
        <Button
          id="downloads-first-page"
          aria-label="First page"
          size="small"
          disabled={page <= 1}
          onClick={() => onPageChange(1)}
        >
          <Icon style={{marginRight: '-3px'}} type="caret-left" />
          <Icon type="caret-left" />
        </Button>
        <Button
          id="downloads-prev-page"
          aria-label="Previous page"
          size="small"
          style={{margin: 3}}
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          <Icon type="caret-left" />
        </Button>
        <span className={classNames('ant-pagination-item', 'ant-pagination-item-active')}>
          {page}
        </span>
        <Button
          id="downloads-next-page"
          aria-label="Next page"
          size="small"
          style={{margin: 3}}
          disabled={!hasNextPage}
          onClick={() => onPageChange(page + 1)}
        >
          <Icon type="caret-right" />
        </Button>
      </div>
    );
  }

  render () {
    const {tasks, pending} = this.props;
    return (
      <div>
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          loading={pending}
          dataSource={tasks || []}
          columns={this.columns}
        />
        {this.pagination}
      </div>
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
  onStatusesFilterChange: PropTypes.func,
  page: PropTypes.number,
  hasNextPage: PropTypes.bool,
  onPageChange: PropTypes.func,
  storageNamesById: PropTypes.object
};

AsyncTasksList.defaultProps = {
  tasks: [],
  pending: false,
  onCancel: () => {},
  onDownload: () => {},
  disabled: false,
  onStatusesFilterChange: () => {},
  page: 1,
  hasNextPage: false,
  onPageChange: () => {},
  storageNamesById: {}
};

export default AsyncTasksList;
