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

import React from 'react';
import displayDuration from '../../../../../utils/displayDuration';
import {Link} from 'react-router';
import {Tooltip} from 'antd';
import classNames from 'classnames';
import StatusIcon from '../../../run-status-icon';
import styles from './runs-attribute.css';
import moment from 'moment-timezone';

const MAX_NESTED_RUNS_TO_DISPLAY = 1;

function sortByStartDate (runA, runB) {
  const {
    startDate: aStartDate
  } = runA;
  const {
    startDate: bStartDate
  } = runB;
  if (!bStartDate) {
    return -1;
  }
  if (!aStartDate) {
    return 1;
  }
  const a = moment.utc(aStartDate);
  const b = moment.utc(bStartDate);
  if (a.isBefore(b)) {
    return 1;
  }
  if (b.isBefore(a)) {
    return -1;
  }
  return 0;
}

function RunsAttribute (
  {
    className,
    value
  }
) {
  const runs = parseAttributeRunsValue(value).sort(sortByStartDate);
  if (runs.length === 0) {
    return null;
  }
  const total = runs.length;

  const renderSingleRun = function (run, index) {
    const {
      runId,
      status,
      startDate,
      endDate
    } = run;
    const duration = displayDuration(startDate, endDate);
    if (!runId || (index >= MAX_NESTED_RUNS_TO_DISPLAY && !this)) {
      return;
    }
    return (
      <Link
        key={index}
        className={
          classNames(
            className,
            styles.nestedRun,
            'cp-run-link'
          )
        }
        to={`/run/${runId}`}
      >
        <StatusIcon
          status={status}
          small
          displayTooltip={false}
        />
        <b className={styles.runId}>{runId}</b>
        {duration && <span className={styles.details}> {duration}</span>}
      </Link>
    );
  };

  const renderTooltip = (nestedRuns) => {
    return (
      <div className={styles.nestedRunsTooltip}>
        {nestedRuns.map(renderSingleRun, true)}
      </div>
    );
  };

  return (
    <div className={styles.nestedRuns}>
      {runs.map(renderSingleRun, false)}
      {total > MAX_NESTED_RUNS_TO_DISPLAY &&
      <Tooltip title={renderTooltip(runs)} placement="left">
        <Link
          className={styles.allNestedRuns}>
          +{total - MAX_NESTED_RUNS_TO_DISPLAY} more
        </Link>
      </Tooltip>}
    </div>
  );
}

export function parseAttributeRunsValue (value) {
  if (!value) {
    return [];
  }
  if (typeof value === 'object' && value.runId) {
    return [value];
  }
  if (typeof value === 'object' && value.length) {
    return (value || []).filter(obj => obj.runId);
  }
  if (typeof value === 'string') {
    try {
      const json = JSON.parse(value);
      if (typeof json === 'object') {
        return parseAttributeRunsValue(json);
      }
    } catch (_) {}
  }
  return [];
}

export function isRunsValue (value) {
  return parseAttributeRunsValue(value).length > 0;
}

export default RunsAttribute;
