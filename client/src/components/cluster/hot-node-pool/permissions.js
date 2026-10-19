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
  isAdmin,
  isRunAdmin,
  isNodePoolManager
} from '../utilities/access-permissinos';

// `isAdmin` hands back the user's own `admin` field rather than a strict boolean, so this
// is coerced to keep every capability below a real boolean.
function hasBlanketManageRights (user) {
  return Boolean(isAdmin(user) || isRunAdmin(user));
}

// `NODE_POOL_CREATE`: ADMIN, RUN_ADMIN, or a NODE_POOL_MANAGER whose request carries a
// capacity reservation (`#vo.capacityReservationRequest != null`) - never "every
// authenticated user", which is what this used to assume. A user with none of the three
// gets a 403 from the backend on create, so the "+ Create" button must not be shown to
// them; the reservation conjunct is what `canCreateAnyMode` below honours, by forcing
// that caller's Create into future dated mode.
function canCreatePools (user) {
  return hasBlanketManageRights(user) || isNodePoolManager(user);
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
      canCreate: false,
      readOnly: true
    };
  }
  const user = authenticatedUserInfo.value;
  const blanketManager = hasBlanketManageRights(user);
  const create = canCreatePools(user);
  return {
    canManagePools: blanketManager, // open / edit / remove any pool outright
    canCreateAnyMode: blanketManager, // choose between Default and future-dated on create
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
