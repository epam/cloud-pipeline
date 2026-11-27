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

/**
 * Checks whether a user matches a capability's `permissions` list.
 * An empty (or missing) list means "visible to everyone".
 *
 * @param {{name: string, principal: boolean}[]} [permissions]
 * @param {{userName: string, roles: {name: string}[], groups: string[]}} [user]
 * @returns {boolean}
 */
export function userMatchesCapabilityPermissions (permissions, user) {
  if (!permissions || !permissions.length) {
    return true;
  }
  if (!user) {
    return false;
  }
  const userName = (user.userName || '').toLowerCase();
  const roles = (user.roles || []).map(role => (role.name || '').toLowerCase());
  const groups = (user.groups || []).map(group => (group || '').toLowerCase());
  return permissions.some(({name, principal}) => {
    const value = (name || '').toLowerCase();
    if (!value) {
      return false;
    }
    return principal
      ? userName === value
      : roles.includes(value) || groups.includes(value);
  });
}
