/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import Columns from './columns';
import * as ColumnDocker from './column-docker';
import * as ColumnElapsed from './column-elapsed';
import * as ColumnEndDate from './column-end-date';
import * as ColumnLinks from './column-links';
import * as ColumnLogs from './column-logs';
import * as ColumnOwner from './column-owner';
import * as ColumnParent from './column-parent';
import * as ColumnPauseResume from './column-pause-resume';
import * as ColumnPipeline from './column-pipeline';
import * as ColumnRun from './column-run';
import * as ColumnStartDate from './column-start-date';
import * as ColumnStopReRun from './column-stop-rerun';
import * as ColumnTags from './column-tags';
import ExpandColumn from './column-expand';
import {
  RUN_LOADING_PLACEHOLDER_PROPERTY,
  RUN_LOADING_ERROR_PROPERTY
} from './run-loading-placeholder';

/**
 * @typedef {Object} GetColumnOptions
 * @property {function} [localizedString]
 * @property {function} [reload]
 * @property {*} [state]
 * @property {function} [setState]
 * @property {string[]} [disabledFilters]
 */

/**
 * @param {string} type
 * @param {GetColumnOptions} options
 */
function getColumn (
  type,
  options
) {
  const {
    localizedString,
    reload,
    state,
    setState,
    disabledFilters = []
  } = options || {};
  /**
   * @type {{getColumnFilter: function, getColumn: function}}
   */
  let config = {
    getColumn: () => undefined,
    getColumnFilter: () => undefined
  };
  switch (type) {
    case Columns.docker:
      config = ColumnDocker;
      break;
    case Columns.elapsed:
      config = ColumnElapsed;
      break;
    case Columns.completed:
      config = ColumnEndDate;
      break;
    case Columns.links:
      config = ColumnLinks;
      break;
    case Columns.logs:
      config = ColumnLogs;
      break;
    case Columns.owner:
      config = ColumnOwner;
      break;
    case Columns.parent:
      config = ColumnParent;
      break;
    case Columns.pauseResume:
      config = ColumnPauseResume;
      break;
    case Columns.pipeline:
      config = ColumnPipeline;
      break;
    case Columns.run:
      config = ColumnRun;
      break;
    case Columns.started:
      config = ColumnStartDate;
      break;
    case Columns.stopReRun:
      config = ColumnStopReRun;
      break;
    case Columns.tags:
      config = ColumnTags;
      break;
    default:
      break;
  }
  const column = config.getColumn(localizedString, reload);
  if (column && !disabledFilters.includes(type)) {
    const filters = config.getColumnFilter(state, setState);
    const {
      filtered = false
    } = filters || {};
    const filteredValue = {
      filteredValue: filtered
        ? ['filtered']
        : [],
      filtered
    };
    return {
      ...column,
      ...filteredValue,
      ...(filters || {})
    };
  }
  return column;
}

const AllColumns = [
  Columns.run,
  Columns.tags,
  Columns.parent,
  Columns.pipeline,
  Columns.docker,
  Columns.started,
  Columns.completed,
  Columns.elapsed,
  Columns.owner,
  Columns.links,
  Columns.pauseResume,
  Columns.stopReRun,
  Columns.logs
];

/**
 * @param {string[]} columns
 * @param {GetColumnOptions} options
 */
function getColumns (
  columns,
  options
) {
  return (columns || [])
    .map((column) => getColumn(
      column,
      options
    ))
    .filter(Boolean);
}

export {
  AllColumns,
  Columns,
  ExpandColumn,
  getColumns,
  RUN_LOADING_PLACEHOLDER_PROPERTY,
  RUN_LOADING_ERROR_PROPERTY
};
