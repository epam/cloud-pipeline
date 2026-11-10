/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import roleModel from '../../../utils/roleModel';

/**
 * A user, who is neither an admin nor a user admin, may edit a group only if WRITE permission
 * is granted on it. The API ignores WRITE permission on a predefined role
 * @param {{admin: boolean, usersAdmin: boolean, role: Object}} options
 * @returns {boolean}
 */
export function isRoleReadOnly (options = {}) {
  const {
    admin = false,
    usersAdmin = false,
    role
  } = options;
  if (admin || usersAdmin) {
    return false;
  }
  return !role || !!role.predefined || !roleModel.writeAllowed(role);
}

/**
 * A default group is assigned to every new user, so only admins and user admins may change
 * the flag - the API rejects the change for a user who only has WRITE permission on the group
 * @param {{admin: boolean, usersAdmin: boolean, readOnly: boolean, pending: boolean}} options
 * @returns {boolean}
 */
export function isUserDefaultEditable (options = {}) {
  const {
    admin = false,
    usersAdmin = false,
    readOnly = false,
    pending = false
  } = options;
  return (admin || usersAdmin) && !readOnly && !pending;
}
