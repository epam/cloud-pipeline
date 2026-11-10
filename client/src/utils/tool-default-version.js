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

export const LATEST_VERSION = 'latest';

/**
 * Returns a version, which a tool's docker image without a tag stands for:
 * the tool's default version, or the "latest" one, if the default version is not set.
 * It is the version the API launches for such an image.
 * @param {{defaultVersion: string}} [tool]
 * @returns {string}
 */
export function getTaglessImageVersion (tool) {
  const {defaultVersion} = tool || {};
  return defaultVersion || LATEST_VERSION;
}

/**
 * Returns a tool's version, which is used, if a user does not choose one:
 * the tool's default version, if the tool has it, else the "latest" one, if the tool has it,
 * else the only version of the tool.
 * @param {{defaultVersion: string}} [tool]
 * @param {string[]} [versions] names of the tool's versions
 * @returns {string|undefined}
 */
export function getToolDefaultVersion (tool, versions = []) {
  const names = (versions || []).filter(Boolean);
  const {defaultVersion} = tool || {};
  if (defaultVersion && names.includes(defaultVersion)) {
    return defaultVersion;
  }
  if (names.includes(LATEST_VERSION)) {
    return LATEST_VERSION;
  }
  if (names.length === 1) {
    return names[0];
  }
  return undefined;
}

/**
 * Checks whether a tool's default version is set by a user and exists
 * (otherwise the "latest" version, or the only one, is used)
 * @param {{defaultVersion: string}} [tool]
 * @param {string[]} [versions] names of the tool's versions
 * @returns {boolean}
 */
export function isCustomDefaultVersion (tool, versions = []) {
  const {defaultVersion} = tool || {};
  return !!defaultVersion && (versions || []).includes(defaultVersion);
}
