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
import {humanizeReservationStatus, formatCloudCommitmentDate} from '../helpers';
import {
  COMMITMENT_DATE_ONLY_FORMAT,
  COMMITMENT_DATE_TIME_FORMAT,
  MIN_COMMITMENT_DURATION_DAYS,
  MIN_ML_BLOCK_DURATION_DAYS,
  MODE_FUTURE_DATED,
  MODE_ML_BLOCK,
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

// Keyed by `getCapacityReservationStatus`'s return value, as `pool-card.js` keys its own
// map - the notice follows the reservation record rather than assuming it's still pending.
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

// A status this map does not know (older client, newer backend) must not blank the
// section or throw - falls back to a neutral notice humanised from the raw value.
function getReservationStatusNotice (status) {
  return RESERVATION_STATUS_NOTICE[status] || {
    type: 'info',
    message: `Reservation status: ${humanizeReservationStatus(status)}.`
  };
}

// An echoed value from the API is a true UTC instant; a day typed into the picker never
// carries a time and needs no conversion.
function toDisplayDay (value) {
  if (!value) {
    return value;
  }
  const isInstant = moment.utc(value, COMMITMENT_DATE_TIME_FORMAT, true).isValid();
  return isInstant ? formatCloudCommitmentDate(value, COMMITMENT_DATE_ONLY_FORMAT) : value;
}

// A future-dated pool reserves one absolute window, unlike Default mode's weekly
// schedule - this control does not reuse `ScheduleControl` and must not touch it.

// "Search until" is independently editable, never derived from `start` + `durationDays`.
function ReservationScheduleControl ({
  mode,
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
  onChangeDuration,
  children
}) {
  const notice = reservationStatus ? getReservationStatusNotice(reservationStatus) : undefined;
  const durationMin = mode === MODE_ML_BLOCK
    ? MIN_ML_BLOCK_DURATION_DAYS
    : MIN_COMMITMENT_DURATION_DAYS;
  return (
    <div>
      <div className={styles.formRow}>
        <span className={styles.label}>Search from:</span>
        <DatePicker
          format={COMMITMENT_DATE_ONLY_FORMAT}
          placeholder="Start date"
          className={classNames(styles.formItem, styles.small)}
          disabled={disabled}
          value={
            start
              ? moment(toDisplayDay(start), COMMITMENT_DATE_ONLY_FORMAT, true)
              : undefined
          }
          onChange={date => onChangeStart(
            date ? date.format(COMMITMENT_DATE_ONLY_FORMAT) : undefined
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
          format={COMMITMENT_DATE_ONLY_FORMAT}
          placeholder="End date"
          className={classNames(styles.formItem, styles.small)}
          disabled={disabled}
          value={
            end
              ? moment(toDisplayDay(end), COMMITMENT_DATE_ONLY_FORMAT, true)
              : undefined
          }
          onChange={date => onChangeEnd(
            date ? date.format(COMMITMENT_DATE_ONLY_FORMAT) : undefined
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
        min={durationMin}
        disabled={disabled}
        hint={durationHint}
        error={durationError}
        touched={touched}
        onChange={onChangeDuration}
      />
      {children}
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
  mode: PropTypes.string,
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
  onChangeDuration: PropTypes.func,
  children: PropTypes.node
};

ReservationScheduleControl.defaultProps = {
  mode: MODE_FUTURE_DATED
};

export default ReservationScheduleControl;
