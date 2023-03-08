/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

export function guessParentFolderForLocalPath (path, isFolder) {
  if (isFolder === true) {
    return path;
  }
  const parts = (path || '').split('/');
  const parent = parts.slice(0, -1).join('/');
  const object = parts.pop();
  if (isFolder === false) {
    return parent;
  }
  if (/\.[^./]+$/i.test(object)) {
    return parent;
  }
  return path;
}

export function correctNFSPath (path) {
  let correctedPath = (path || '').trim();
  const e = /^\/?cloud-data\/([^/]+)\/(.*)$/i.exec(correctedPath);
  if (e && e.length === 3) {
    correctedPath = `nfs://${e[1]}:/${e[2]}`;
  }
  return correctedPath;
}

export function findStorageByPath (path, storages = []) {
  let lowerCasedPath = (path || '').toLowerCase();
  if (lowerCasedPath.endsWith('/')) {
    lowerCasedPath = lowerCasedPath.slice(0, -1);
  }
  const notSharedStorages = storages.filter((storage) => !storage.shared);
  const storageMatch = (aStorage) => {
    const {
      pathMask = ''
    } = aStorage || {};
    let pathMaskCorrected = pathMask.toLowerCase();
    if (pathMaskCorrected.endsWith('/')) {
      pathMaskCorrected = pathMaskCorrected.slice(0, -1);
    }
    return pathMaskCorrected === lowerCasedPath ||
      lowerCasedPath.startsWith(`${pathMaskCorrected}/`);
  };
  return notSharedStorages.find(storageMatch);
}

export function findStorageByIdentifier (identifier, storages = []) {
  return storages
    .find((aStorage) => aStorage.id === Number(identifier));
}

/**
 * @typedef {Object} StorageLinkInfoOptions
 * @property {string|number} [storageId]
 * @property {string} [path]
 * @property {boolean|undefined} [isFolder]
 * @property {*[]} [storages]
 */

/**
 * @param {StorageLinkInfoOptions} options
 * @returns {{path: string, storageId: number}}
 */
export function getStorageLinkInfo (options) {
  const {
    storages = [],
    storageId,
    path,
    isFolder
  } = options;
  let storage;
  let correctedPath = correctNFSPath(path);
  if (storageId && !Number.isNaN(Number(storageId))) {
    storage = findStorageByIdentifier(Number(storageId), storages);
  } else if (correctedPath) {
    storage = findStorageByPath(correctedPath, storages);
  }
  let relativePath = correctedPath || '';
  if (storage && relativePath.startsWith((storage.pathMask || '').toLowerCase())) {
    relativePath = relativePath.slice((storage.pathMask || '').length);
  }
  if (relativePath.startsWith('/')) {
    relativePath = relativePath.slice(1);
  }
  const objectPath = guessParentFolderForLocalPath(relativePath, isFolder);
  let objectStorageId;
  if (storage) {
    objectStorageId = storage.id;
  } else if (!Number.isNaN(Number(storageId))) {
    objectStorageId = Number(storageId);
  }
  return {
    storageId: objectStorageId,
    path: objectPath
  };
}
