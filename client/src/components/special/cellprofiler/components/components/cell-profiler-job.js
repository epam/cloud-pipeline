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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from '../cell-profiler.css';
import StatusIcon from '../../../run-status-icon';
import UserName from '../../../UserName';
import displayDate from '../../../../../utils/displayDate';
import Duration from './duration';

function CellProfilerJob (props) {
  const {
    className,
    job,
    style,
    onClick,
    pending
  } = props;
  if (!job) {
    return null;
  }
  let name = 'Batch analysis';
  const input = job.input ? job.input.path.split('/').pop() : undefined;
  if (job.alias) {
    name = job.alias;
  } else if (input) {
    name = input;
  }
  return (
    <div
      className={
        classNames(
          styles.cellProfilerJob,
          className,
          {
            'cp-text-not-important': pending
          }
        )
      }
      style={style}
      onClick={pending ? undefined : onClick}
    >
      <div className={styles.row}>
        <StatusIcon
          run={job.job}
          small
          tooltipPlacement="right"
        />
        <span
          className={styles.title}
          title={name}
        >
          {name}
        </span>
      </div>
      <div
        className={
          classNames(
            styles.row,
            styles.info
          )
        }
      >
        <UserName
          userName={job.owner}
          showIcon
          tooltipPlacement="right"
        />
        {
          job.startDate && (
            <span>
              {displayDate(job.startDate, 'D MMMM, YYYY, HH:mm')}
            </span>
          )
        }
        <Duration
          startDate={job.startDate}
          endDate={job.endDate}
        />
      </div>
    </div>
  );
}

CellProfilerJob.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  job: PropTypes.object,
  onClick: PropTypes.func,
  pending: PropTypes.bool
};

export default CellProfilerJob;
