/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

export const DISK_MIN_SIZE = 15;
export const DISK_MAX_SIZE = 15360;

export const COUNT_MIN_SIZE = 0;

export const MODE_DEFAULT = 'DEFAULT';
export const MODE_FUTURE_DATED = 'FUTURE_DATED';
export const POOL_TYPE_STANDARD = 'STANDARD';
// The literal is the backend `NodePoolType` enum value.
export const POOL_TYPE_SHAREABLE = 'SHAREABLE_NODE';
// The backend's `LocalDateTime` pattern (`JsonMapper`/`Constants`): milliseconds required,
// no `T`, no timezone.
export const COMMITMENT_DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
// The reservation search window is picked by day. This is what the control shows and the
// state holds; `COMMITMENT_DATE_TIME_FORMAT` stays the wire format the day is converted to.
export const COMMITMENT_DATE_ONLY_FORMAT = 'YYYY-MM-DD';

export const RESERVATION_COUNT_MIN_SIZE = 1; // reservation pools cannot be "disabled"
export const SECONDS_PER_DAY = 24 * 60 * 60;
// The duration is entered in whole days and sent in seconds: `NodePoolValidator`
// requires at least 1209600 seconds, i.e. these 14 days.
export const MIN_COMMITMENT_DURATION_DAYS = 14;
export const MIN_RESERVATION_LEAD_DAYS = 5; // NodePoolValidator.MINIMUM_LEAD_DAYS
export const MAX_RESERVATION_LEAD_DAYS = 120; // NodePoolValidator.MAX_RESERVATION_LEAD_DAYS
export const RESERVATION_TYPE_FUTURE_DATED = 'FUTURE_DATED'; // the only type the backend accepts

// Client-only label for a future-dated reservation pool's approval state before any
// reservation record exists (there is no backend enum for this — it is derived from
// whether `startDate`/`endDate` have been granted yet).
export const RESERVATION_STATUS_PENDING = 'PENDING';

// These mirror the backend's `CapacityReservationStatus` enum verbatim, so a reservation
// record's own `status` can be used as the pool-card badge key directly.
export const RESERVATION_STATUS_REQUIRED_APPROVE = 'REQUIRED_APPROVE';
export const RESERVATION_STATUS_APPROVED = 'APPROVED';
export const RESERVATION_STATUS_ASSESSING = 'ASSESSING_BY_CLOUD_PROVIDER';
export const RESERVATION_STATUS_SCHEDULED = 'SCHEDULED';
export const RESERVATION_STATUS_ACTIVE = 'ACTIVE';
export const RESERVATION_STATUS_FINALIZING = 'FINALIZING';
export const RESERVATION_STATUS_FINISHED = 'FINISHED';
export const RESERVATION_STATUS_PURCHASE_FAILED = 'PURCHASE_FAILED';
export const RESERVATION_STATUS_FAILED = 'FAILED';
export const RESERVATION_STATUS_CANCELLED = 'CANCELLED';

// Mirrors the backend's `CapacityReservationStatus.terminalStatuses()`: once a reservation
// reaches one of these it will never again hold capacity, so a count sourced from it must
// not be presented as currently reserved.
export const RESERVATION_TERMINAL_STATUSES = [
  RESERVATION_STATUS_PURCHASE_FAILED,
  RESERVATION_STATUS_FINISHED,
  RESERVATION_STATUS_FAILED,
  RESERVATION_STATUS_CANCELLED
];

export const MODE_ML_BLOCK = 'ML_BLOCK';
// The backend `CapacityReservationType` enum value.
export const RESERVATION_TYPE_CAPACITY_BLOCK = 'CAPACITY_BLOCK';
// The only `CapacityBlockResourceType` the backend accepts today.
export const CAPACITY_BLOCK_RESOURCE_TYPE_INSTANCE = 'INSTANCE';
// AWS sells a block by the hour; the form asks for whole days, and one day is the floor.
export const MIN_ML_BLOCK_DURATION_DAYS = 1;
export const HOURS_PER_DAY = 24;
export const SECONDS_PER_HOUR = 60 * 60;
// A nodes count is typed digit by digit, so the search waits for the typing to settle.
export const ML_BLOCK_OFFERINGS_FETCH_DELAY_MS = 500;
// Shared by index.js's own fallback and the candidates control's defensive one, so the
// two can never read differently for the same failure.
export const ML_BLOCK_OFFERINGS_ERROR_MESSAGE = 'Error searching for capacity block offerings';
