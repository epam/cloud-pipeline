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
import {
  DISK_MIN_SIZE,
  DISK_MAX_SIZE,
  COUNT_MIN_SIZE,
  MODE_FUTURE_DATED,
  MODE_ML_BLOCK,
  COMMITMENT_DATE_TIME_FORMAT,
  COMMITMENT_DATE_ONLY_FORMAT,
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

// An editable default for a new reservation pool; the backend already supports RUN_OWNER/EQUAL.
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

// The one place a picked day becomes the UTC instant the backend receives. Plain `moment()`,
// not `.local()`, which ignores `moment.tz.setDefault`.
function getCommitmentDayStart (day) {
  const parsed = moment(day, COMMITMENT_DATE_ONLY_FORMAT, true);
  return parsed.isValid() ? parsed.startOf('day').utc() : parsed;
}

// Same parsing as `getCommitmentDayStart`, anchored to the day's end instead.
function getCommitmentDayEnd (day) {
  const parsed = moment(day, COMMITMENT_DATE_ONLY_FORMAT, true);
  return parsed.isValid() ? parsed.endOf('day').utc() : parsed;
}

// Windows arrive as a zone-less UTC wall time (`AwsCapacityReservationService.toLocalDateTime`),
// so they are parsed as UTC and rendered in the viewer's own zone.
export function formatCloudCommitmentDate (value, format) {
  if (!value) {
    return undefined;
  }
  // Non-strict: the backend's formatting may differ slightly from the wire format sent.
  const parsed = moment.utc(value, COMMITMENT_DATE_TIME_FORMAT);
  if (!parsed.isValid()) {
    return undefined;
  }
  // `.local()` reads the OS zone, not `moment.tz.setDefault`, so this converts through a
  // plain `Date` instead - the same workaround `displayDate.js` uses.
  return moment(parsed.toDate()).format(format);
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

// The response is one flat list, which `getOfferingsForCandidate` attributes back to a row
// by this same `instanceType` string.
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
    startDateRange: getCommitmentDayStart(commitmentStart).format(COMMITMENT_DATE_TIME_FORMAT),
    capacityDurationHours: Number(commitmentDurationDays) * HOURS_PER_DAY
  };
  const end = getCommitmentDayEnd(commitmentEnd);
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
  const start = moment(commitmentStart, COMMITMENT_DATE_ONLY_FORMAT, true);
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

// The selection is one value shared by the whole form, so finding it spans every row.
export function getAllMlBlockOfferings (offeringsByCandidate) {
  return Object.values(offeringsByCandidate || {})
    .reduce((all, entry) => all.concat((entry || {}).offerings || []), []);
}

// A selection is dropped when its owning row is searched again; this is that ownership check.
export function offeringBelongsToCandidate (offeringsByCandidate, id, offeringId) {
  if (!offeringId) {
    return false;
  }
  const entry = (offeringsByCandidate || {})[id];
  return Boolean(entry && (entry.offerings || []).some(o => o.offeringId === offeringId));
}

export function getSelectedOffering (values) {
  const {mlBlockOfferingsByCandidate, mlBlockOfferingId} = values;
  if (!mlBlockOfferingId) {
    return undefined;
  }
  return getAllMlBlockOfferings(mlBlockOfferingsByCandidate)
    .find(o => o.offeringId === mlBlockOfferingId);
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
    const memoryInGb = (memoryInMiB / 1024).toFixed(1).replace(/\.0$/, '');
    parts.push(`${memoryInGb} GB`);
  }
  return parts.filter(Boolean).join(', ');
}

// Shared by the future-dated and ML Block rules, which differ only in these three arguments.
function getCommitmentWindowErrors (values, minDurationDays, leadTime, endRequired = true) {
  const {commitmentStart, commitmentEnd, commitmentDurationDays} = values;
  // Checked on the true instants the payload carries, as the backend re-checks them
  // against `DateUtils.nowUTC()`.
  const start = getCommitmentDayStart(commitmentStart);
  const end = getCommitmentDayEnd(commitmentEnd);
  const now = moment.utc();
  let commitmentStartError, commitmentEndError, commitmentDurationError;
  // A value carrying a time fails this day-only strict parse, so it counts as missing.
  if (!commitmentStart || !start.isValid()) {
    commitmentStartError = 'Search from date is required';
  } else if (!start.isAfter(now)) {
    commitmentStartError = 'Search from date should be in the future';
  } else if (leadTime) {
    // Matches the backend's `ChronoUnit.DAYS.between`: floor of the fractional diff, not
    // `diff(now, 'days')`, which truncates toward zero.
    const leadDays = Math.floor(start.diff(now, 'days', true));
    if (leadDays < leadTime.min || leadDays > leadTime.max) {
      // `leadDays` floors against a start pinned to 00:00, so the calendar boundary a user
      // hits is one day later than `leadTime`'s own min/max.
      commitmentStartError = 'Search from date should be between ' +
        `${leadTime.min + 1} and ${leadTime.max + 1} days from now`;
    }
  }
  if (!commitmentEnd) {
    if (endRequired) {
      commitmentEndError = 'Search until date is required';
    }
  } else if (!end.isValid()) {
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
  // Pre-validates `NodePoolValidator`'s window rules. Its min-vCPU and allowed-family rules
  // are left to the backend - they need lookups this form does not have.
  if (mode === MODE_FUTURE_DATED) {
    // This endpoint cannot change an existing reservation's window, so SAVE must not be
    // blocked by fields the form leaves empty (the duration GET never echoes).
    if (!isExistingReservation) {
      ({commitmentStartError, commitmentEndError, commitmentDurationError} =
        getCommitmentWindowErrors(values, MIN_COMMITMENT_DURATION_DAYS, {
          min: MIN_RESERVATION_LEAD_DAYS,
          max: MAX_RESERVATION_LEAD_DAYS
        }));
    }
    // The backend stores a reservation pool with count 0 until activated, so an existing
    // future-dated pool legitimately loads with 0.
    if (!isExistingReservation && (Number.isNaN(Number(count)) ||
        Number(count) < RESERVATION_COUNT_MIN_SIZE)) {
      countError = 'Count should be at least 1 for a Future dated pool';
    }
  }
  // Nothing about a purchased block is editable.
  if (mode === MODE_ML_BLOCK && !isExistingReservation) {
    ({commitmentStartError, commitmentEndError, commitmentDurationError} =
      getCommitmentWindowErrors(values, MIN_ML_BLOCK_DURATION_DAYS, undefined, false));
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
    // `count`/`instanceType` come from the selected offering, so this only fails with
    // nothing selected.
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

// `instancePlatform`/`originId` are deliberately omitted - the backend defaults
// `instancePlatform` to `Linux/UNIX`.
export function buildCapacityReservationRequest (values) {
  const {mode} = values;
  if (mode === MODE_ML_BLOCK) {
    const offering = getSelectedOffering(values) || {};
    const {offeringId, availabilityZone, startDate, endDate, capacityDurationHours} = offering;
    return {
      reservationType: RESERVATION_TYPE_CAPACITY_BLOCK,
      capacityBlockOfferingId: offeringId,
      availabilityZone,
      // Sent raw: the backend reads these back as UTC (`toInstant(ZoneOffset.UTC)`), so
      // converting them the way the card display does would corrupt the buy.
      requestedStartDate: startDate,
      requestedEndDate: endDate,
      commitmentDuration: Number(capacityDurationHours) * SECONDS_PER_HOUR
    };
  }
  const {commitmentStart, commitmentEnd, commitmentDurationDays} = values;
  return {
    reservationType: RESERVATION_TYPE_FUTURE_DATED,
    // The viewer's picked day, converted to the UTC instant it denotes - the backend reads
    // this zone-less string as UTC.
    requestedStartDate: getCommitmentDayStart(commitmentStart).format(COMMITMENT_DATE_TIME_FORMAT),
    requestedEndDate: getCommitmentDayEnd(commitmentEnd).format(COMMITMENT_DATE_TIME_FORMAT),
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
  // The backend reads `poolType` on create only (its UPDATE omits the column), so sending it
  // unconditionally cannot overwrite an existing pool's type.
  payload.poolType = poolType;
  if (mode === MODE_FUTURE_DATED || mode === MODE_ML_BLOCK) {
    // `NodePoolManager.update()` never reads `capacityReservationRequest`, so it goes on
    // create only.
    if (!isExistingReservation) {
      payload.capacityReservationRequest = buildCapacityReservationRequest(values);
    }
  }
  return payload;
}

// A window counts as granted once both dates parse. Shared with `ReservationSummary` so the
// pool-card badge and that summary never disagree.
export function isReservationScheduled (start, end) {
  return Boolean(
    formatCloudCommitmentDate(start, 'YYYY-MM-DD HH:mm:ss') &&
    formatCloudCommitmentDate(end, 'YYYY-MM-DD HH:mm:ss')
  );
}

// The record is the source of truth for its own lifecycle, so its `status` is returned verbatim.
// With no record yet, Approved/Pending is inferred from the pool's dates so nothing goes blank.
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

// An unknown status must still read as something: `SOME_NEW_STATUS` -> "Some new status".
export function humanizeReservationStatus (status) {
  const words = `${status}`.toLowerCase().split('_').filter(Boolean);
  if (!words.length) {
    return status;
  }
  return `${words[0][0].toUpperCase()}${words[0].substring(1)} ${words.slice(1).join(' ')}`
    .trim();
}

// A reservation's `instanceCount` is only real while it still holds capacity.
export function getReservationDisplayInstanceCount (reservation) {
  if (!reservation || RESERVATION_TERMINAL_STATUSES.includes(reservation.status)) {
    return undefined;
  }
  const {instanceCount} = reservation;
  return instanceCount === null || instanceCount === undefined ? undefined : instanceCount;
}

// Backend `CapacityReservationService#onPoolDeletion`: deleting a pool gives up its reservation.
const RESERVATION_SAFE_TO_DELETE_STATUSES = [
  ...RESERVATION_TERMINAL_STATUSES,
  RESERVATION_STATUS_REQUIRED_APPROVE
];

// At any other status, `ACTIVE` included, giving up the capacity may still cost money, so the
// backend refuses to do it implicitly.
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
