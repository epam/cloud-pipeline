/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

function normalizeSid (sid, isPrincipal) {
  if (!sid || !sid.name) {
    return null;
  }
  return {
    name: sid.name,
    isPrincipal,
    accessType: sid.accessType
  };
}

export function configurationShareToSids (configuration = {}) {
  /* eslint-disable camelcase */
  const users = (configuration.share_with_users || [])
    .map((sid) => normalizeSid(sid, true))
    .filter(Boolean);
  const roles = (configuration.share_with_roles || [])
    .map((sid) => normalizeSid(sid, false))
    .filter(Boolean);
  /* eslint-enable camelcase */
  return [...users, ...roles];
}

function toConfigurationSid (sid) {
  return {
    name: sid.name,
    isPrincipal: sid.isPrincipal,
    accessType: sid.accessType
  };
}

export function sidsToConfigurationShare (sids = []) {
  /* eslint-disable camelcase */
  return {
    users: sids
      .filter((sid) => sid.isPrincipal)
      .map(toConfigurationSid),
    roles: sids
      .filter((sid) => !sid.isPrincipal)
      .map(toConfigurationSid)
  };
  /* eslint-enable camelcase */
}

function sidKey (sid) {
  return [
    (sid.name || '').toLowerCase(),
    !!sid.isPrincipal,
    sid.accessType || ''
  ].join('|');
}

export function shareSidsEqual (a = [], b = []) {
  if (a.length !== b.length) {
    return false;
  }
  const keysA = a.map(sidKey).sort();
  const keysB = b.map(sidKey).sort();
  for (let i = 0; i < keysA.length; i++) {
    if (keysA[i] !== keysB[i]) {
      return false;
    }
  }
  return true;
}
