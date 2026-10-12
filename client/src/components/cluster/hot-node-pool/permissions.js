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

// Three distinct capabilities, deliberately not one boolean: an admin manages every pool,
// an authenticated non-admin may only *create* a GPU-mode pool and may not reach any
// existing one, and an unloaded user may do nothing.
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
  const admin = isAdmin(authenticatedUserInfo.value);
  return {
    canManagePools: admin, // open / edit / remove an existing pool
    canCreateAnyMode: admin, // choose between Default and GPU on create
    canCreateGpuPool: true, // every authenticated user may create a GPU pool
    canCreate: true,
    readOnly: !admin // same meaning the old isReadOnly had for PoolCard
  };
}
