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
import {Alert, DatePicker} from 'antd';
import classNames from 'classnames';
import moment from 'moment-timezone';
import StringInput from './StringInput';
import HintControl from './HintControl';
import {humanizeReservationStatus} from '../helpers';
import {
  COMMITMENT_DATE_TIME_FORMAT,
  MIN_COMMITMENT_DURATION_DAYS,
  RESERVATION_STATUS_PENDING,
  RESERVATION_STATUS_REQUIRED_APPROVE,
  RESERVATION_STATUS_APPROVED,
  RESERVATION_STATUS_ASSESSING,
  RESERVATION_STATUS_SCHEDULED,
  RESERVATION_STATUS_ACTIVE,
  RESERVATION_STATUS_FINALIZING,
  RESERVATION_STATUS_FINISHED,
  RESERVATION_STATUS_PURCHASE_FAILED,
  RESERVATION_STATUS_FAILED,
  RESERVATION_STATUS_CANCELLED
} from '../constants';
import styles from '../../edit-hot-node-pool.css';

// Keyed by `getCapacityReservationStatus`'s return value, exactly as `pool-card.js` keys
// its own presentation map - the reservation record is the source of truth for its own
// lifecycle, so the notice tells the user what is actually happening instead of always
// assuming a brand new pool still waiting on the capacity reservation service.
const RESERVATION_STATUS_NOTICE = {
  [RESERVATION_STATUS_REQUIRED_APPROVE]: {
    type: 'info',
    message: 'This reservation is waiting for an administrator\'s approval.'
  },
  [RESERVATION_STATUS_PENDING]: {
    type: 'info',
    message: 'This pool is waiting to be scheduled by the capacity reservation service.'
  },
  [RESERVATION_STATUS_APPROVED]: {
    type: 'info',
    message: 'This reservation is approved and waiting to be placed with the cloud provider.'
  },
  [RESERVATION_STATUS_ASSESSING]: {
    type: 'info',
    message: 'The cloud provider is looking for capacity inside this window.'
  },
  [RESERVATION_STATUS_SCHEDULED]: {
    type: 'info',
    message: 'This reservation is scheduled. Its granted window is not available here yet.'
  },
  [RESERVATION_STATUS_ACTIVE]: {
    type: 'info',
    message: 'This reservation is active.'
  },
  [RESERVATION_STATUS_FINALIZING]: {
    type: 'info',
    message: 'This reservation is finishing.'
  },
  [RESERVATION_STATUS_FINISHED]: {
    type: 'warning',
    message: 'This reservation has ended.'
  },
  [RESERVATION_STATUS_PURCHASE_FAILED]: {
    type: 'error',
    message: 'The cloud provider could not place this reservation.'
  },
  [RESERVATION_STATUS_FAILED]: {
    type: 'error',
    message: 'This reservation failed.'
  },
  [RESERVATION_STATUS_CANCELLED]: {
    type: 'warning',
    message: 'This reservation was cancelled.'
  }
};

// A status this map does not know (an older client against a newer backend release) must
// not blank the section or throw: fall back to a neutral notice with a label humanised
// from the raw value.
function getReservationStatusNotice (status) {
  return RESERVATION_STATUS_NOTICE[status] || {
    type: 'info',
    message: `Reservation status: ${humanizeReservationStatus(status)}.`
  };
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
  reservationStatus,
  onChangeStart,
  onChangeEnd,
  onChangeDuration
}) {
  const notice = reservationStatus ? getReservationStatusNotice(reservationStatus) : undefined;
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
        notice && (
          <div className={styles.formRow}>
            <Alert type={notice.type} showIcon message={notice.message} />
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
  reservationStatus: PropTypes.string,
  onChangeStart: PropTypes.func,
  onChangeEnd: PropTypes.func,
  onChangeDuration: PropTypes.func
};

export default ReservationScheduleControl;
