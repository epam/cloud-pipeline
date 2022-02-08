/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import GetPermissions from '../../models/grant/GrantGet';
import RemovePermissions from '../../models/grant/GrantRemove';
import GrantPermission from '../../models/grant/GrantPermission';

const DATA_STORAGE_ACL = 'DATA_STORAGE';

/**
 * @typedef {Object} SharedStoragePermission
 * @property {string} name
 * @property {boolean} principal
 */

/**
 * @typedef {Object} SharedStoragePermissions
 * @property {number} mask
 * @property {SharedStoragePermission[]} permissions
 */

/**
 * Removes shared storage folder permission
 * @param {number} storageId
 * @param {SharedStoragePermission} permission
 * @returns {Promise<unknown>}
 */
function removePermission (storageId, permission) {
  if (!permission) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const request = new RemovePermissions(
      storageId,
      DATA_STORAGE_ACL,
      permission.name,
      permission.principal
    );
    request.fetch()
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        }
      })
      .then(() => resolve())
      .catch(e => {
        const {
          name,
          principal
        } = permission;
        console.warn(
          `Error removing permissions for ${principal ? 'user' : 'role'} ${name}: ${e.message}`
        );
        resolve();
      });
  });
}

/**
 * Gets shared storage folder' permissions
 * @param {number|undefined} storageId
 * @returns {Promise<SharedStoragePermissions>}
 */
export function getSharedStoragePermissions (storageId) {
  if (!storageId) {
    return Promise.reject(new Error('Storage not specified'));
  }
  return new Promise((resolve) => {
    const request = new GetPermissions(storageId, DATA_STORAGE_ACL);
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          const {permissions = []} = request.value;
          return Promise.resolve(permissions);
        }
        return Promise.resolve([]);
      })
      .then((permissions = []) => {
        const mask = permissions
          .reduce((reducedMask, current) => reducedMask & (current.mask || 0), 0b1111);
        return Promise.resolve({
          mask,
          permissions: permissions
            .map(({sid}) => sid && sid.name
              ? {name: sid.name, principal: !!sid.principal}
              : undefined
            )
            .filter(Boolean)
        });
      })
      .then(resolve)
      .catch(() => resolve());
  });
}

/**
 * Removes shared storage folder permissions recursively (one by one)
 * @param {Number} storageId
 * @param {SharedStoragePermission[]} permissions
 * @returns {Promise}
 */
function removeMultiplePermissions (storageId, permissions = []) {
  if (permissions.length === 0) {
    return Promise.resolve();
  }
  const [permission, ...restPermissions] = permissions;
  return new Promise((resolve) => {
    removePermission(storageId, permission)
      .then(() => removeMultiplePermissions(storageId, restPermissions))
      .then(() => resolve());
  });
}

/**
 * Removes shared storage folder permissions
 * @param {number} storageId
 * @param {boolean} [skip=false] - skip permissions removal
 * @returns {Promise}
 */
export function removeCurrentPermissions (storageId, skip = false) {
  if (skip) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    getSharedStoragePermissions(storageId)
      .then((currentPermissions) => {
        if (currentPermissions) {
          const {permissions = []} = currentPermissions;
          return Promise.resolve(permissions);
        }
        return Promise.resolve([]);
      })
      .then((permissions = []) => removeMultiplePermissions(storageId, permissions))
      .then(() => resolve())
      .catch(() => resolve());
  });
}

function grantPermissions (storageId, name, principal, mask) {
  return new Promise((resolve) => {
    const request = new GrantPermission();
    request.send({
      aclClass: DATA_STORAGE_ACL,
      id: storageId,
      mask,
      principal,
      userName: name
    })
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        }
      })
      .then(() => resolve())
      .catch(e => {
        console.warn(
          `Error setting permissions for ${principal ? 'user' : 'role'} ${name}: ${e.message}`
        );
        resolve();
      });
  });
}

/**
 * Grants shared storage permissions recursively (one by one)
 * @param {number} storageId
 * @param {number} mask
 * @param {SharedStoragePermission[]} permissions
 * @returns {Promise}
 */
function grantMultiplePermissions (storageId, mask, permissions = []) {
  if (permissions.length === 0) {
    return Promise.resolve();
  }
  const [permission, ...restPermissions] = permissions;
  return new Promise((resolve) => {
    grantPermissions(storageId, permission.name, permission.principal, mask)
      .then(() => grantMultiplePermissions(storageId, mask, restPermissions))
      .then(() => resolve());
  });
}

/**
 * Grants shared storage folder permissions. Returns promise that resolves to the storage identifier
 * @param {number} storageId
 * @param {number} mask
 * @param {SharedStoragePermission[]} permissions
 * @param {boolean} [replace=true] - true if current permissions should be removed
 * @returns {Promise<number>}
 */
export function grantSharedStoragePermissions (
  storageId,
  mask,
  permissions = [],
  replace = true
) {
  if (!storageId) {
    return Promise.reject(new Error('Storage not specified'));
  }
  return new Promise((resolve, reject) => {
    removeCurrentPermissions(storageId, !replace)
      .then(() => grantMultiplePermissions(storageId, mask, permissions))
      .then(() => resolve(storageId))
      .catch(reject);
  });
}
