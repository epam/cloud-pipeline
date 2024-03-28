/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import {inject, observer} from 'mobx-react';

const bitEnabled = (bit, mask) => {
  return (mask & bit) === bit;
};

const readAllowed = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(1, collapseMask(item.mask));
  }
  return bitEnabled(1, item.mask);
};

const writeAllowed = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(2, collapseMask(item.mask));
  }
  return bitEnabled(2, item.mask);
};

const executeAllowed = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(4, collapseMask(item.mask));
  }
  return bitEnabled(4, item.mask);
};

const readDenied = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(1 << 1, item.mask);
  }
  return !bitEnabled(1, item.mask);
};

const writeDenied = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(1 << 3, item.mask);
  }
  return !bitEnabled(2, item.mask);
};

const executeDenied = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(1 << 5, item.mask);
  }
  return !bitEnabled(4, item.mask);
};

const isOwner = (item, extendedMask = false) => {
  if (!item || item.mask === undefined || item.mask === null) {
    return false;
  }
  if (extendedMask) {
    return bitEnabled(8, collapseMask(item.mask));
  }
  return bitEnabled(8, item.mask);
};

const extendMask = (mask) => {
  return (
    readAllowed({mask}) |
    !readAllowed({mask}) << 1 |
    writeAllowed({mask}) << 2 |
    !writeAllowed({mask}) << 3 |
    executeAllowed({mask}) << 4 |
    !executeAllowed({mask}) << 5
  );
};

const collapseMask = (mask) => {
  let readAllowed = (mask & 1) === 1;
  let writeAllowed = (mask & 4) === 4;
  let executeAllowed = (mask & 16) === 16;
  return readAllowed | writeAllowed << 1 | executeAllowed << 2;
};

const buildMask = (read, write, execute, extendedMask = false) => {
  const mask = (read ? 1 : 0) | ((write ? 1 : 0) << 1) | ((execute ? 1 : 0) << 2);
  if (extendedMask) {
    return extendedMask(mask);
  }
  return mask;
};

const buildPermissionsMask = (ra, rd, wa, wd, ea, ed) => {
  const buildBit = (bit, shift) => bit ? (1 << shift) : 0;
  return (
    buildBit(ra, 0) |
    buildBit(rd, 1) |
    buildBit(wa, 2) |
    buildBit(wd, 3) |
    buildBit(ea, 4) |
    buildBit(ed, 5)
  );
};

/**
 * Checks if specific permission (i.e. read, write or execute) specified by
 * `testMinifiedMask` conflict with corresponding permission for the object (`objectMinifiedMask`).
 * "Conflict" is a situation when object has a "DENY" rule, but permission (`testMinifiedMask`)
 * requests "ALLOW" one.
 * @returns {boolean | undefined} `true` if permissions conflict, `false` if dont,
 * `undefined` if it depends on inheritance
 * @param testMinifiedMask {number} permission mask (2-bit value; first bit describes "allow" rule,
 * second bit describes "deny" rule)
 * @param objectMinifiedMask {number} object's permission mask (2-bit value; first bit describes
 * "allow" rule, second bit describes "deny" rule)
 */
const checkPermissionConflicting = (testMinifiedMask, objectMinifiedMask) => {
  if (testMinifiedMask === 0b00) {
    return false;
  }
  if (
    testMinifiedMask === 0b11 ||
    objectMinifiedMask === 0b00 ||
    objectMinifiedMask === 0b11
  ) {
    // for 00 values: permissions are inherited
    // for 11 values (impossible situation: both "allow" and "deny" rules): skip
    return undefined;
  }
  return testMinifiedMask === 0b01 && objectMinifiedMask === 0b10;
};

/**
 * Checks if permissions specified by `testMask` conflict
 * with permissions for the object (`objectMask`). "Conflict" is a situation when
 * object has a "DENY" rule, but permissions (`testMask`) request "ALLOW" one.
 * @returns {{read: boolean | undefined, write: boolean | undefined, execute: boolean | undefined}}
 * For each permission (read, write, execute): `true` if permissions conflict, `false` if dont,
 * `undefined` if it depends on inheritance
 * @param testMask {number} permissions to check
 * @param objectMask {number} object's permissions mask
 * @param extendedMask {boolean} true if provided masks are of extended format (i.e. 6-bit format)
 */
const checkPermissionsConflicting = (testMask, objectMask, extendedMask = false) => {
  const testMaskExtended = extendedMask ? testMask : extendMask(testMask);
  const objectMaskExtended = extendedMask ? objectMask : extendMask(objectMask);
  const extractMinifiedPermissionsMask = (mask, shift = 0) => (mask >> shift) & 0b11;
  return {
    read: checkPermissionConflicting(
      extractMinifiedPermissionsMask(testMaskExtended),
      extractMinifiedPermissionsMask(objectMaskExtended)
    ),
    write: checkPermissionConflicting(
      extractMinifiedPermissionsMask(testMaskExtended, 2),
      extractMinifiedPermissionsMask(objectMaskExtended, 2)
    ),
    execute: checkPermissionConflicting(
      extractMinifiedPermissionsMask(testMaskExtended, 4),
      extractMinifiedPermissionsMask(objectMaskExtended, 4)
    )
  };
};

/**
 * Checks if permissions specified by `testMask` conflict
 * with permissions set for the object (`objectMasks`). "Conflict" is a situation when
 * object has a "DENY" rule, but permissions (`testMask`) request "ALLOW" one.
 * @returns {{read: boolean, write: boolean, execute: boolean}}
 * For each permission (read, write, execute): `true` if permissions conflict, `false` if dont
 * @param testMask {number} permissions to check
 * @param objectMasks {number} object's permissions masks
 * @param extendedMask {boolean} true if provided masks are of extended format (i.e. 6-bit format)
 */
/*
  Unlike `checkPermissionsConflicting` method, this one does not return `undefined`;
  if all permissions specify `undefined` value (i.e. rule is inherited) then there are no "allow"
  rules provided and access is denied therefore.
 */
const checkPermissionsSetConflicting = (testMask, objectMasks = [], extendedMask = false) => {
  const merged = objectMasks
    .map(objectMask => checkPermissionsConflicting(testMask, objectMask, extendedMask))
    .reduce((acc, cur) => ({
      read: [...(acc.read || []), cur.read],
      write: [...(acc.write || []), cur.write],
      execute: [...(acc.execute || []), cur.execute]
    }), {});
  const getResolution = conflictResults => {
    const filtered = (conflictResults || []).filter(result => result !== undefined);
    if (filtered.length === 0) {
      // conflict!
      return true;
    }
    // if we have at least one "true" - there is a conflict
    return filtered.find(o => o);
  };
  return {
    read: getResolution(merged.read),
    write: getResolution(merged.write),
    execute: getResolution(merged.execute)
  };
};

/**
 * Checks if user or group specified by `sid` has conflicting permissions (`mask`) with ones
 * provided for the object (objectPermissions). "Conflict" means that "allow" permission was
 * requested but there is at least one "deny" permission for user/group or/and user groups.
 * Exceptions: if user is owner or admin, no conflicts will occur.
 * @param mask {number} requested access permissions (6-bit format)
 * @param sid {{name: string, principal: boolean}} requester
 * @param sidRoles {{name: string}[]} user's roles
 * @param objectOwner {string} object's owner.
 * @param objectPermissions {{mask: number, sid: {name: string, principal: boolean}}[]}
 * object permissions
 * @returns {{read: boolean, write: boolean, execute: boolean}}
 * For each permission (read, write, execute): `true` if permissions conflict (i.e. "allow"
 * requested, but object has at least one "deny" rule), `false` if dont
 */
const checkObjectPermissionsConflict = (mask, sid, sidRoles, objectOwner, objectPermissions) => {
  const {name, principal} = sid;
  const roleNames = (sidRoles || []).map(r => r.name);
  if (principal && (name === objectOwner || (roleNames || []).indexOf('ROLE_ADMIN') >= 0)) {
    // there are no conflicts if user is owner or admin
    return {
      read: false,
      write: false,
      execute: false
    };
  }
  const findMask = (testSid) => (objectPermissions || [])
    .find(op => op.sid && op.sid.name === testSid.name && op.sid.principal === testSid.principal)
    ?.mask;
  const findConflicts = (testSid) => {
    const objectMask = findMask(testSid);
    if (objectMask !== undefined) {
      return checkPermissionsConflicting(
        mask,
        objectMask,
        true
      );
    }
    return {};
  };
  const sids = [sid, ...sidRoles.map(({name}) => ({name, principal: false}))];
  const getResolution = conflictResults => {
    const filtered = (conflictResults || []).filter(result => result !== undefined);
    if (filtered.length === 0) {
      // conflict!
      return true;
    }
    // if we have at least one "true" - there is a conflict
    return !!filtered.find(o => o);
  };
  const merged = sids
    .map(findConflicts)
    .reduce((acc, cur) => ({
      read: [...(acc.read || []), cur.read],
      write: [...(acc.write || []), cur.write],
      execute: [...(acc.execute || []), cur.execute]
    }), {});
  if (principal) {
    // If user has suitable permissions, ignore user's roles permissions
    const principalConflicts = findConflicts(sid);
    for (const permission of ['read', 'write', 'execute']) {
      if (principalConflicts[permission] === false) {
        // No conflict (i.e., "false") for "permission"
        merged[permission] = [false];
      }
    }
  }
  return {
    read: getResolution(merged.read),
    write: getResolution(merged.write),
    execute: getResolution(merged.execute)
  };
};

const permissionEnabled = (extendedMask, shift = 0) => {
  return (extendedMask >> shift) & 0b11 > 0;
};

const readPermissionEnabled = (extendedMask) => {
  return permissionEnabled(extendedMask);
};

const writePermissionEnabled = (extendedMask) => {
  return permissionEnabled(extendedMask, 2);
};

const executePermissionEnabled = (extendedMask) => {
  return permissionEnabled(extendedMask, 4);
};

const management = (roleName) => (WrappedComponent, key) => {
  const Component = inject('authenticatedUserInfo')(
    observer(
      ({authenticatedUserInfo}) => {
        if (authenticatedUserInfo.loaded &&
          (authenticatedUserInfo.value.admin ||
          (authenticatedUserInfo.value.roles || [])
            .filter(r => r.name === roleName).length === 1)) {
          return WrappedComponent;
        }
        return null;
      }
    )
  );
  return <Component key={key} />;
};

const hasRole = (roleName) => ({props}) => {
  const {authenticatedUserInfo} = props;
  if (authenticatedUserInfo && authenticatedUserInfo.loaded) {
    return authenticatedUserInfo.value.admin ||
      (authenticatedUserInfo.value.roles || []).filter(r => r.name === roleName).length === 1;
  }
  return false;
};

const userHasRole = (user, roleName) => {
  if (user && user.roles && user.roles.length > 0 && roleName) {
    return user.admin || (user.roles || []).find(r => r.name === roleName);
  }
  return false;
};

const authenticationInfo = (...opts) => inject('authenticatedUserInfo')(...opts);

const refreshAuthenticationInfo = async ({props}) => {
  if (props) {
    const {authenticatedUserInfo} = props;
    if (authenticatedUserInfo) {
      return authenticatedUserInfo.fetch();
    }
  }
};

const manager = {
  archiveReader: management('ROLE_STORAGE_ARCHIVE_READER'),
  archiveManager: management('ROLE_STORAGE_ARCHIVE_MANAGER'),
  dtsManager: management('ROLE_DTS_MANAGER'),
  storageAdmin: management('ROLE_STORAGE_ADMIN'),
  pipeline: management('ROLE_PIPELINE_MANAGER'),
  versionedStorage: management('ROLE_VERSIONED_STORAGE_MANAGER'),
  folder: management('ROLE_FOLDER_MANAGER'),
  configuration: management('ROLE_CONFIGURATION_MANAGER'),
  storage: management('ROLE_STORAGE_MANAGER'),
  storageTag: management('ROLE_STORAGE_TAG_MANAGER'),
  toolGroup: management('ROLE_TOOL_GROUP_MANAGER'),
  entities: management('ROLE_ENTITIES_MANAGER'),
  billing: management('ROLE_BILLING_MANAGER')
};

const isManager = {
  archiveReader: hasRole('ROLE_STORAGE_ARCHIVE_READER'),
  archiveManager: hasRole('ROLE_STORAGE_ARCHIVE_MANAGER'),
  storageAdmin: hasRole('ROLE_STORAGE_ADMIN'),
  dtsManager: hasRole('ROLE_DTS_MANAGER'),
  pipeline: hasRole('ROLE_PIPELINE_MANAGER'),
  versionedStorage: hasRole('ROLE_VERSIONED_STORAGE_MANAGER'),
  folder: hasRole('ROLE_FOLDER_MANAGER'),
  configuration: hasRole('ROLE_CONFIGURATION_MANAGER'),
  storage: hasRole('ROLE_STORAGE_MANAGER'),
  storageTag: hasRole('ROLE_STORAGE_TAG_MANAGER'),
  toolGroup: hasRole('ROLE_TOOL_GROUP_MANAGER'),
  entities: hasRole('ROLE_ENTITIES_MANAGER'),
  billing: hasRole('ROLE_BILLING_MANAGER')
};

export default {
  readAllowed,
  readDenied,
  writeAllowed,
  writeDenied,
  executeAllowed,
  executeDenied,
  isOwner,
  extendMask,
  collapseMask,
  manager,
  isManager,
  hasRole,
  userHasRole,
  authenticationInfo,
  refreshAuthenticationInfo,
  buildMask,
  buildPermissionsMask,
  checkPermissionsSetConflicting,
  checkObjectPermissionsConflict,
  readPermissionEnabled,
  writePermissionEnabled,
  executePermissionEnabled
};
