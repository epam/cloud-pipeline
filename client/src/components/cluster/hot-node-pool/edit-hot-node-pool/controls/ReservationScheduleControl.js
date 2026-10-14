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
import {
  COMMITMENT_DATE_TIME_FORMAT,
  SECONDS_PER_DAY,
  MIN_COMMITMENT_DURATION_DAYS
} from '../constants';
import styles from '../../edit-hot-node-pool.css';

// undefined when any input is missing/unparseable (no hint is shown then);
// true/false when all three are usable.
export function windowCoversDuration (start, end, durationDays) {
  const duration = Number(durationDays);
  if (
    durationDays === undefined || durationDays === null || durationDays === '' ||
    Number.isNaN(duration) || duration <= 0
  ) {
    return undefined;
  }
  const startMoment = moment(start, COMMITMENT_DATE_TIME_FORMAT, true);
  const endMoment = moment(end, COMMITMENT_DATE_TIME_FORMAT, true);
  if (!startMoment.isValid() || !endMoment.isValid()) {
    return undefined;
  }
  return endMoment.diff(startMoment, 'seconds') >= duration * SECONDS_PER_DAY;
}

// A future-dated pool reserves capacity for a single one-time absolute window, unlike Default
// mode's weekly recurring schedule (see `ScheduleControl`/`ScheduleControls`, which this
// control does not reuse and must not touch). `start`/`end` are carried as plain strings
// in `COMMITMENT_DATE_TIME_FORMAT`, parsed/formatted with `moment` in the local zone so
// they round-trip; `[start, end]` is the window the platform *searches* in for matching
// capacity, and `durationDays` the committed length, in days, that must fit somewhere
// inside it - converted to the backend's seconds only in
// `buildCapacityReservationRequest`. "Search until" is independently editable and never
// derived from `start` + `durationDays`.
function ReservationScheduleControl ({
  start,
  end,
  durationDays,
  disabled,
  startError,
  endError,
  durationError,
  touched,
  startHint,
  endHint,
  durationHint,
  onChangeStart,
  onChangeEnd,
  onChangeDuration
}) {
  const covers = windowCoversDuration(start, end, durationDays);
  return (
    <div>
      <div className={styles.formRow}>
        <span className={styles.label}>Search from:</span>
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
        <HintControl hint={startHint} />
      </div>
      {
        startError && touched && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {startError}
          </div>
        )
      }
      <div className={styles.formRow}>
        <span className={styles.label}>Search until:</span>
        <DatePicker
          showTime
          format={COMMITMENT_DATE_TIME_FORMAT}
          placeholder="End date and time"
          className={classNames(styles.formItem, styles.small)}
          disabled={disabled}
          value={end ? moment(end, COMMITMENT_DATE_TIME_FORMAT) : undefined}
          onChange={date => onChangeEnd(
            date ? date.format(COMMITMENT_DATE_TIME_FORMAT) : undefined
          )}
        />
        <HintControl hint={endHint} />
      </div>
      {
        endError && touched && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {endError}
          </div>
        )
      }
      <StringInput
        type="number"
        label="Duration (days):"
        value={durationDays}
        min={MIN_COMMITMENT_DURATION_DAYS}
        disabled={disabled}
        hint={durationHint}
        error={durationError}
        touched={touched}
        onChange={onChangeDuration}
      />
      {
        covers === true && (
          <div className={styles.formRow}>
            <span>Reservation window covers this duration</span>
          </div>
        )
      }
      {
        covers === false && (
          <div className={styles.formRow}>
            <span>This duration does not fit in the selected window</span>
          </div>
        )
      }
    </div>
  );
}

ReservationScheduleControl.propTypes = {
  start: PropTypes.string,
  end: PropTypes.string,
  durationDays: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  disabled: PropTypes.bool,
  startError: PropTypes.string,
  endError: PropTypes.string,
  durationError: PropTypes.string,
  touched: PropTypes.bool,
  startHint: PropTypes.node,
  endHint: PropTypes.node,
  durationHint: PropTypes.node,
  onChangeStart: PropTypes.func,
  onChangeEnd: PropTypes.func,
  onChangeDuration: PropTypes.func
};

export default ReservationScheduleControl;
