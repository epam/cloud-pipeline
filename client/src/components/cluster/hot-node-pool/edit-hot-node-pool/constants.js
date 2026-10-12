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
export const MODE_GPU = 'GPU';
export const POOL_TYPE_STANDARD = 'STANDARD';
// The literal is the backend `NodePoolType` enum value, deliberately spelled without a second "e".
export const POOL_TYPE_SHAREABLE = 'SHARABLE_NODE';
// The backend's `LocalDateTime` pattern (`JsonMapper`/`Constants`): milliseconds required,
// no `T`, no timezone.
export const COMMITMENT_DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';

export const GPU_COUNT_MIN_SIZE = 1; // reservation pools cannot be "disabled"
export const MIN_COMMITMENT_DURATION_SECONDS = 1209600; // 14 days — NodePoolValidator
export const MIN_RESERVATION_LEAD_DAYS = 5; // NodePoolValidator.MINIMUM_LEAD_DAYS
export const MAX_RESERVATION_LEAD_DAYS = 120; // NodePoolValidator.MAX_RESERVATION_LEAD_DAYS
export const RESERVATION_TYPE_FUTURE_DATED = 'FUTURE_DATED'; // the only type the backend accepts
