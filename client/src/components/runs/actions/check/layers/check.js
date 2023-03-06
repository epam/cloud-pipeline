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

import preferences from '../../../../../models/preferences/PreferencesLoad';
import PipelineRunLayers from '../../../../../models/pipelines/PipelineRunLayers';

/**
 * @typedef {Object} LayersCheckResult
 * @property {boolean} result
 * @property {number} [allowed]
 * @property {number} [current]
 */

/**
 * @param {number|string} runId
 * @param {boolean} [skip=false]
 * @returns {Promise<{LayersCheckResult}>} true if layers check passed, false otherwise
 */
export default async function checkLayers (runId, skip = false) {
  if (skip) {
    return Promise.resolve({result: true});
  }
  await preferences.fetchIfNeededOrWait();
  const maxLayers = preferences.commitMaxLayers;
  if (!maxLayers) {
    return {result: true};
  }
  const request = new PipelineRunLayers(runId);
  await request.fetch();
  if (request.loaded && request.value && !Number.isNaN(Number(request.value))) {
    return {
      result: Number(request.value) < maxLayers,
      allowed: maxLayers,
      current: Number(request.value)
    };
  }
  return {result: true};
}
