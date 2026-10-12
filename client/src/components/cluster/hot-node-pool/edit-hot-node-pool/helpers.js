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

import {getFiltersError, filtersAreEqual} from '../filters-control';
import {compareSchedulesArray, scheduleIsValid} from './controls/ScheduleControl';
import {DISK_MIN_SIZE, DISK_MAX_SIZE, COUNT_MIN_SIZE} from './constants';

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
    labels = []
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
    labelsError;
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
    !labelsError;
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
      labelsError
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

export function buildPoolPayload (values) {
  const {
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
  return {
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
}

export function buildSchedulePayload (values) {
  const {schedule, name} = values;
  return schedule.length > 0 ? {
    scheduleEntries: schedule.map(({id, ...s}) => s),
    name
  } : undefined;
}
