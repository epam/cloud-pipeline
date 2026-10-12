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
import {DatePicker} from 'antd';
import classNames from 'classnames';
import moment from 'moment-timezone';
import StringInput from './StringInput';
import HintControl from './HintControl';
import {COMMITMENT_DATE_TIME_FORMAT} from '../constants';
import styles from '../../edit-hot-node-pool.css';

// "Ends on" is derived, never independently editable: letting the user type it would
// allow it to contradict Duration. Returns undefined when either input is missing.
export function getCommitmentEnd (start, durationSeconds) {
  const duration = Number(durationSeconds);
  if (!start || !durationSeconds || Number.isNaN(duration) || duration <= 0) {
    return undefined;
  }
  const parsed = moment(start, COMMITMENT_DATE_TIME_FORMAT, true);
  if (!parsed.isValid()) {
    return undefined;
  }
  return parsed.add(duration, 'seconds').format(COMMITMENT_DATE_TIME_FORMAT);
}

// A GPU pool reserves capacity for a single one-time absolute window, unlike Default
// mode's weekly recurring schedule (see `ScheduleControl`/`ScheduleControls`, which this
// control does not reuse and must not touch). `start` is carried as a plain string in
// `COMMITMENT_DATE_TIME_FORMAT`, parsed/formatted with `moment` in the local zone so it
// round-trips; `duration` is a number of seconds. "Ends on" is read-only and derived.
function GpuScheduleControl ({
  start,
  duration,
  disabled,
  startError,
  durationError,
  touched,
  hint,
  onChangeStart,
  onChangeDuration
}) {
  const end = getCommitmentEnd(start, duration);
  return (
    <div>
      <div className={styles.formRow}>
        <span className={styles.label}>Starts on:</span>
        <DatePicker
          showTime
          format={COMMITMENT_DATE_TIME_FORMAT}
          placeholder="Start date and time"
          className={classNames(styles.formItem, styles.small)}
          disabled={disabled}
          value={start ? moment(start, COMMITMENT_DATE_TIME_FORMAT) : undefined}
          onChange={date => onChangeStart(
            date ? date.format(COMMITMENT_DATE_TIME_FORMAT) : undefined
          )}
        />
        <HintControl hint={hint} />
      </div>
      {
        startError && touched && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {startError}
          </div>
        )
      }
      <StringInput
        type="number"
        label="Duration (sec):"
        value={duration}
        min={1}
        disabled={disabled}
        error={durationError}
        touched={touched}
        onChange={onChangeDuration}
      />
      <div className={styles.formRow}>
        <span className={styles.label}>Ends on:</span>
        <span>{end || 'Not set'}</span>
      </div>
    </div>
  );
}

GpuScheduleControl.propTypes = {
  start: PropTypes.string,
  duration: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  disabled: PropTypes.bool,
  startError: PropTypes.string,
  durationError: PropTypes.string,
  touched: PropTypes.bool,
  hint: PropTypes.node,
  onChangeStart: PropTypes.func,
  onChangeDuration: PropTypes.func
};

export default GpuScheduleControl;
