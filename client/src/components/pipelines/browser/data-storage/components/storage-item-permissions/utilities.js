import roleModel from '../../../../../../utils/roleModel';
import {alphabeticalSorter} from '../../../../../../utils/sorting';
import UpdatePathPermissions
from '../../../../../../models/dataStorage/permissions/update-path-permissions';
import FetchPathPermissions
from '../../../../../../models/dataStorage/permissions/fetch-path-permissions';

/**
 * @typedef {Object} StorageItemPermissionSID
 * @property {string} name
 * @property {boolean} isPrincipal
 *
 */
/**
 * @typedef {Object} StorageItemPermission
 * @property {StorageItemPermissionSID} sid
 * @property {number} storageId
 * @property {string} storagePath
 * @property {string} type
 * @property {number} mask
 */
/**
 * @typedef {Object} StorageItemInfo
 * @property {string} path
 * @property {string} type
 */
/**
 * Fetches storage item permissions
 * @param storageId {number}
 * @param storagePath {StorageItemInfo}
 * @returns {Promise<StorageItemPermission[]>}
 */
export async function fetchStorageItemPermissions (storageId, storagePath) {
  const request = new FetchPathPermissions(storageId, storagePath.path, storagePath.type);
  await request.fetch();
  if (request.error) {
    console.warn(
      // eslint-disable-next-line max-len
      `error fetching permissions for #${storageId} ${storagePath.type} ${storagePath.path}: ${request.error}`
    );
    return [];
  }
  const result = request.value || [];
  return result.map((r) => {
    const {
      name,
      principal: isPrincipal,
      mask
    } = r;
    return {
      storageId,
      storagePath: storagePath.path,
      type: storagePath.type,
      mask: convertFromPayloadMask(mask),
      sid: {
        name,
        isPrincipal
      }
    };
  });
}

/**
 * Fetches storage item permissions
 * @param storageId {number}
 * @param storagePaths {StorageItemInfo[]}
 * @returns {Promise<StorageItemPermission[]>}
 */
export async function fetchStorageItemsPermissions (storageId, storagePaths = []) {
  const results = await Promise.all(storagePaths.map((sp) => fetchStorageItemPermissions(
    storageId,
    sp
  )));
  return results.reduce((acc, current) => acc.concat(current), []);
}

/**
 * @param mask {number}
 * @returns {number}
 */
function convertToPayloadMask (mask) {
  const write = roleModel.writeAllowed({mask});
  return write ? 0b101 : 0b1;
}

/**
 * @param mask {number}
 * @returns {number}
 */
function convertFromPayloadMask (mask) {
  const write = roleModel.writeAllowed({mask}, true);
  return write ? 0b11 : 0b1;
}

/**
 * @param storageId {number}
 * @param permissions {StorageItemPermission[]}
 * @param initial {StorageItemPermission[]}
 * @returns {Promise<void>}
 */
export async function saveStorageItemPermissions (storageId, permissions, initial = []) {
  const uniquePaths = new Set();
  for (const permission of [...permissions, ...initial]) {
    const sp = permission.storagePath || '/';
    uniquePaths.add(`${permission.type}|${sp}`);
  }
  const payloads = [...uniquePaths].map((up) => {
    const [type, ...pathComponents] = up.split('|');
    const path = pathComponents.join('|');
    const pathPermissions = permissions
      .filter(p => (p.storagePath || '/') === path && p.type === type);
    if (pathPermissions.length > 0) {
      return {
        path,
        type,
        permissions: pathPermissions.map((p) => ({
          principal: p.sid.isPrincipal,
          name: p.sid.name,
          mask: convertToPayloadMask(p.mask)
        }))
      };
    }
    return {
      path,
      type
    };
  });
  const request = new UpdatePathPermissions(storageId);
  await request.send(payloads);
  if (request.error) {
    throw new Error(request.error);
  }
}

/**
 * Returns SID string representation
 * @param sid {StorageItemPermissionSID}
 * @returns {string}
 */
export function getSIDKey (sid) {
  return `${sid.isPrincipal ? 'user' : 'group'}|${sid.name}`;
}

/**
 * Returns SID string representation
 * @param sidKey {string}
 * @returns {StorageItemPermissionSID}
 */
export function parseSIDKey (sidKey) {
  const [type, ...name] = sidKey.split('|');
  if (type === 'user' || type === 'group') {
    return {
      isPrincipal: type === 'user',
      name: name.join('|')
    };
  }
  return {
    isPrincipal: true,
    name: sidKey
  };
}

/**
 * @param a {StorageItemPermission}
 * @returns {string}
 */
function getStorageItemPermissionHash (a) {
  return `${a.storageId}|${a.type}|${a.storagePath ?? ''}|${getSIDKey(a.sid)}|${a.mask}`;
}

/**
 * @param a {StorageItemPermission}
 * @param b {StorageItemPermission}
 * @return {boolean}
 */
export function storageItemPermissionsEqual (a, b) {
  if (!a || !b) return false;
  return getStorageItemPermissionHash(a) === getStorageItemPermissionHash(b);
}

/**
 * @param a {StorageItemPermission[]}
 * @param b {StorageItemPermission[]}
 * @return {this is *[]|boolean}
 */
export function storageItemPermissionsSetsEqual (a, b) {
  const aa = [...new Set((a ?? []).map(getStorageItemPermissionHash))].sort(alphabeticalSorter);
  const bb = [...new Set((b ?? []).map(getStorageItemPermissionHash))].sort(alphabeticalSorter);
  if (aa.length !== bb.length) {
    return false;
  }
  for (let i = 0; i < aa.length; i++) {
    if (aa[i] !== bb[i]) {
      return false;
    }
  }
  return true;
}

/**
 * @typedef {Object} StorageItemsNormalizedPermissions
 * @property {boolean} writeAllowed
 * @property {boolean?} writeAllowedIndeterminate
 */

/**
 * @param permissions {StorageItemPermission[]}
 * @return {StorageItemsNormalizedPermissions}
 */
export function normalizePermissions (permissions = []) {
  const allWriteAllowed = !permissions
    .some(permission => !roleModel.writeAllowed({mask: permission.mask}));
  const allWriteDenied = !permissions
    .some(permission => roleModel.writeAllowed({mask: permission.mask}));
  return {
    writeAllowed: allWriteAllowed,
    writeAllowedIndeterminate: !allWriteDenied && !allWriteAllowed
  };
}

/**
 * @param a {StorageItemInfo[]}
 * @param b {StorageItemInfo[]}
 * @returns {boolean}
 */
export function storagePathsAreEqual (a, b) {
  const aSet = [...new Set(a.map((o) => `${o.type}|${o.path}`))].sort(alphabeticalSorter);
  const bSet = [...new Set(b.map((o) => `${o.type}|${o.path}`))].sort(alphabeticalSorter);
  if (aSet.length !== bSet.length) {
    return false;
  }
  for (let i = 0; i < aSet.length; i++) {
    if (aSet[i] !== bSet[i]) {
      return false;
    }
  }
  return true;
}

/**
 * @param storagePaths {StorageItemInfo[]}
 * @returns {string}
 */
export function getStoragePathsDescription (storagePaths) {
  const paths = storagePaths.map((p) => {
    if (!p.path || p.path === '' || p.path === '/') {
      return 'Root folder';
    }
    return p.path.split('/').pop();
  });
  if (paths.length === 1) {
    return paths[0];
  }
  return `${paths.length} storage items`;
}
