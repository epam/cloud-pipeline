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
  POLLED_STATUSES,
  taskIsActive,
  taskCanBeCancelled,
  taskArchiveIsAvailable,
  normalizeStatus,
  archiveNameOf,
  statusesAreFiltered,
  ownerIsFiltered
} from '../../../models/asyncTask/statuses';
import UserAutoComplete from '../../special/UserAutoComplete';
import styles from './async-tasks-list.css';

// All eight statuses, in the vocabulary's own declaration order - the
// checkbox list the Status column's filter dropdown offers.
const ALL_STATUSES = Object.keys(ASYNC_TASK_STATUSES);

// Presentational: no @inject and no model import of its own. Everything it
// needs arrives as props, so most of it is testable without a Provider or a
// router, and reusable from a future modal that renders it directly. The one
// exception is the Owner column (admin-only, below): it embeds
// UserAutoComplete, which carries its own `@inject('authenticatedUserInfo')`,
// and mobx-react's `inject` throws when that store is absent from context. So
// a test that renders with `isAdmin` true needs `renderWithStores`. `isAdmin`
// defaults to `false`, which is what keeps every other test in this file free
// of that requirement.

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

// Mirrors the backend's own DataStorageDownloadArchiveManager.offeredArchiveName
// (StringUtils.endsWithIgnoreCase(requested, ".zip") ? requested : requested + ".zip"):
// the displayed name gets the suffix appended unless it is already there,
// case-insensitively, so what is shown here matches the file the user
// actually receives. Applies to the task-id fallback too.
function displayArchiveNameOf (task) {
  const name = archiveNameOf(task);
  if (!name) {
    return name;
  }
  const value = `${name}`;
  return /\.zip$/i.test(value) ? value : `${value}.zip`;
}

// Neither filterable column below supplies a custom `filterIcon` - on
// purpose. antd's own `FilterMenu.renderFilterIcon` (node_modules/antd/lib/
// table/filterDropdown.js) only ever applies its built-in "active" highlight
// (`selectedKeys.length > 0 ? prefixCls + '-selected' : ''`, fed by
// `filteredValue` below through antd's own `Table`) in the branch that
// renders its own default icon; supplying any custom `filterIcon` at all -
// even one that only adds a `className` - takes that branch, and so the
// highlight, out unconditionally: `cloneElement`'s `className` is computed
// from `filterIcon.className`, which is always `undefined` on an element
// (the prop lives at `.props.className`, not on the element itself), so a
// custom icon's own class is discarded too. A column `className` keyed off
// the same filtered state does not hit this bug (rc-table's own
// `getHeaderRows` applies it to the header `<th>` verbatim), but it is not
// what this component was asked to show, and it does not look the way it
// does on the Runs table - the highlight belongs on the icon antd already
// renders, not reproduced on the header cell.
//
// The only reason either column used to carry a custom icon was giving a
// test a locale-independent handle to click: `renderFilterIcon` always
// overwrites the icon's `title` to the current locale's `filterTitle`, zh-CN
// without a `LocaleProvider` in scope. The test file solves that at its own
// source instead, rendering through `renderWithLocale` from `@test` so
// antd's own icon carries the real, English title ("Filter menu") -
// `client/src/components/runs/run-table/columns/column-run.js` and
// `column-owner.js` never set a custom `filterIcon` either.

class AsyncTasksList extends React.Component {
  // Pure UI mechanics: whether the Status column's filter dropdown is open,
  // and which boxes are ticked while it is - "pending" until OK or Clear
  // commits them. The *committed* filter (`this.props.statusesFilter`) is
  // owned by the parent, which is what actually drives the server request -
  // this component stays free of @inject and model imports either way.
  state = {
    statusesFilterVisible: false,
    pendingStatuses: [],
    // The Owner column's own dropdown mechanics, mirroring the Status
    // column's pair above: `ownerFilterVisible` is whether the dropdown is
    // open, `pendingOwner` is the not-yet-committed value while it is. The
    // *committed* value is `this.props.ownerFilter`, owned by the parent.
    ownerFilterVisible: false,
    pendingOwner: undefined
  };

  get committedStatuses () {
    return this.props.statusesFilter || [];
  }

  get committedOwner () {
    return this.props.ownerFilter;
  }

  // Whether the Status column's selection differs from the default
  // (POLLED_STATUSES) - the single comparison that decides both the header
  // icon's active look and `filteredValue`, computed once so the two can
  // never drift apart. `statusesAreFiltered` (models/asyncTask/statuses.js)
  // is order-independent, which is what makes this correct regardless of
  // checkbox order, and is the same comparison DownloadsManager's own
  // "Reset filters" visibility reuses - see that file's `filtersChanged`.
  get statusesFiltered () {
    return statusesAreFiltered(this.committedStatuses, POLLED_STATUSES);
  }

  // Whether the committed owner differs from the default - the current
  // user. "Default" here means exactly what DownloadsManager seeds `owner`
  // to on first load (its own `defaultOwner` field, passed down as the
  // `defaultOwner` prop): a lit icon means the admin is looking at someone
  // *other than* themselves, not merely that an owner is set at all - an
  // owner is set from the very first load, since it is seeded by default,
  // so "is anything set" would light this permanently. `ownerIsFiltered`
  // (models/asyncTask/statuses.js) is the same comparison DownloadsManager's
  // own "Reset filters" visibility reuses, so the two can never drift apart
  // the way this used to from that one.
  get ownerFiltered () {
    return ownerIsFiltered(this.committedOwner, this.props.defaultOwner);
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

  // Commits `statuses` as the filter. Unlike every other filter in this
  // feature, an empty selection does NOT omit the key: it commits
  // POLLED_STATUSES (every status except CANCELLED/EXPIRED) instead of
  // `undefined`. "Untick every box, then OK" is the same gesture as `Clear`
  // (both below), and mapping either to `undefined` would delete the filter
  // and so show MORE rows than before unticking - the opposite of the
  // gesture. So `Clear` and "untick everything, OK" both land on the same
  // default. This is a named exception to the general key-omission rule the
  // Owner filter below still follows.
  commitStatusesFilter = (statuses) => {
    const {onStatusesFilterChange} = this.props;
    this.setState({statusesFilterVisible: false, pendingStatuses: []});
    if (onStatusesFilterChange) {
      onStatusesFilterChange(statuses && statuses.length ? statuses : POLLED_STATUSES);
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

  openOwnerFilter = () => {
    this.setState({
      ownerFilterVisible: true,
      pendingOwner: this.committedOwner
    });
  };

  handlePendingOwnerChange = (owner) => {
    this.setState({pendingOwner: owner});
  };

  // Commits `owner` as the filter - a string, or `undefined` to mean "any
  // user". Unlike the Status filter above, an empty/cleared owner DOES omit
  // the key (the general rule): `owner` is a single `String` server-side, so
  // there is no "everything except two values" default to fall back to the
  // way POLLED_STATUSES is for statuses - "any user" already is the absence
  // of a filter.
  commitOwnerFilter = (owner) => {
    const {onOwnerFilterChange} = this.props;
    this.setState({ownerFilterVisible: false, pendingOwner: undefined});
    if (onOwnerFilterChange) {
      onOwnerFilterChange(owner || undefined);
    }
  };

  handleOwnerFilterOk = () => {
    this.commitOwnerFilter(this.state.pendingOwner);
  };

  handleOwnerFilterClear = () => {
    this.commitOwnerFilter(undefined);
  };

  // Same "commit on close" shape as handleStatusesFilterVisibleChange above.
  handleOwnerFilterVisibleChange = (visible) => {
    if (visible) {
      this.openOwnerFilter();
    } else {
      this.commitOwnerFilter(this.state.pendingOwner);
    }
  };

  // Copies client/src/components/runs/run-table/columns/column-owner.js's
  // OwnersRolesFilterComponent (lines 28-105), minus its roles half: a single
  // UserAutoComplete rather than a checkbox list, because `owner` is a single
  // `String` server-side while `statuses` above is a `List` - a checkbox list
  // here would let an admin tick several users and then send only one of
  // them, promising something the API cannot do. UserAutoComplete cannot
  // itself signal "cleared" (its `onChange` only fires from a selection), but
  // that stops mattering inside a column-filter dropdown: the `Clear` link
  // below carries that signal externally, the same way the precedent's does.
  get ownerFilterDropdown () {
    const {pendingOwner} = this.state;
    return (
      <div
        className={classNames(styles.filterPopoverContainer, 'cp-filter-popover-container')}
        style={{width: 300}}
      >
        <UserAutoComplete
          placeholder="Owner"
          value={pendingOwner}
          onChange={this.handlePendingOwnerChange}
          onPressEnter={this.handleOwnerFilterOk}
        />
        <div className={styles.filterActionsButtonsContainer}>
          <a onClick={this.handleOwnerFilterOk}>{'OK'}</a>
          <a onClick={this.handleOwnerFilterClear}>{'Clear'}</a>
        </div>
      </div>
    );
  }

  get columns () {
    const {onCancel, onDownload, disabled, storageNamesById, isAdmin} = this.props;
    // Admin-only, and omitted from the array entirely (not merely hidden)
    // when `isAdmin` is false - a non-admin must see neither the column nor
    // its filter. Inserted right after Status below: it pairs with the
    // other filterable column, and tacking it onto the end next to Actions
    // would bury it.
    const ownerColumn = isAdmin ? {
      title: 'Owner',
      key: 'owner',
      render: (task) => task.owner,
      filterDropdown: this.ownerFilterDropdown,
      filterDropdownVisible: this.state.ownerFilterVisible,
      onFilterDropdownVisibleChange: this.handleOwnerFilterVisibleChange,
      filteredValue: this.ownerFiltered ? ['filtered'] : []
    } : null;
    return [
      {
        title: 'Archive',
        key: 'archive',
        render: (task) => displayArchiveNameOf(task)
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
        // node_modules/antd/lib/table/Column.d.ts) is what antd's own
        // `Table` uses for its internal filter-state bookkeeping
        // (`getFilteredValueColumns` in node_modules/antd/lib/table/
        // Table.js) and, through it, for the header's own filter icon
        // highlight - see this file's own comment above the class
        // declaration for why no custom `filterIcon` is supplied here to
        // carry that.
        filterDropdown: this.statusesFilterDropdown,
        filterDropdownVisible: this.state.statusesFilterVisible,
        onFilterDropdownVisibleChange: this.handleStatusesFilterVisibleChange,
        // `statusesFilter` is now always populated (see defaultProps and
        // DownloadsManager's initial state), so `statusesFiltered` compares
        // against POLLED_STATUSES rather than a truthy/non-empty check - a
        // plain "is it set" test would light the icon permanently. It
        // should read as active only once the selection has actually moved
        // away from the default.
        filteredValue: this.statusesFiltered ? ['filtered'] : [],
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
      ownerColumn,
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
    ].filter(Boolean);
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
  storageNamesById: PropTypes.object,
  isAdmin: PropTypes.bool,
  ownerFilter: PropTypes.string,
  onOwnerFilterChange: PropTypes.func,
  // The current user - DownloadsManager's own `defaultOwner` field, the
  // single source of truth `ownerFiltered` compares `ownerFilter` against.
  // `undefined` until DownloadsManager's first successful `/whoami` (see
  // that file's own comment on `defaultOwner`), in which case `ownerFiltered`
  // reports "not filtered" rather than guessing - see `ownerIsFiltered`
  // (models/asyncTask/statuses.js).
  defaultOwner: PropTypes.string
};

AsyncTasksList.defaultProps = {
  tasks: [],
  pending: false,
  onCancel: () => {},
  onDownload: () => {},
  disabled: false,
  // Matches DownloadsManager's own initial `state.filters.statuses`: with the
  // Status filter's default now a concrete list rather than "unfiltered",
  // the component's own default must agree, or a test/caller that renders
  // with no `statusesFilter` prop at all would compare `[]` against
  // POLLED_STATUSES, find them different, and light the filter icon
  // spuriously.
  statusesFilter: POLLED_STATUSES,
  onStatusesFilterChange: () => {},
  page: 1,
  hasNextPage: false,
  onPageChange: () => {},
  storageNamesById: {},
  isAdmin: false,
  onOwnerFilterChange: () => {}
};

export default AsyncTasksList;
