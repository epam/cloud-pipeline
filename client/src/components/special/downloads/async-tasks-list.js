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
import {Table, Pagination, Checkbox, Radio} from 'antd';
import classNames from 'classnames';
import {
  ALL_ASYNC_TASK_STATUSES,
  POLLED_STATUSES,
  ASYNC_TASK_TYPES,
  displayTaskType,
  statusesAreFiltered,
  ownerIsFiltered,
  typeIsFiltered
} from '../../../models/asyncTask/statuses';
import UserAutoComplete from '../../special/UserAutoComplete';
import buildColumns from './columns';
import styles from './async-tasks-list.css';

// Presentational: no @inject and no model import of its own. Everything it
// needs arrives as props, so most of it is testable without a Provider or a
// router, and reusable from a future modal that renders it directly. The one
// exception is the Owner column (admin-only, below): it embeds
// UserAutoComplete, which carries its own `@inject('authenticatedUserInfo')`,
// and mobx-react's `inject` throws when that store is absent from context. So
// a test that renders with `isAdmin` true needs `renderWithStores`. `isAdmin`
// defaults to `false`, which is what keeps every other test in this file free
// of that requirement.
//
// What each column looks like - its render functions, the status/size/
// archive-name display logic, and why neither filterable column supplies a
// custom `filterIcon` - lives in `buildColumns` (./columns.js), a plain
// function of this component's props/state/handlers. This file stays the
// one place that owns the two filter dropdowns' own open/pending React
// state and calls `buildColumns` to turn it into the array `<Table>` below
// renders.

class AsyncTasksList extends React.Component {
  // Pure UI mechanics: whether the Status column's filter dropdown is open,
  // and which boxes are ticked while it is - "pending" until OK or
  // Reset to defaults commits them. The *committed* filter
  // (`this.props.statusesFilter`) is owned by the parent, which is what
  // actually drives the server request - this component stays free of
  // @inject and model imports either way.
  state = {
    statusesFilterVisible: false,
    pendingStatuses: [],
    // The Owner column's own dropdown mechanics, mirroring the Status
    // column's pair above: `ownerFilterVisible` is whether the dropdown is
    // open, `pendingOwner` is the not-yet-committed value while it is. The
    // *committed* value is `this.props.ownerFilter`, owned by the parent.
    ownerFilterVisible: false,
    pendingOwner: undefined,
    // What the input holds, which differs from `pendingOwner` while typing.
    pendingOwnerText: undefined,
    // The Type column's dropdown mechanics. `undefined` is "every type", so
    // the pending value is a type or nothing - not a list, because
    // `AsyncTaskFilterVO.type` is one enum server-side.
    typeFilterVisible: false,
    pendingType: undefined
  };

  get committedStatuses () {
    return this.props.statusesFilter || [];
  }

  get committedOwner () {
    return this.props.ownerFilter;
  }

  get committedType () {
    return this.props.typeFilter;
  }

  get typeFiltered () {
    return typeIsFiltered(this.committedType);
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

  // The dropdown's own select-all box (see `statusesFilterDropdown` below
  // for the tri-state it renders with, and for the label that names whichever
  // of the two it is about to do): ticks every known status, or unticks the
  // lot. Driven by antd's own `event.target.checked`, which an indeterminate
  // Checkbox reports as `false` - so a click from the half-ticked state
  // selects everything, which is the gesture anyone expects of it.
  toggleAllStatuses = (event) => {
    this.setState({pendingStatuses: event.target.checked ? [...ALL_ASYNC_TASK_STATUSES] : []});
  };

  // Commits `statuses` as the filter, verbatim - an **empty array included**.
  // That is the whole point of the empty case here: unticking every box
  // reaches the server as `statuses: []` rather than being quietly turned
  // back into a default the user did not ask for. The parent
  // (downloads-manager.js's `setFilter`/`sendFilterRequest`) carries that
  // empty array through to the request body for the same reason.
  //
  // **What the user then sees is not an empty table.**
  // `AsyncTaskSpecification.of` only adds a status predicate for a NON-empty
  // list (`CollectionUtils.isNotEmpty`), so an empty one filters nothing and
  // the table shows every status, CANCELLED and EXPIRED included - MORE rows
  // than before unticking, not fewer. That is deliberate and agreed, and it
  // is not something to "fix" at either end: the request says what the user
  // chose, the server decides what that matches. It is also why
  // `Reset to defaults` below is a separate gesture - it is the only thing
  // that puts POLLED_STATUSES back.
  //
  // `|| []` guards a caller passing `undefined`: the parent treats an array
  // (empty or not) as the committed value, and handing it `undefined`
  // instead would delete the key and send no `statuses` at all - the same
  // request as `[]` today, but by a different route, and a confusing one to
  // debug if the server's handling of an empty list ever changes.
  commitStatusesFilter = (statuses) => {
    const {onStatusesFilterChange} = this.props;
    this.setState({statusesFilterVisible: false, pendingStatuses: []});
    if (onStatusesFilterChange) {
      onStatusesFilterChange(statuses || []);
    }
  };

  handleStatusesFilterOk = () => {
    this.commitStatusesFilter(this.state.pendingStatuses);
  };

  // "Reset to defaults" - what `Clear` used to be, renamed because that is
  // what it has always actually done here: it commits POLLED_STATUSES (every
  // status but CANCELLED/EXPIRED), the table's own default, rather than
  // clearing the filter. The rename matters more now that unticking
  // everything genuinely commits an empty list: the two gestures are no
  // longer the same one, and a link labelled `Clear` would be the wrong name
  // for either.
  handleStatusesFilterResetToDefaults = () => {
    this.commitStatusesFilter(POLLED_STATUSES);
  };

  // Called by antd's Table filter Dropdown itself - on open (seed the
  // pending checkboxes from the committed filter) and on close by any means
  // other than our own OK/Reset links, e.g. a click outside the dropdown,
  // which commits whatever is currently ticked - now including nothing at
  // all, which closing on an empty selection commits as an empty list. Copies
  // client/src/components/runs/run-table/columns/state-utilities.js's
  // onDropdownVisibilityChanged "commit on close" shape, reimplemented
  // locally for this single-parameter case rather than imported: that file's
  // helpers are shaped around a host table's own `{filters, filtersState}`
  // state (and a `search` sub-feature this column does not need), built for
  // column-run.js's two-parameter statuses+regions combo.
  //
  // The `statusesFilterVisible` guard on the close branch is what keeps
  // "commit on close" from firing twice. Our own OK/Reset links close the
  // dropdown themselves (`commitStatusesFilter` lowers the flag and empties
  // `pendingStatuses`), so a second close - whether antd reports the
  // controlled prop going false, or the user clicks away afterwards - would
  // otherwise commit that emptied `pendingStatuses` straight over the
  // selection just made. The old code survived this by accident, mapping an
  // empty list back onto the default; that mapping is gone on purpose now,
  // so the guard has to be explicit.
  handleStatusesFilterVisibleChange = (visible) => {
    if (visible) {
      this.openStatusesFilter();
    } else if (this.state.statusesFilterVisible) {
      this.commitStatusesFilter(this.state.pendingStatuses);
    }
  };

  // Copies the Checkbox-list-plus-OK shape of
  // client/src/components/runs/run-table/columns/column-run.js's
  // StatusesRegionsFilterComponent (lines 31-143), minus the regions half
  // this column has no equivalent of. `cp-filter-popover-container` is the
  // existing theme class that component also reuses - no new `cp-*` class,
  // and the divider below reuses the themable `cp-divider`/`horizontal` pair
  // from src/themes/styles/layout.less rather than hard-coding a border
  // colour in this module's own CSS, where it would not follow the theme.
  get statusesFilterDropdown () {
    const {pendingStatuses} = this.state;
    const pendingSet = new Set(pendingStatuses);
    // How many of the known statuses are ticked. Counted against
    // ALL_ASYNC_TASK_STATUSES rather than read off `pendingStatuses.length`, so neither
    // a duplicate nor a status the parent committed that this vocabulary
    // does not know can make "every box is ticked" read as true.
    const selectedCount = ALL_ASYNC_TASK_STATUSES.filter((status) => pendingSet.has(status)).length;
    const allSelected = selectedCount === ALL_ASYNC_TASK_STATUSES.length;
    return (
      <div
        className={classNames(styles.filterPopoverContainer, 'cp-filter-popover-container')}
      >
        {/*
          * The tri-state "select everything / nothing" box, above the list
          * it controls and separated from it by the divider below - the same
          * `checked`+`indeterminate` pair client/src/components/cluster/
          * Cluster.js's own node-selection header checkbox uses. `checked`
          * only once every status is ticked; `indeterminate` for any
          * non-empty partial selection, which is what the default
          * (POLLED_STATUSES - six of the eight) opens as.
          *
          * Its label names the action the next click performs rather than
          * describing the control, so it always reads as true: everything is
          * ticked only in the `allSelected` case, which is the only one where
          * clicking unticks. antd reports an indeterminate box's `checked` as
          * false, so a partial selection - the default included - selects
          * everything on the next click, and says so.
          */}
        <div className={styles.filterItem}>
          <Checkbox
            checked={allSelected}
            indeterminate={selectedCount > 0 && !allSelected}
            onChange={this.toggleAllStatuses}
          >
            {allSelected ? 'Unselect all' : 'Select all'}
          </Checkbox>
        </div>
        <div className={classNames(styles.filterDivider, 'cp-divider', 'horizontal')} />
        {
          ALL_ASYNC_TASK_STATUSES.map((status) => (
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
          <a onClick={this.handleStatusesFilterResetToDefaults}>{'Reset to defaults'}</a>
        </div>
      </div>
    );
  }

  openTypeFilter = () => {
    this.setState({typeFilterVisible: true, pendingType: this.committedType});
  };

  selectType = (event) => {
    // The "every type" radio carries an empty string, since a Radio value of
    // `undefined` makes the group uncontrolled.
    this.setState({pendingType: event.target.value || undefined});
  };

  commitTypeFilter = (type) => {
    const {onTypeFilterChange} = this.props;
    this.setState({typeFilterVisible: false, pendingType: undefined});
    if (onTypeFilterChange) {
      onTypeFilterChange(type);
    }
  };

  handleTypeFilterOk = () => {
    this.commitTypeFilter(this.state.pendingType);
  };

  handleTypeFilterResetToDefaults = () => {
    this.commitTypeFilter(undefined);
  };

  // Same "commit on close" shape, and the same double-commit guard, as
  // `handleStatusesFilterVisibleChange` above.
  handleTypeFilterVisibleChange = (visible) => {
    if (visible) {
      this.openTypeFilter();
    } else if (this.state.typeFilterVisible) {
      this.commitTypeFilter(this.state.pendingType);
    }
  };

  // A radio list, not the Status column's checkboxes: the server takes one
  // type per request. "Any type" is the default and the first option.
  get typeFilterDropdown () {
    return (
      <div
        className={classNames(styles.filterPopoverContainer, 'cp-filter-popover-container')}
      >
        <Radio.Group value={this.state.pendingType || ''} onChange={this.selectType}>
          <div className={styles.filterItem}>
            <Radio value={''}>{'Any type'}</Radio>
          </div>
          {
            Object.keys(ASYNC_TASK_TYPES).map((type) => (
              <div key={type} className={styles.filterItem}>
                <Radio value={type}>{displayTaskType(type)}</Radio>
              </div>
            ))
          }
        </Radio.Group>
        <div className={styles.filterActionsButtonsContainer}>
          <a onClick={this.handleTypeFilterOk}>{'OK'}</a>
          <a onClick={this.handleTypeFilterResetToDefaults}>{'Reset to defaults'}</a>
        </div>
      </div>
    );
  }

  openOwnerFilter = () => {
    this.setState({
      ownerFilterVisible: true,
      pendingOwner: this.committedOwner,
      pendingOwnerText: undefined
    });
  };

  handlePendingOwnerChange = (owner) => {
    this.setState({pendingOwner: owner, pendingOwnerText: owner});
  };

  // An emptied field means "every user's tasks" (`undefined`, no `owner` key). Typed text
  // leaves `pendingOwner` alone: it is the field's `value`, and changing it drops the typing.
  handlePendingOwnerText = (text) => {
    if (text) {
      this.setState({pendingOwnerText: text});
    } else {
      this.setState({pendingOwner: undefined, pendingOwnerText: ''});
    }
  };

  // Typed but not picked: committing would send `pendingOwner`, not the name the field shows.
  get ownerCommitBlocked () {
    const {pendingOwner, pendingOwnerText} = this.state;
    return !!pendingOwnerText && pendingOwnerText !== pendingOwner;
  }

  // Commits `owner` as the filter - a string, or `undefined` to mean "any
  // user". An emptied owner omits the key from the request entirely, where
  // an emptied status selection sends `statuses: []`: `owner` is a single
  // `String` server-side, so "any user" genuinely IS the absence of this
  // filter and there is no empty-list shape for it to take. Either way,
  // neither falls back to its default - that is `Reset to defaults`'s job
  // alone, on both columns.
  commitOwnerFilter = (owner) => {
    const {onOwnerFilterChange} = this.props;
    this.closeOwnerFilter();
    if (onOwnerFilterChange) {
      onOwnerFilterChange(owner || undefined);
    }
  };

  closeOwnerFilter = () => {
    this.setState({
      ownerFilterVisible: false,
      pendingOwner: undefined,
      pendingOwnerText: undefined
    });
  };

  handleOwnerFilterOk = () => {
    if (this.ownerCommitBlocked) {
      return;
    }
    this.commitOwnerFilter(this.state.pendingOwner);
  };

  // "Reset to defaults" - puts the owner back to the current user, which is
  // what DownloadsManager seeds it to on first load and what `ownerFiltered`
  // above compares against. It REPLACES the `Clear` link this dropdown used
  // to carry, the same rename the Status column got: "every user's tasks" is
  // now asked for by emptying the input instead (`handlePendingOwnerText`
  // above), which is the gesture that reads as clearing, and leaves this one
  // link meaning only what its label says.
  //
  // The parent decides, since it knows when the current user is not loaded yet.
  handleOwnerFilterResetToDefaults = () => {
    const {onOwnerFilterResetToDefaults} = this.props;
    this.closeOwnerFilter();
    if (onOwnerFilterResetToDefaults) {
      onOwnerFilterResetToDefaults();
    }
  };

  // Same "commit on close" shape as handleStatusesFilterVisibleChange above,
  // including its `ownerFilterVisible` guard, and for the same reason: both
  // links below close the dropdown themselves and empty `pendingOwner`, so
  // an unguarded second close would commit `undefined` over the owner just
  // committed - clearing the filter instead of setting the user that
  // `Reset to defaults` had asked for.
  handleOwnerFilterVisibleChange = (visible) => {
    if (visible) {
      this.openOwnerFilter();
    } else if (this.state.ownerFilterVisible) {
      if (this.ownerCommitBlocked) {
        this.closeOwnerFilter();
      } else {
        this.commitOwnerFilter(this.state.pendingOwner);
      }
    }
  };

  // Copies client/src/components/runs/run-table/columns/column-owner.js's
  // OwnersRolesFilterComponent (lines 28-105), minus its roles half: a single
  // UserAutoComplete rather than a checkbox list, because `owner` is a single
  // `String` server-side while `statuses` above is a `List` - a checkbox list
  // here would let an admin tick several users and then send only one of
  // them, promising something the API cannot do.
  //
  // There is no `Clear` link, unlike that precedent: emptying the input by
  // hand is what clears this filter, carried by UserAutoComplete's own
  // `onTextChange` (added for exactly this - its `onChange` fires only from a
  // selection, so it cannot report an emptied field). That leaves the one
  // remaining link meaning only "back to the current user".
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
          onTextChange={this.handlePendingOwnerText}
          onPressEnter={this.handleOwnerFilterOk}
        />
        {
          this.ownerCommitBlocked ? (
            <div className={classNames(styles.filterHint, 'cp-text-not-important')}>
              {'Select a user from the list, or empty the field to show every user\'s tasks.'}
            </div>
          ) : undefined
        }
        <div className={styles.filterActionsButtonsContainer}>
          <a onClick={this.handleOwnerFilterOk}>{'OK'}</a>
          <a onClick={this.handleOwnerFilterResetToDefaults}>{'Reset to defaults'}</a>
        </div>
      </div>
    );
  }

  // The actual column definitions - what each one renders, the Owner
  // column's admin-only omission, and both filterable columns'
  // `filterDropdown`/`filterDropdownVisible`/`filteredValue` wiring - live in
  // `buildColumns` (./columns.js), a plain function of exactly these inputs.
  // This getter's only job is gathering them: the props `<AsyncTasksList>`
  // was given, plus this component's own filter-dropdown state and the
  // getters/handlers built from it, above.
  get columns () {
    const {onCancel, onDownload, disabled, storageNamesById, isAdmin} = this.props;
    return buildColumns({
      onCancel,
      onDownload,
      disabled,
      storageNamesById,
      isAdmin,
      ownerFilterDropdown: this.ownerFilterDropdown,
      ownerFilterVisible: this.state.ownerFilterVisible,
      onOwnerFilterVisibleChange: this.handleOwnerFilterVisibleChange,
      ownerFiltered: this.ownerFiltered,
      statusesFilterDropdown: this.statusesFilterDropdown,
      statusesFilterVisible: this.state.statusesFilterVisible,
      onStatusesFilterVisibleChange: this.handleStatusesFilterVisibleChange,
      statusesFiltered: this.statusesFiltered,
      typeFilterDropdown: this.typeFilterDropdown,
      typeFilterVisible: this.state.typeFilterVisible,
      onTypeFilterVisibleChange: this.handleTypeFilterVisibleChange,
      typeFiltered: this.typeFiltered
    });
  }

  // antd's own `Pagination`, which `/async-task/filter` can now drive: its
  // answer carries `totalCount` - how many tasks the filter matched, not how
  // many this page holds - so there is a number from which to render page
  // numbers and a last page. The hand-rolled first/prev/next row this
  // replaces existed only because that number was missing, and could do no
  // better than "a full page of rows might mean there are more".
  //
  // Rendered BESIDE the table rather than through `<Table pagination={...}>`,
  // copying client/src/components/main/notification/NotificationBrowser.js:
  // antd's `Table` slices its own `dataSource` for the current page, and the
  // rows here are already that page, so letting it page them again would show
  // an empty table from page two on. `pagination={false}` below is what keeps
  // it out of the way.
  //
  // Nothing at all while everything fits on one page - the one thing worth
  // keeping from the row that used to be here - UNLESS the user is past the
  // first page, where hiding it would strand them. That happens for real: the
  // default Status filter hides CANCELLED and EXPIRED, so cancelling the last
  // task on page 2 of 16 matches, or one of them expiring between two polls,
  // drops the total to a single page while page 2 is on screen. The server
  // then answers that page with nothing, and without a control there is no
  // way back short of reloading.
  //
  // No `pageSize` means no pagination either, rather than handing
  // rc-pagination a zero it divides by (`calculatePage` would answer
  // `Infinity` pages).
  get pagination () {
    const {page, pageSize, total, onPageChange} = this.props;
    if (!pageSize || (total <= pageSize && page <= 1)) {
      return null;
    }
    return (
      <Pagination
        className={styles.pagination}
        current={page}
        pageSize={pageSize}
        total={total}
        size="small"
        onChange={onPageChange}
      />
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
  // One AsyncTaskType, or `undefined` for every type - the default.
  typeFilter: PropTypes.string,
  onTypeFilterChange: PropTypes.func,
  // 1-based, matching the API and `DownloadsManager`'s own state.
  page: PropTypes.number,
  pageSize: PropTypes.number,
  // How many tasks the filter matched, from the response's `totalCount` -
  // not how many rows `tasks` holds.
  total: PropTypes.number,
  onPageChange: PropTypes.func,
  storageNamesById: PropTypes.object,
  isAdmin: PropTypes.bool,
  ownerFilter: PropTypes.string,
  onOwnerFilterChange: PropTypes.func,
  onOwnerFilterResetToDefaults: PropTypes.func,
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
  onTypeFilterChange: () => {},
  page: 1,
  pageSize: 0,
  total: 0,
  onPageChange: () => {},
  storageNamesById: {},
  isAdmin: false,
  onOwnerFilterChange: () => {}
};

export default AsyncTasksList;
