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

import {getPoolPermissions} from './permissions';

const everyCapabilityDenied = {
  canManagePools: false,
  canCreateAnyMode: false,
  canCreateGpuPool: false,
  canCreate: false,
  readOnly: true
};

test('undefined gives every capability false and readOnly true', () => {
  expect(getPoolPermissions(undefined)).toEqual(everyCapabilityDenied);
});

test('{loaded: false} gives every capability false and readOnly true', () => {
  expect(getPoolPermissions({loaded: false})).toEqual(everyCapabilityDenied);
});

test('{loaded: true, value: undefined} gives every capability false and readOnly true', () => {
  expect(getPoolPermissions({loaded: true, value: undefined})).toEqual(everyCapabilityDenied);
});

test('an admin gets every capability, and is not read-only', () => {
  const permissions = getPoolPermissions({loaded: true, value: {admin: true}});
  expect(permissions.canManagePools).toBe(true);
  expect(permissions.canCreateAnyMode).toBe(true);
  expect(permissions.canCreateGpuPool).toBe(true);
  expect(permissions.canCreate).toBe(true);
  expect(permissions.readOnly).toBe(false);
});

test('a non-admin may create a GPU pool only, and is read-only otherwise', () => {
  const permissions = getPoolPermissions({loaded: true, value: {admin: false, roles: []}});
  expect(permissions.canCreate).toBe(true);
  expect(permissions.canCreateGpuPool).toBe(true);
  expect(permissions.canManagePools).toBe(false);
  expect(permissions.canCreateAnyMode).toBe(false);
  expect(permissions.readOnly).toBe(true);
});

test('a cluster reader gains no management rights beyond a plain non-admin', () => {
  const permissions = getPoolPermissions({
    loaded: true,
    value: {admin: false, roles: [{name: 'ROLE_CLUSTER_READER'}]}
  });
  expect(permissions.canCreate).toBe(true);
  expect(permissions.canCreateGpuPool).toBe(true);
  expect(permissions.canManagePools).toBe(false);
  expect(permissions.canCreateAnyMode).toBe(false);
  expect(permissions.readOnly).toBe(true);
});
