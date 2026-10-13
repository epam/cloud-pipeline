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

import {isAdmin} from '../utilities/access-permissinos';
import roleModel from '../../../utils/roleModel';

// The backend's `NODE_POOL_MANAGER` role (`DefaultRoles.ROLE_NODE_POOL_MANAGER`) and
// `RUN_ADMIN`: the two non-ADMIN roles `NodePoolApiService`'s `NODE_POOL_CREATE`/
// `NODE_POOL_ID_WRITE`/`NODE_POOL_ID_OWNER` ACL expressions accept outright, regardless
// of any per-pool grant.
const ROLE_NODE_POOL_MANAGER = 'ROLE_NODE_POOL_MANAGER';
const ROLE_RUN_ADMIN = 'ROLE_RUN_ADMIN';

// `userHasRole` resolves through `Array.find`, which returns the matching role object
// rather than a strict boolean - coerced here so every capability below is a real
// boolean, not a role object that happens to be truthy.
function hasBlanketManageRights (user) {
  return Boolean(isAdmin(user) || roleModel.userHasRole(user, ROLE_RUN_ADMIN));
}

// `NODE_POOL_CREATE`: ADMIN, RUN_ADMIN or NODE_POOL_MANAGER - never "every authenticated
// user", which is what this used to assume. A user with none of the three gets a 403
// from the backend on create, so the "+ Create" button must not be shown to them.
function canCreatePools (user) {
  return hasBlanketManageRights(user) ||
    Boolean(roleModel.userHasRole(user, ROLE_NODE_POOL_MANAGER));
}

// Two distinct capabilities, deliberately not one boolean: an admin or a RUN_ADMIN
// manages every pool outright; a NODE_POOL_MANAGER may only create a reservation pool
// and cannot reach any existing one; an unloaded user may do nothing.
//
// `NodePool` is ACL-securable on the backend (`NODE_POOL_ID_WRITE`/`NODE_POOL_ID_OWNER`
// also accept a per-pool WRITE/OWNER grant, not just ADMIN/RUN_ADMIN), but `GET
// /cluster/pool` does not yet annotate its read methods with `@AclMask`/`@AclMaskList`,
// so every pool's `mask` comes back as the all-permissions default regardless of the
// viewer's real grant. Keying `canEditPool`/`canDeletePool` off that mask today would
// show Edit/Delete to any user who can merely list a pool, not just one actually granted
// WRITE/OWNER - worse than the admin-only gate it would replace. Omitted until the
// backend sends a real per-pool mask.
export function getPoolPermissions (authenticatedUserInfo) {
  const loaded = !!authenticatedUserInfo &&
    !!authenticatedUserInfo.loaded &&
    !!authenticatedUserInfo.value;
  if (!loaded) {
    return {
      canManagePools: false,
      canCreateAnyMode: false,
      canCreateGpuPool: false,
      canCreate: false,
      readOnly: true
    };
  }
  const user = authenticatedUserInfo.value;
  const blanketManager = hasBlanketManageRights(user);
  const create = canCreatePools(user);
  return {
    canManagePools: blanketManager, // open / edit / remove any pool outright
    canCreateAnyMode: blanketManager, // choose between Default and GPU on create
    canCreateGpuPool: create,
    canCreate: create,
    readOnly: !blanketManager
  };
}

// `NODE_POOL_ID_WRITE`: ADMIN or RUN_ADMIN - see the note on `getPoolPermissions` above
// for why a per-pool WRITE grant is not checked here yet.
export function canEditPool (pool, authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  return hasBlanketManageRights(user);
}

// `NODE_POOL_ID_OWNER`: ADMIN or RUN_ADMIN - see the note on `getPoolPermissions` above
// for why a per-pool OWNER grant is not checked here yet.
export function canDeletePool (pool, authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  return hasBlanketManageRights(user);
}
