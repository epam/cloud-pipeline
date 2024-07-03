/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import getRunDurationInfo from './run-duration';

const SECONDS_IN_HOUR = 3600;

/**
 * @typedef {Object} RunPriceEstimationOptions
 * @property {boolean} [analyseSchedulingPhase]
 * @property {object|*[]} [runTasks]
 */

/**
 * Gets run estimated price
 * @param run
 * @param {RunPriceEstimationOptions} [options]
 * @returns {{total: number, master: number, workers: number}}
 */
export default function evaluateRunPrice (
  run,
  options = {}
) {
  const {
    analyseSchedulingPhase = false,
    runTasks = []
  } = options || {};
  if (!run) {
    return {
      total: 0,
      master: 0,
      workers: 0
    };
  }
  const {
    totalBillableDuration,
    totalBillableRunningDuration
  } = getRunDurationInfo(
    run,
    analyseSchedulingPhase,
    runTasks
  );
  const {
    computePricePerHour = 0,
    diskPricePerHour = 0,
    workersPrice = 0
  } = run;
  const format = (value) => Math.ceil(value * 100.0) / 100.0;
  const master = computePricePerHour * (totalBillableRunningDuration / SECONDS_IN_HOUR) +
    diskPricePerHour * (totalBillableDuration / SECONDS_IN_HOUR);
  return {
    master: format(master),
    workers: format(workersPrice),
    total: format(master + workersPrice)
  };
}
