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
  MODE_ML_BLOCK,
  COMMITMENT_DATE_TIME_FORMAT,
  RESERVATION_COUNT_MIN_SIZE,
  SECONDS_PER_DAY,
  MIN_COMMITMENT_DURATION_DAYS,
  MIN_RESERVATION_LEAD_DAYS,
  MAX_RESERVATION_LEAD_DAYS,
  RESERVATION_TYPE_FUTURE_DATED,
  RESERVATION_TYPE_CAPACITY_BLOCK,
  CAPACITY_BLOCK_RESOURCE_TYPE_INSTANCE,
  MIN_ML_BLOCK_DURATION_DAYS,
  HOURS_PER_DAY,
  SECONDS_PER_HOUR,
  RESERVATION_STATUS_PENDING,
  RESERVATION_STATUS_APPROVED,
  RESERVATION_STATUS_REQUIRED_APPROVE,
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

// The two fields the offering search sends per candidate.
function isCompleteMlBlockCandidate (candidate) {
  const {instanceType, instanceCount} = candidate || {};
  return Boolean((instanceType || '').trim()) &&
    Number.isInteger(Number(instanceCount)) &&
    Number(instanceCount) >= 1;
}

export function getCompleteMlBlockCandidates (candidates) {
  return (candidates || []).filter(isCompleteMlBlockCandidate);
}

// One AWS call per candidate, but a flat response list - `getOfferingsForCandidate`
// attributes a result back to its row by this same `instanceType` string.
export function buildCapacityBlockOfferingRequest (values) {
  const {
    region,
    commitmentStart,
    commitmentEnd,
    commitmentDurationDays,
    mlBlockCandidates
  } = values;
  const request = {
    regionId: Number(region),
    resourceType: CAPACITY_BLOCK_RESOURCE_TYPE_INSTANCE,
    candidates: getCompleteMlBlockCandidates(mlBlockCandidates)
      .map(({instanceType, instanceCount}) => ({
        instanceType,
        instanceCount: Number(instanceCount)
      })),
    startDateRange: moment(commitmentStart, COMMITMENT_DATE_TIME_FORMAT, true)
      .format(COMMITMENT_DATE_TIME_FORMAT),
    capacityDurationHours: Number(commitmentDurationDays) * HOURS_PER_DAY
  };
  const end = moment(commitmentEnd, COMMITMENT_DATE_TIME_FORMAT, true);
  if (commitmentEnd && end.isValid()) {
    request.endDateRange = end.format(COMMITMENT_DATE_TIME_FORMAT);
  }
  return request;
}

export function canSearchCapacityBlockOfferings (values) {
  const {region, commitmentStart, commitmentDurationDays, mlBlockCandidates} = values;
  if (!region) {
    return false;
  }
  const start = moment(commitmentStart, COMMITMENT_DATE_TIME_FORMAT, true);
  if (!commitmentStart || !start.isValid()) {
    return false;
  }
  const durationDays = Number(commitmentDurationDays);
  if (!Number.isInteger(durationDays) || durationDays < MIN_ML_BLOCK_DURATION_DAYS) {
    return false;
  }
  return getCompleteMlBlockCandidates(mlBlockCandidates).length > 0;
}

export function getOfferingsForCandidate (offerings, candidate) {
  const type = ((candidate || {}).instanceType || '').toLowerCase();
  if (!type) {
    return [];
  }
  return (offerings || []).filter(o => (o.instanceType || '').toLowerCase() === type);
}

export function getSelectedOffering (values) {
  const {mlBlockOfferings, mlBlockOfferingId} = values;
  if (!mlBlockOfferingId) {
    return undefined;
  }
  return (mlBlockOfferings || []).find(o => o.offeringId === mlBlockOfferingId);
}

// The catalogue reports memory in MiB; this line shows it in GB.
export function formatCapacityBlockInstanceType (info) {
  const {instanceType, vCPU, memoryInMiB, gpuCount, gpuDeviceName} = info || {};
  const parts = [instanceType];
  if (gpuCount) {
    parts.push(gpuDeviceName ? `${gpuCount} x ${gpuDeviceName}` : `${gpuCount} GPU`);
  }
  if (vCPU !== undefined && vCPU !== null) {
    parts.push(`${vCPU} vCPU`);
  }
  if (memoryInMiB !== undefined && memoryInMiB !== null) {
    parts.push(`${(memoryInMiB / 1024).toFixed(1)} GB`);
  }
  return parts.filter(Boolean).join(', ');
}

// Shared by the future-dated and ML Block branches below, which differ only in the
// minimum duration and whether a lead-time window applies to the start date.
function getCommitmentWindowErrors (values, minDurationDays, leadTime) {
  const {commitmentStart, commitmentEnd, commitmentDurationDays} = values;
  const start = moment(commitmentStart, COMMITMENT_DATE_TIME_FORMAT, true);
  const end = moment(commitmentEnd, COMMITMENT_DATE_TIME_FORMAT, true);
  const now = moment();
  let commitmentStartError, commitmentEndError, commitmentDurationError;
  if (!commitmentStart || !start.isValid()) {
    commitmentStartError = 'Search from date is required';
  } else if (!start.isAfter(now)) {
    commitmentStartError = 'Search from date should be in the future';
  } else if (leadTime) {
    // Matches `ChronoUnit.DAYS.between` on the backend: `Math.floor` on the
    // fractional diff, not a plain integer `diff(now, 'days')` (which truncates
    // toward zero and happens to agree here, but is not the same operation).
    const leadDays = Math.floor(start.diff(now, 'days', true));
    if (leadDays < leadTime.min || leadDays > leadTime.max) {
      commitmentStartError = 'Search from date should be between ' +
        `${leadTime.min} and ${leadTime.max} days from now`;
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
  } else if (Number(commitmentDurationDays) < minDurationDays) {
    commitmentDurationError = 'Duration should be at least ' +
      `${minDurationDays} day${minDurationDays === 1 ? '' : 's'}`;
  } else if (
    start.isValid() &&
    end.isValid() &&
    end.isAfter(start) &&
    end.diff(start, 'seconds') < Number(commitmentDurationDays) * SECONDS_PER_DAY
  ) {
    commitmentDurationError = 'Duration should fit between "Search from" and "Search until"';
  }
  return {commitmentStartError, commitmentEndError, commitmentDurationError};
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
    mlBlockCandidates,
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
    commitmentDurationError,
    mlBlockCandidatesError,
    mlBlockOfferingError;
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
      ({commitmentStartError, commitmentEndError, commitmentDurationError} =
        getCommitmentWindowErrors(values, MIN_COMMITMENT_DURATION_DAYS, {
          min: MIN_RESERVATION_LEAD_DAYS,
          max: MAX_RESERVATION_LEAD_DAYS
        }));
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
  // Nothing about a purchased block is editable, so none of this runs once the pool
  // already holds one.
  if (mode === MODE_ML_BLOCK && !isExistingReservation) {
    ({commitmentStartError, commitmentEndError, commitmentDurationError} =
      getCommitmentWindowErrors(values, MIN_ML_BLOCK_DURATION_DAYS));
    const candidates = mlBlockCandidates || [];
    const hasIncompleteTypedRow = candidates.some(c =>
      (c.instanceType || '').trim() && !isCompleteMlBlockCandidate(c)
    );
    if (hasIncompleteTypedRow) {
      mlBlockCandidatesError = 'Specify the nodes count for every instance type';
    } else if (getCompleteMlBlockCandidates(candidates).length === 0) {
      mlBlockCandidatesError = 'Add at least one instance type';
    }
    if (!getSelectedOffering(values)) {
      mlBlockOfferingError = 'Select one of the offerings';
    }
    // `count`/`instanceType` are filled from the selected offering, so this only
    // fails while nothing is selected.
    if (Number.isNaN(Number(count)) || Number(count) < RESERVATION_COUNT_MIN_SIZE) {
      countError = 'Count should be at least 1 for an ML Block pool';
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
    !commitmentDurationError &&
    !mlBlockCandidatesError &&
    !mlBlockOfferingError;
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
      commitmentDurationDays: commitmentDurationError,
      mlBlockCandidates: mlBlockCandidatesError,
      mlBlockOffering: mlBlockOfferingError
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
  const {mode} = values;
  if (mode === MODE_ML_BLOCK) {
    // The offering has already fixed the window, so its own dates go out verbatim -
    // `CapacityReservationService.build()` copies them straight into the reservation.
    const offering = getSelectedOffering(values) || {};
    const {offeringId, availabilityZone, startDate, endDate, capacityDurationHours} = offering;
    return {
      reservationType: RESERVATION_TYPE_CAPACITY_BLOCK,
      capacityBlockOfferingId: offeringId,
      availabilityZone,
      requestedStartDate: startDate,
      requestedEndDate: endDate,
      commitmentDuration: Number(capacityDurationHours) * SECONDS_PER_HOUR
    };
  }
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
  if (mode === MODE_FUTURE_DATED || mode === MODE_ML_BLOCK) {
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

// A status none of the presentation maps keyed off `getCapacityReservationStatus` knows
// (an older client against a newer backend release) must still read as something rather
// than blank out or throw, so they fall back to the raw enum value humanised here:
// `SOME_NEW_STATUS` -> "Some new status".
export function humanizeReservationStatus (status) {
  const words = `${status}`.toLowerCase().split('_').filter(Boolean);
  if (!words.length) {
    return status;
  }
  return `${words[0][0].toUpperCase()}${words[0].substring(1)} ${words.slice(1).join(' ')}`
    .trim();
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

// Mirrors the backend's `CapacityReservationService#onPoolDeletion`: deleting a pool gives
// its reservation up too, and the backend refuses that implicit cancellation - unless the
// caller forces it - while the reservation is still open to anything but these two
// outcomes: a terminal status (see `RESERVATION_TERMINAL_STATUSES`), where there is
// nothing left to give up, or `REQUIRED_APPROVE`, where there is nothing yet committed
// with the cloud provider. Anywhere else (including `ACTIVE`) the capacity is still held,
// and giving it up may still cost money, which is why the client asks for the same consent
// the backend requires - from an admin exactly as much as from the pool's own owner.
//
// A pool that is not a capacity reservation at all (a `Default` pool) never needs this -
// the backend's own check short-circuits there too. Nor does one with no reservation
// record to find: the backend's own check finds nothing to cancel and returns early, so
// there is nothing for the client to hold the caller to either.
const RESERVATION_SAFE_TO_DELETE_STATUSES = [
  ...RESERVATION_TERMINAL_STATUSES,
  RESERVATION_STATUS_REQUIRED_APPROVE
];

export function needsForceToDeletePool (pool, reservation) {
  const {isCapacityReservation} = pool || {};
  if (!isCapacityReservation || !reservation) {
    return false;
  }
  return !RESERVATION_SAFE_TO_DELETE_STATUSES.includes(reservation.status);
}

export function buildSchedulePayload (values) {
  const {schedule, name} = values;
  return schedule.length > 0 ? {
    scheduleEntries: schedule.map(({id, ...s}) => s),
    name
  } : undefined;
}
