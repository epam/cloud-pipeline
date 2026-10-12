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
import moment from 'moment-timezone';
import {COMMITMENT_DATE_TIME_FORMAT} from '../constants';
import styles from '../../edit-hot-node-pool.css';

// Exported for its own unit tests.
export function formatReservationDate (value) {
  if (!value) {
    return undefined;
  }
  // Non-strict: the backend may hand back a value whose formatting differs
  // slightly from the wire format this client sends.
  const parsed = moment(value, COMMITMENT_DATE_TIME_FORMAT);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : undefined;
}

// A reservation's window is granted by the backend's scheduler and is not editable
// through `/cluster/pool`, so an existing reservation pool shows it as read-only text
// instead of rendering `GpuScheduleControl`; `startDate`/`endDate` are null until the
// reservation is actually scheduled.
function GpuReservationSummary ({start, end}) {
  const formattedStart = formatReservationDate(start);
  const formattedEnd = formatReservationDate(end);
  const text = formattedStart && formattedEnd
    ? `${formattedStart} – ${formattedEnd}`
    : 'Pending approval (not yet scheduled)';
  return (
    <div className={styles.formRow}>
      <span className={styles.label}>Reservation window:</span>
      <span>{text}</span>
    </div>
  );
}

GpuReservationSummary.propTypes = {
  start: PropTypes.string,
  end: PropTypes.string
};

export default GpuReservationSummary;
