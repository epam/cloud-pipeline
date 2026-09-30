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

import {isObservableArray} from 'mobx';

const isArray = (o) => Array.isArray(o) || isObservableArray(o);

/**
 * Returns `undefined` if the roles are not set (no restriction), otherwise the list of
 * the role / group names. A malformed value gives an empty list, so nobody passes the check
 * @param {*} roles
 * @returns {string[]|undefined}
 */
function parseRoles (roles) {
  if (roles === undefined || roles === null || (isArray(roles) && roles.length === 0)) {
    return undefined;
  }
  const list = isArray(roles) ? roles.slice() : [roles];
  return list
    .filter((role) => typeof role === 'string')
    .map((role) => role.trim())
    .filter((role) => role.length > 0);
}

/**
 * Whether the user has one of the roles or groups, compared by the exact name
 * @param {string[]} roles - role / group names
 * @param {object} authenticatedUserInfo - authenticated user store
 * @returns {boolean}
 */
function userHasAnyRole (roles, authenticatedUserInfo) {
  if (!authenticatedUserInfo || !authenticatedUserInfo.loaded || !authenticatedUserInfo.value) {
    return false;
  }
  const {
    roles: userRoles,
    groups: userGroups
  } = authenticatedUserInfo.value;
  const userRolesAndGroups = [
    ...(userRoles || []).map((role) => role ? role.name : undefined),
    ...(userGroups || [])
  ].filter((name) => typeof name === 'string');
  return roles.some((role) => userRolesAndGroups.includes(role));
}

/**
 * Builds the url of the failed run investigation service
 * (`investigation` field of the `misc.ai.preferences` preference):
 * `${investigation.url}/?run_id=${run.id}`.
 * If `investigation.roles` is set (and not empty), the url is returned only for the users
 * with one of these roles or groups.
 * Returns nothing if the run has not failed or the service is not configured.
 * @param {object} preferences - PreferencesLoad store
 * @param {{id: number, status: string}} run
 * @param {object} authenticatedUserInfo - authenticated user store
 * @returns {string|undefined}
 */
export default function getRunInvestigationUrl (preferences, run, authenticatedUserInfo) {
  if (!preferences || !preferences.loaded || !run) {
    return undefined;
  }
  const {id, status} = run;
  if (
    `${status || ''}`.toUpperCase() !== 'FAILURE' ||
    id === undefined ||
    id === null ||
    `${id}`.length === 0
  ) {
    return undefined;
  }
  const {
    investigation
  } = preferences.miscAIPreferences || {};
  if (!investigation || typeof investigation !== 'object') {
    return undefined;
  }
  const {url, roles} = investigation;
  if (typeof url !== 'string' || url.trim().length === 0) {
    return undefined;
  }
  const allowedRoles = parseRoles(roles);
  if (allowedRoles && !userHasAnyRole(allowedRoles, authenticatedUserInfo)) {
    return undefined;
  }
  const base = url.trim().replace(/\/+$/, '');
  return `${base}/?run_id=${encodeURIComponent(id)}`;
}
