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

import {getPoolPermissions, canEditPool, canDeletePool} from './permissions';

const everyCapabilityDenied = {
  canManagePools: false,
  canCreateAnyMode: false,
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
  expect(permissions.canCreate).toBe(true);
  expect(permissions.readOnly).toBe(false);
});

// `NodePoolApiService.create`'s `NODE_POOL_CREATE` ACL expression is
// `ADMIN OR RUN_ADMIN OR NODE_POOL_MANAGER` - a plain authenticated user with none of
// the three gets a 403 from the backend, so the client must not offer Create either.
test('a plain non-admin, with no role, cannot create or manage anything', () => {
  const permissions = getPoolPermissions({loaded: true, value: {admin: false, roles: []}});
  expect(permissions.canCreate).toBe(false);
  expect(permissions.canManagePools).toBe(false);
  expect(permissions.canCreateAnyMode).toBe(false);
  expect(permissions.readOnly).toBe(true);
});

test('a cluster reader gains no management rights beyond a plain non-admin', () => {
  const permissions = getPoolPermissions({
    loaded: true,
    value: {admin: false, roles: [{name: 'ROLE_CLUSTER_READER'}]}
  });
  expect(permissions.canCreate).toBe(false);
  expect(permissions.canManagePools).toBe(false);
  expect(permissions.canCreateAnyMode).toBe(false);
  expect(permissions.readOnly).toBe(true);
});

test('a NODE_POOL_MANAGER may create a future-dated pool only, and is read-only otherwise', () => {
  const permissions = getPoolPermissions({
    loaded: true,
    value: {admin: false, roles: [{name: 'ROLE_NODE_POOL_MANAGER'}]}
  });
  expect(permissions.canCreate).toBe(true);
  expect(permissions.canManagePools).toBe(false);
  expect(permissions.canCreateAnyMode).toBe(false);
  expect(permissions.readOnly).toBe(true);
});

test('a RUN_ADMIN gets every capability, same as an admin', () => {
  const permissions = getPoolPermissions({
    loaded: true,
    value: {admin: false, roles: [{name: 'ROLE_RUN_ADMIN'}]}
  });
  expect(permissions.canCreate).toBe(true);
  expect(permissions.canManagePools).toBe(true);
  expect(permissions.canCreateAnyMode).toBe(true);
  expect(permissions.readOnly).toBe(false);
});

// `GET /cluster/pool` does not yet annotate its read methods with `@AclMask`/
// `@AclMaskList`, so a pool's `mask` is not a trustworthy per-user grant today - these
// checks deliberately ignore it (see the comment on `getPoolPermissions`) and fall back
// to ADMIN/RUN_ADMIN only, same as `canManagePools`.
describe('canEditPool', () => {
  test('an admin may edit any pool', () => {
    expect(canEditPool({mask: 0}, {value: {admin: true}})).toBe(true);
  });

  test('a RUN_ADMIN may edit any pool', () => {
    expect(canEditPool({mask: 0}, {value: {admin: false, roles: [{name: 'ROLE_RUN_ADMIN'}]}}))
      .toBe(true);
  });

  test('a plain user may not edit a pool, even one whose mask claims a WRITE grant', () => {
    const user = {admin: false, roles: []};
    expect(canEditPool({mask: 2}, {value: user})).toBe(false);
  });
});

describe('canDeletePool', () => {
  test('an admin may delete any pool', () => {
    expect(canDeletePool({mask: 0}, {value: {admin: true}})).toBe(true);
  });

  test('a plain user may not delete a pool, even one whose mask claims an OWNER grant', () => {
    const user = {admin: false, roles: []};
    expect(canDeletePool({mask: 8}, {value: user})).toBe(false);
  });
});
