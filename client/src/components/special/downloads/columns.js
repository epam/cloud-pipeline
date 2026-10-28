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
import {Button, Icon, Tooltip, Popover} from 'antd';
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

// What the table's `columns` array looks like is pulled out of
// async-tasks-list.js into this file - a plain, store-free builder function
// rather than a getter mixed in among the component's own state and DOM
// event handlers. async-tasks-list.js stays the only thing that owns React
// state (the two filter dropdowns' open/pending mechanics) and calls
// `buildColumns` below to turn that state, plus its own props, into the
// array `<Table columns={...} />` renders. Nothing here reaches into
// `this` - every input arrives as a plain argument - so this file carries
// no @inject and no model import of its own either, the same invariant
// async-tasks-list.js's own header comment states for the component.

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

// Builds the `columns` array `async-tasks-list.js` passes to antd's
// `<Table>`. Every input below is something async-tasks-list.js already
// owns - a prop it was given, or a piece of its own component state/
// getters/handlers - passed in verbatim rather than read off `this`, so this
// function has nothing it reaches for on its own and nothing it could reach
// for inconsistently between renders.
export default function buildColumns ({
  onCancel,
  onDownload,
  disabled,
  storageNamesById,
  isAdmin,
  ownerFilterDropdown,
  ownerFilterVisible,
  onOwnerFilterVisibleChange,
  ownerFiltered,
  statusesFilterDropdown,
  statusesFilterVisible,
  onStatusesFilterVisibleChange,
  statusesFiltered
}) {
  // Admin-only, and omitted from the array entirely (not merely hidden)
  // when `isAdmin` is false - a non-admin must see neither the column nor
  // its filter. Inserted right after Status below: it pairs with the
  // other filterable column, and tacking it onto the end next to Actions
  // would bury it.
  const ownerColumn = isAdmin ? {
    title: 'Owner',
    key: 'owner',
    render: (task) => task.owner,
    filterDropdown: ownerFilterDropdown,
    filterDropdownVisible: ownerFilterVisible,
    onFilterDropdownVisibleChange: onOwnerFilterVisibleChange,
    filteredValue: ownerFiltered ? ['filtered'] : []
  } : null;
  return [
    {
      title: 'Archive',
      key: 'archive',
      // The archive's name, and how many items went into it:
      // `my-archive.zip (2 items)`, with the count's own popover listing the
      // archived paths on hover. The count used to be a column of its own; it
      // belongs here, where it reads as a property of the archive rather than
      // as a bare number under a heading. Singular for one, matching the way
      // `data-storage/index.js` words its own item counts.
      //
      // A task with nothing to list renders the bare name - no count in
      // brackets and no popover at all. The same guard the count itself
      // already applied: an empty popover that opens on hover and shows
      // nothing would be worse than no affordance.
      render: (task) => {
        const name = displayArchiveNameOf(task);
        const paths = (task.params && task.params.paths) || [];
        // `displayArchiveNameOf` hands back a falsy name untouched, so that an
        // unnameable task renders an empty cell; the template below would turn
        // that into the literal text `undefined (2 items)`.
        if (!name || !paths.length) {
          return name;
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
            <span>{`${name} (${paths.length} item${paths.length > 1 ? 's' : ''})`}</span>
          </Popover>
        );
      }
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
      // highlight - see this file's own comment above `buildColumns` for
      // why no custom `filterIcon` is supplied here to carry that.
      filterDropdown: statusesFilterDropdown,
      filterDropdownVisible: statusesFilterVisible,
      onFilterDropdownVisibleChange: onStatusesFilterVisibleChange,
      // `statusesFilter` is now always populated (see async-tasks-list.js's
      // own defaultProps and DownloadsManager's initial state), so
      // `statusesFiltered` compares against POLLED_STATUSES rather than a
      // truthy/non-empty check - a plain "is it set" test would light the
      // icon permanently. It should read as active only once the selection
      // has actually moved away from the default.
      filteredValue: statusesFiltered ? ['filtered'] : [],
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
