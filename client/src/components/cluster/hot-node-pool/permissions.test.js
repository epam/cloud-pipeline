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

import {
  getPoolPermissions,
  canEditPool,
  canDeletePool,
  canViewPoolUsage
} from './permissions';

const nodePoolManager = (userName = 'MANAGER') => ({
  admin: false,
  userName,
  roles: [{name: 'ROLE_NODE_POOL_MANAGER'}]
});

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
// checks deliberately ignore it (see the comment on `getPoolPermissions`): ADMIN and
// RUN_ADMIN reach every pool, and a NODE_POOL_MANAGER reaches the ones it owns, decided
// on `owner` rather than on that mask.
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

  // A NODE_POOL_MANAGER holds no write of its own under `NODE_POOL_ID_WRITE`; what it
  // holds is ownership of the pools it created, so `owner` is what decides here.
  test('a NODE_POOL_MANAGER may edit a pool it owns', () => {
    expect(canEditPool({owner: 'MANAGER'}, {value: nodePoolManager()})).toBe(true);
  });

  test('a NODE_POOL_MANAGER may edit a pool it owns whatever the case of the name', () => {
    expect(canEditPool({owner: 'manager'}, {value: nodePoolManager('MANAGER')})).toBe(true);
  });

  test('a NODE_POOL_MANAGER may not edit a pool owned by somebody else', () => {
    expect(canEditPool({owner: 'SOMEONE_ELSE'}, {value: nodePoolManager()})).toBe(false);
  });

  test('a NODE_POOL_MANAGER may not edit a pool with no owner on it', () => {
    expect(canEditPool({mask: 2}, {value: nodePoolManager()})).toBe(false);
  });

  test('a plain user may not edit a pool it owns - the role is required too', () => {
    expect(canEditPool({owner: 'USER'}, {value: {admin: false, userName: 'USER', roles: []}}))
      .toBe(false);
  });
});

// The chart button leads to `/cluster/usage`, gated on ADMIN_OR_CLUSTER_READER, so the
// NODE_POOL_MANAGER role alone does not earn it.
describe('canViewPoolUsage', () => {
  test('an admin may view pool usage', () => {
    expect(canViewPoolUsage({value: {admin: true}})).toBe(true);
  });

  test('a cluster reader may view pool usage', () => {
    expect(canViewPoolUsage({value: {admin: false, roles: [{name: 'ROLE_CLUSTER_READER'}]}}))
      .toBe(true);
  });

  // `ADMIN_OR_CLUSTER_READER`, the backend's own gate behind `POST /report/pools`, is
  // ADMIN, RUN_ADMIN or ROLE_CLUSTER_READER - `hasBlanketManageRights` already treats
  // RUN_ADMIN as admin's equal for edit/delete, and this must not single it out to lose
  // the chart button that the other two capabilities on the same card keep for it.
  test('a RUN_ADMIN may view pool usage', () => {
    expect(canViewPoolUsage({value: {admin: false, roles: [{name: 'ROLE_RUN_ADMIN'}]}}))
      .toBe(true);
  });

  test('a NODE_POOL_MANAGER may not view pool usage', () => {
    expect(canViewPoolUsage({value: nodePoolManager()})).toBe(false);
  });

  test('a NODE_POOL_MANAGER who is also a cluster reader may view pool usage', () => {
    expect(canViewPoolUsage({
      value: {
        admin: false,
        userName: 'MANAGER',
        roles: [{name: 'ROLE_NODE_POOL_MANAGER'}, {name: 'ROLE_CLUSTER_READER'}]
      }
    })).toBe(true);
  });

  test('an unloaded user may not view pool usage', () => {
    expect(canViewPoolUsage(undefined)).toBe(false);
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

  // `NODE_POOL_ID_OWNER` reads the same ownership `NODE_POOL_ID_WRITE` does - see
  // `canEditPool` above - so a NODE_POOL_MANAGER's reach for delete is identical to its
  // reach for edit.
  test('a NODE_POOL_MANAGER may delete a pool it owns', () => {
    expect(canDeletePool({owner: 'MANAGER'}, {value: nodePoolManager()})).toBe(true);
  });

  test('a NODE_POOL_MANAGER may not delete a pool owned by somebody else', () => {
    expect(canDeletePool({owner: 'SOMEONE_ELSE'}, {value: nodePoolManager()})).toBe(false);
  });
});
