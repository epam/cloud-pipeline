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
import LoadToolInfo from '../../../../../models/tools/LoadToolInfo';
import LoadTool from '../../../../../models/tools/LoadTool';

async function getToolIdentifierAndVersion (toolId) {
  if (!toolId) {
    return undefined;
  }
  if (!Number.isNaN(Number(toolId))) {
    return {
      id: Number(toolId)
    };
  }
  let registry, image, version;
  if (typeof toolId === 'string' && /^[^/]+\/[^/]+\/[^/]+$/i.test(toolId)) {
    // registry/group/tool
    const [r, g, i] = toolId.split('/');
    registry = r;
    const [imageName, v] = i.split(':');
    image = `${g}/${imageName}`;
    version = v;
  } else if (typeof toolId === 'string') {
    image = toolId;
  }
  if (image) {
    const request = new LoadTool(image, registry);
    try {
      await request.fetch();
      if (request.loaded && request.value) {
        return {
          id: Number(request.value.id),
          version
        };
      }
    } catch (_) {
      return undefined;
    }
  }
  return undefined;
}

/**
 * @typedef {Object} ToolLayersCheckResult
 * @property {boolean} result
 * @property {number} [allowed]
 * @property {number} [current]
 */

/**
 * @param {number|string} toolId
 * @returns {Promise<{ToolLayersCheckResult}>} true if tool layers check passed, false otherwise
 */
export default async function checkToolLayers (toolId) {
  await preferences.fetchIfNeededOrWait();
  const maxLayers = preferences.commitMaxLayers;
  if (!maxLayers) {
    return {result: true};
  }
  const info = await getToolIdentifierAndVersion(toolId);
  if (!info) {
    return {result: true};
  }
  const {
    id,
    version = 'latest'
  } = info;
  const request = new LoadToolInfo(id);
  await request.fetch();
  if (request.loaded && request.value) {
    const {
      versions = []
    } = request.value;
    const versionInfo = versions
      .find((o) => (o.version || '').toLowerCase() === version.toLowerCase());
    const {
      scanResult = {}
    } = versionInfo || {};
    const {
      layersCount = 0
    } = scanResult;
    if (layersCount > 0) {
      return {
        result: layersCount < maxLayers,
        allowed: maxLayers,
        current: layersCount
      };
    }
  }
  return {result: true};
}
