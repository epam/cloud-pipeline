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
import {formatCloudCommitmentDate, isReservationScheduled} from '../helpers';
import styles from '../../edit-hot-node-pool.css';

// Exported for its own unit tests.
export function formatReservationDate (value) {
  return formatCloudCommitmentDate(value, 'YYYY-MM-DD HH:mm:ss');
}

// The backend's scheduler grants this window and `/cluster/pool` cannot change it, so it is
// read-only text; `startDate`/`endDate` are null until it is scheduled.
function ReservationSummary ({start, end}) {
  const formattedStart = formatReservationDate(start);
  const formattedEnd = formatReservationDate(end);
  const text = isReservationScheduled(start, end)
    ? `${formattedStart} – ${formattedEnd}`
    : 'Pending approval (not yet scheduled)';
  return (
    <div className={styles.formRow}>
      <span className={styles.label}>Reservation window:</span>
      <span>{text}</span>
    </div>
  );
}

ReservationSummary.propTypes = {
  start: PropTypes.string,
  end: PropTypes.string
};

export default ReservationSummary;
