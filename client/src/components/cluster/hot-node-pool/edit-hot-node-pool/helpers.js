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

import moment from 'moment-timezone';
import {getFiltersError, filtersAreEqual} from '../filters-control';
import {compareSchedulesArray, scheduleIsValid} from './controls/ScheduleControl';
import {isReservationScheduled} from './controls/ReservationSummary';
import {
  DISK_MIN_SIZE,
  DISK_MAX_SIZE,
  COUNT_MIN_SIZE,
  MODE_FUTURE_DATED,
  COMMITMENT_DATE_TIME_FORMAT,
  RESERVATION_COUNT_MIN_SIZE,
  SECONDS_PER_DAY,
  MIN_COMMITMENT_DURATION_DAYS,
  MIN_RESERVATION_LEAD_DAYS,
  MAX_RESERVATION_LEAD_DAYS,
  RESERVATION_TYPE_FUTURE_DATED,
  RESERVATION_STATUS_PENDING,
  RESERVATION_STATUS_APPROVED,
  RESERVATION_TERMINAL_STATUSES
} from './constants';

// A new future-dated pool opens with its creator as the run owner - a default the
// user may edit or remove, not a derived or enforced value. It is the already
// backend-supported RUN_OWNER/EQUAL criterion, sent in the payload exactly like a
// hand-made filter.
export function buildRunOwnerFilter (userName) {
  return {
    operator: 'AND',
    filters: [{type: 'RUN_OWNER', operator: 'EQUAL', value: userName}]
  };
}

export function arraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const sA = new Set(a);
  const sB = new Set(b);
  if (sA.size !== sB.size) {
    return false;
  }
  for (let aa of sA) {
    if (!sB.has(aa)) {
      return false;
    }
  }
  return true;
}

export function getValidationErrors (values) {
  const {
    disk,
    instanceType,
    dockerImages,
    schedule,
    count,
    name,
    region,
    filters,
    autoscaled,
    minSize,
    maxSize,
    scaleDownThreshold,
    scaleUpThreshold,
    scaleStep,
    labels = [],
    mode,
    commitmentStart,
    commitmentEnd,
    commitmentDurationDays,
    isExistingReservation
  } = values;
  let diskError,
    instanceTypeError,
    dockerImagesError,
    scheduleError,
    countError,
    nameError,
    regionError,
    filtersError,
    minSizeError,
    maxSizeError,
    scaleDownThresholdError,
    scaleUpThresholdError,
    scaleStepError,
    labelsError,
    commitmentStartError,
    commitmentEndError,
    commitmentDurationError;
  if (
    Number.isNaN(Number(disk)) ||
    Number(disk) < DISK_MIN_SIZE ||
    Number(disk) > DISK_MAX_SIZE
  ) {
    diskError = `Disk should be a positive integer (${DISK_MIN_SIZE}...${DISK_MAX_SIZE})`;
  }
  if (!instanceType) {
    instanceTypeError = 'Instance type is required';
  }
  const images = (dockerImages || [])
    .filter(o => !o.removed)
    .map(o => (o.image || '').trim())
    .filter(Boolean);
  if ((new Set(images)).size < images.length) {
    dockerImagesError = 'Duplicates are not allowed';
  }
  if (schedule.map(scheduleIsValid).filter(o => !o).length > 0) {
    scheduleError = 'Invalid schedule';
  }
  if (!name) {
    nameError = 'Name is required';
  }
  if (!region) {
    regionError = 'Region is required';
  }
  filtersError = getFiltersError(filters);
  if (autoscaled) {
    if (
      !minSize ||
      Number.isNaN(minSize) ||
      Number(minSize) < 1
    ) {
      minSizeError = 'Min Size should be a positive integer larger than or equal 1';
    }
    if (
      !maxSize ||
      Number.isNaN(maxSize) ||
      Number(maxSize) < 1
    ) {
      maxSizeError = 'Max Size should be a positive integer larger than or equal 1';
    }
    if (
      !Number.isNaN(minSize) &&
      !Number.isNaN(maxSize) &&
      Number(minSize) > Number(maxSize)
    ) {
      if (!minSizeError) {
        minSizeError = 'Min Size should not be greater than Max Size';
      }
      if (!maxSizeError) {
        maxSizeError = 'Min Size should not be greater than Max Size';
      }
    }
    if (
      Number.isNaN(scaleUpThreshold) ||
      Number(scaleUpThreshold) < 0 ||
      Number(scaleUpThreshold) > 100
    ) {
      scaleUpThresholdError = 'Scale Up Threshold should be a positive (0..100%)';
    }
    if (
      Number.isNaN(scaleDownThreshold) ||
      Number(scaleDownThreshold) < 0 ||
      Number(scaleDownThreshold) > 100
    ) {
      scaleDownThresholdError = 'Scale Down Threshold should be a positive (0..100%)';
    }
    if (
      !Number.isNaN(scaleDownThreshold) &&
      !Number.isNaN(scaleUpThreshold) &&
      Number(scaleDownThreshold) >= Number(scaleUpThreshold)
    ) {
      if (!scaleDownThresholdError) {
        scaleDownThresholdError = 'Scale Up Threshold should be larger than Scale Down Threshold';
      }
      if (!scaleUpThresholdError) {
        scaleUpThresholdError = 'Scale Up Threshold should be larger than Scale Down Threshold';
      }
    }
    if (
      Number.isNaN(scaleStep) ||
      Number(scaleStep) < 1
    ) {
      scaleStepError = 'Scale Step should be a positive integer larger than or equal 1';
    }
  } else {
    if (
      Number.isNaN(Number(count)) ||
      Number(count) < COUNT_MIN_SIZE
    ) {
      countError = `Count should be a positive value or zero (pool is disabled)`;
    }
  }
  // Future-dated pools reserve a single one-time window. These are the backend's own
  // `NodePoolValidator` rules, pre-validated here so the user sees them before the
  // request. The minimum-32-vCPU and allowed-instance-family rules are intentionally
  // left to the backend: both need async lookups this form does not have.
  if (mode === MODE_FUTURE_DATED) {
    // An existing reservation's window cannot be changed through this endpoint, so
    // there is nothing to validate here, and SAVE must not be blocked by three fields
    // the form intentionally leaves empty for an existing reservation pool (the
    // requested duration is never echoed back by GET).
    if (!isExistingReservation) {
      const start = moment(commitmentStart, COMMITMENT_DATE_TIME_FORMAT, true);
      const end = moment(commitmentEnd, COMMITMENT_DATE_TIME_FORMAT, true);
      const now = moment();
      if (!commitmentStart || !start.isValid()) {
        commitmentStartError = 'Search from date is required';
      } else if (!start.isAfter(now)) {
        commitmentStartError = 'Search from date should be in the future';
      } else {
        // Matches `ChronoUnit.DAYS.between` on the backend: `Math.floor` on the
        // fractional diff, not a plain integer `diff(now, 'days')` (which truncates
        // toward zero and happens to agree here, but is not the same operation).
        const leadDays = Math.floor(start.diff(now, 'days', true));
        if (leadDays < MIN_RESERVATION_LEAD_DAYS || leadDays > MAX_RESERVATION_LEAD_DAYS) {
          commitmentStartError = 'Search from date should be between ' +
            `${MIN_RESERVATION_LEAD_DAYS} and ${MAX_RESERVATION_LEAD_DAYS} days from now`;
        }
      }
      if (!commitmentEnd || !end.isValid()) {
        commitmentEndError = 'Search until date is required';
      } else if (start.isValid() && !end.isAfter(start)) {
        commitmentEndError = 'Search until date should be after the search from date';
      }
      if (
        commitmentDurationDays === undefined ||
        commitmentDurationDays === null ||
        commitmentDurationDays === '' ||
        Number.isNaN(Number(commitmentDurationDays)) ||
        Number(commitmentDurationDays) <= 0
      ) {
        commitmentDurationError = 'Duration should be a positive number of days';
      } else if (!Number.isInteger(Number(commitmentDurationDays))) {
        commitmentDurationError = 'Duration should be a whole number of days';
      } else if (Number(commitmentDurationDays) < MIN_COMMITMENT_DURATION_DAYS) {
        commitmentDurationError = 'Duration should be at least ' +
          `${MIN_COMMITMENT_DURATION_DAYS} days`;
      } else if (
        start.isValid() &&
        end.isValid() &&
        end.isAfter(start) &&
        end.diff(start, 'seconds') < Number(commitmentDurationDays) * SECONDS_PER_DAY
      ) {
        commitmentDurationError = 'Duration should fit between "Search from" and "Search until"';
      }
    }
    // The backend enforces count >= 1 on the reservation request, but it then stores
    // the pool with count 0 until the reservation is activated - so an existing
    // future-dated pool legitimately loads with 0 and SAVE must not be blocked by it
    // (its Count input is disabled and round-trips unchanged anyway).
    if (!isExistingReservation && (Number.isNaN(Number(count)) ||
        Number(count) < RESERVATION_COUNT_MIN_SIZE)) {
      countError = 'Count should be at least 1 for a Future dated pool';
    }
  }
  const labelsNames = [...new Set(labels.map((lb) => (lb.name || '').trim().toLowerCase()))];
  if (labels.some((lb) => (lb.name || '').trim().length === 0)) {
    labelsError = 'There are invalid node labels';
  } else if (labelsNames.length < labels.length) {
    labelsError = 'There are duplicate node labels';
  }
  const valid = !diskError &&
    !instanceTypeError &&
    !dockerImagesError &&
    !scheduleError &&
    !countError &&
    !nameError &&
    !regionError &&
    !filtersError &&
    !minSizeError &&
    !maxSizeError &&
    !scaleDownThresholdError &&
    !scaleUpThresholdError &&
    !scaleStepError &&
    !labelsError &&
    !commitmentStartError &&
    !commitmentEndError &&
    !commitmentDurationError;
  return {
    valid,
    errors: {
      disk: diskError,
      instanceType: instanceTypeError,
      dockerImages: dockerImagesError,
      schedule: scheduleError,
      count: countError,
      name: nameError,
      region: regionError,
      filters: filtersError,
      minSize: minSizeError,
      maxSize: maxSizeError,
      scaleDownThreshold: scaleDownThresholdError,
      scaleUpThreshold: scaleUpThresholdError,
      scaleStep: scaleStepError,
      labelsError,
      commitmentStart: commitmentStartError,
      commitmentEnd: commitmentEndError,
      commitmentDurationDays: commitmentDurationError
    }
  };
}

export function isModified (values) {
  const {
    disk,
    initialDisk,
    instanceType,
    initialInstanceType,
    dockerImages,
    initialDockerImages,
    schedule,
    initialSchedule,
    count,
    initialCount,
    spot,
    initialSpot,
    instanceImage,
    initialInstanceImage,
    name,
    initialName,
    region,
    initialRegion,
    filters,
    initialFilters,
    autoscaled,
    initialAutoScaled,
    minSize,
    initialMinSize,
    maxSize,
    initialMaxSize,
    scaleDownThreshold,
    initialScaleDownThreshold,
    scaleUpThreshold,
    initialScaleUpThreshold,
    scaleStep,
    initialScaleStep
  } = values;
  return Number(disk) !== Number(initialDisk) ||
    Number(count) !== Number(initialCount) ||
    instanceType !== initialInstanceType ||
    !arraysAreEqual(
      dockerImages.filter(o => !o.removed).map(o => o.image),
      initialDockerImages
    ) ||
    !compareSchedulesArray(schedule, initialSchedule) ||
    spot !== initialSpot ||
    instanceImage !== initialInstanceImage ||
    name !== initialName ||
    region !== initialRegion ||
    !filtersAreEqual(filters, initialFilters) ||
    autoscaled !== initialAutoScaled ||
    (autoscaled && Number(minSize) !== Number(initialMinSize)) ||
    (autoscaled && Number(maxSize) !== Number(initialMaxSize)) ||
    (autoscaled && Number(scaleDownThreshold) !== Number(initialScaleDownThreshold)) ||
    (autoscaled && Number(scaleUpThreshold) !== Number(initialScaleUpThreshold)) ||
    (autoscaled && Number(scaleStep) !== Number(initialScaleStep));
}

// The form carries the duration in whole days (`commitmentDurationDays`), the unit a
// user reasons about; the backend's `commitmentDuration` field is seconds, so the
// conversion happens exactly once, here, on the way out. `instancePlatform`/`originId`
// are deliberately not sent (the backend defaults `instancePlatform` to `Linux/UNIX`).
export function buildCapacityReservationRequest (values) {
  const {commitmentStart, commitmentEnd, commitmentDurationDays} = values;
  return {
    reservationType: RESERVATION_TYPE_FUTURE_DATED,
    // Re-formatting through `moment` (rather than passing the string straight through)
    // normalizes whatever the picker produced into the backend's exact pattern,
    // including the mandatory `.SSS`. Never append a `Z` and never call `.utc()` — the
    // backend field is a zone-less local datetime.
    requestedStartDate: moment(commitmentStart, COMMITMENT_DATE_TIME_FORMAT, true)
      .format(COMMITMENT_DATE_TIME_FORMAT),
    requestedEndDate: moment(commitmentEnd, COMMITMENT_DATE_TIME_FORMAT, true)
      .format(COMMITMENT_DATE_TIME_FORMAT),
    commitmentDuration: Number(commitmentDurationDays) * SECONDS_PER_DAY
  };
}

export function buildPoolPayload (values) {
  const {
    mode,
    poolType,
    isExistingReservation,
    count,
    disk: instanceDisk,
    dockerImages,
    instanceType,
    name,
    spot: priceType,
    region: regionId,
    instanceImage,
    filters,
    autoscaled,
    minSize,
    maxSize,
    scaleDownThreshold,
    scaleUpThreshold,
    scaleStep,
    labels
  } = values;
  const payload = {
    count: autoscaled ? minSize : count,
    instanceDisk,
    dockerImages: dockerImages
      .filter(image => !image.removed)
      .map(image => image.image),
    instanceType,
    name,
    priceType,
    regionId,
    instanceImage,
    filter: filters,
    autoscaled,
    minSize: autoscaled ? minSize : undefined,
    maxSize: autoscaled ? maxSize : undefined,
    scaleDownThreshold: autoscaled ? scaleDownThreshold : undefined,
    scaleUpThreshold: autoscaled ? scaleUpThreshold : undefined,
    scaleStep: autoscaled ? scaleStep : undefined,
    kubeLabels: labels.reduce((acc, label) => ({
      ...acc,
      [label.name]: {
        value: label.value,
        monitored: label.monitored || false
      }
    }), {})
  };
  // `poolType` applies to every pool, not only a future-dated one, and the backend
  // only reads it on create — its UPDATE statement does not include the `pool_type`
  // column — so sending it unconditionally can never overwrite an existing pool's type.
  payload.poolType = poolType;
  if (mode === MODE_FUTURE_DATED) {
    // `NodePoolManager.update()` never reads `capacityReservationRequest` on an
    // existing reservation pool — the window cannot be changed through this
    // endpoint at all (a separate "extend" flow, out of scope) — so it is only ever
    // sent on create, never on an update of an already-reserved pool.
    if (!isExistingReservation) {
      payload.capacityReservationRequest = buildCapacityReservationRequest(values);
    }
  }
  return payload;
}

// Returns the pool-card badge status for a future-dated pool. The reservation record is
// the source of truth for its own lifecycle, so once one is available its `status` is
// returned verbatim - the ten-state backend enum (REQUIRED_APPROVE through FINISHED, plus
// the terminal failures) is for the caller to present, not to collapse here. When no record
// is provided (data still loading, endpoint returned nothing) the function falls back to
// inferring Approved/Pending from `pool.startDate`/`endDate` so the badge never goes blank.
// Returns `undefined` for a non-reservation pool.
export function getCapacityReservationStatus (pool, reservation) {
  const {isCapacityReservation, startDate, endDate} = pool || {};
  if (!isCapacityReservation) {
    return undefined;
  }
  if (reservation) {
    return reservation.status;
  }
  return isReservationScheduled(startDate, endDate)
    ? RESERVATION_STATUS_APPROVED
    : RESERVATION_STATUS_PENDING;
}

// The reserved count a matched reservation record carries is only real while that
// reservation still holds, or may still hold, capacity - a terminal status (see
// `RESERVATION_TERMINAL_STATUSES`) means it never will again, so neither the pool card
// nor the edit form may present its `instanceCount` as currently reserved.
export function getReservationDisplayInstanceCount (reservation) {
  if (!reservation || RESERVATION_TERMINAL_STATUSES.includes(reservation.status)) {
    return undefined;
  }
  const {instanceCount} = reservation;
  return instanceCount === null || instanceCount === undefined ? undefined : instanceCount;
}

export function buildSchedulePayload (values) {
  const {schedule, name} = values;
  return schedule.length > 0 ? {
    scheduleEntries: schedule.map(({id, ...s}) => s),
    name
  } : undefined;
}
