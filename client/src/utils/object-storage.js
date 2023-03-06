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

import S3Storage from '../models/s3-upload/s3-storage';
import DataStorageItemContent from '../models/dataStorage/DataStorageItemContent';
import GenerateDownloadUrl from '../models/dataStorage/GenerateDownloadUrl';
import storagesRequest from '../models/dataStorage/DataStorageAvailable';
import DataStorageRequest from '../models/dataStorage/DataStoragePage';
import DataStorageItemUpdateContent from '../models/dataStorage/DataStorageItemUpdateContent';

const parser = new DOMParser();

function checkS3Error (responseText, file) {
  let error = false;
  let code, message;
  try {
    const d = parser.parseFromString(responseText, 'application/xml');
    if (d && /^error$/i.test(d.documentElement.nodeName)) {
      error = true;
      const getValue = key => {
        const tag = d.documentElement.getElementsByTagName(key)[0];
        if (tag) {
          return tag.textContent;
        }
        return undefined;
      };
      code = getValue('Code');
      message = getValue('Message');
    }
  } catch (e) {
    return;
  }
  if (error) {
    if (/^NoSuchKey$/i.test(code) && file) {
      throw new Error(`${file} does not exist`);
    }
    if (message) {
      throw new Error(message);
    }
    if (code) {
      throw new Error(`code ${code}`);
    }
  }
}

async function readS3Response (response, asJSON = false, requestedFile = undefined) {
  const contentType = response.headers.get('content-type');
  if (!/^application\/xml$/i.test(contentType)) {
    return asJSON ? response.json() : response.text();
  }
  const text = await response.text();
  checkS3Error(text, requestedFile);
  if (asJSON) {
    return JSON.parse(text);
  }
  return text;
}

class ObjectStorage {
  constructor (options = {}, permissions = {}) {
    const {
      id,
      type,
      path,
      pathMask,
      region,
      delimiter = '/',
      mountPoint
    } = options;
    this.id = id;
    this.type = type;
    this.region = region;
    this.path = path;
    this.pathMask = pathMask;
    this.delimiter = delimiter;
    this.s3Storage = undefined;
    this.permissions = permissions;
    this.mountPoint = mountPoint;
    if (/^nfs$/i.test(type)) {
      if (mountPoint) {
        this.localRoot = mountPoint;
      } else {
        this.localRoot = '/cloud-data/'.concat(path.replace(/[:]/g, ''));
      }
    } else {
      this.localRoot = '/cloud-data/'.concat(path);
    }
  }

  get initialized () {
    if (this.id && this.type && this.path && /^s3$/i.test(this.type)) {
      return !!this.s3Storage;
    }
    return true;
  }

  async initialize (permissions = {}) {
    if (this.id && this.type && this.path && /^s3$/i.test(this.type)) {
      this.s3Storage = new S3Storage({
        id: this.id,
        type: this.type,
        path: this.path,
        region: this.region,
        ...permissions
      });
      await this.s3Storage.updateCredentials();
    }
  }

  async generateFileUrl (file) {
    if (!this.s3Storage) {
      await this.initialize(this.permissions);
    }
    if (this.s3Storage) {
      await this.s3Storage.refreshCredentialsIfNeeded();
      return this.s3Storage.getSignedUrl(file);
    }
    const request = new GenerateDownloadUrl(this.id, file);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    return atob((request.value || {}).url);
  }

  async getFileContent (file, options = {}) {
    const {
      json = false
    } = options;
    if (this.s3Storage) {
      await this.s3Storage.refreshCredentialsIfNeeded();
      const url = this.s3Storage.getSignedUrl(file);
      const response = await fetch(url);
      return readS3Response(response, json, file);
    }
    const request = new DataStorageItemContent(this.id, file);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    return atob((request.value || {}).content);
  }

  async getFolderContents (folder) {
    if (!this.id) {
      throw new Error(`Storage ID not specified`);
    }
    const id = this.id;
    function fetchPage (marker) {
      return new Promise((resolve) => {
        const request = new DataStorageRequest(
          id,
          decodeURIComponent(folder),
          false,
          false,
          50
        );
        request
          .fetchPage(marker)
          .then(() => {
            if (request.loaded) {
              const pathRegExp = new RegExp(`^${folder}\\/`, 'i');
              const nextPageMarker = (request.value || {}).nextPageMarker;
              const items = ((request.value || {}).results || [])
                .filter(item => pathRegExp.test(item.path));
              return Promise.resolve({items, nextPageMarker});
            } else {
              return Promise.resolve({items: []});
            }
          })
          .then(({items, nextPageMarker}) => {
            if (!nextPageMarker) {
              return Promise.resolve([items]);
            } else {
              return Promise.all([
                Promise.resolve(items),
                fetchPage(nextPageMarker)
              ]);
            }
          })
          .then(itemsArray => resolve(itemsArray.reduce((r, c) => ([...r, ...c]), [])))
          .catch(() => {
            resolve([]);
          });
      });
    }
    return fetchPage();
  }

  getRelativePath = (path) => {
    if (this.pathMask) {
      const e = (new RegExp(`^${this.pathMask}/(.+)$`, 'i')).exec(path);
      if (e && e.length) {
        return e[1];
      }
    }
    return path;
  };

  joinPaths = (...path) => {
    const removeSlashes = o => {
      let result = o || '';
      if (result.startsWith(this.delimiter)) {
        result = result.slice(1);
      }
      if (result.endsWith(this.delimiter)) {
        result = result.slice(0, -1);
      }
      return result;
    };
    return path.map(removeSlashes).join(this.delimiter);
  };

  getLocalPath = (path) => {
    return (this.localRoot || '').concat('/').concat(path).replace(/\/\//g, '/');
  }

  writeFile = async (path, content) => {
    const request = new DataStorageItemUpdateContent(this.id, path);
    await request.send(content);
    if (request.error) {
      throw new Error(request.error);
    }
  };
}

function findStorageByIdentifierFn (storageId) {
  return function predicate (storage) {
    return storage.id === Number(storageId);
  };
}

function findStorageByPathFn (storagePath) {
  return function predicate (storage) {
    const storageMask = new RegExp(`^${storage.pathMask}(/|$)`, 'i');
    const storagePathMask = new RegExp(`^${storage.path}(/|$)`, 'i');
    return storageMask.test(storagePath) || storagePathMask.test(storagePath);
  };
}

async function getStorages (storages) {
  if (!storages) {
    return [];
  }
  if (typeof storages.fetchIfNeededOrWait === 'function') {
    await storages.fetchIfNeededOrWait();
    if (storages.error) {
      throw new Error(storages.error);
    }
    return (storages.value || []);
  }
  return storages;
}

/**
 * Creates ObjectStorage instance
 * @param {*[]|Remote} storages - available storages
 * @param {string|number|Object} storage - storage id, storage path or storage object
 * @param {{read: boolean, write: boolean}} [permissions]
 * @param {{isURL: boolean?, generateCredentials: boolean?}} [options]
 * @return {Promise<ObjectStorage|undefined>}
 */
export async function createObjectStorageWrapper (
  storages,
  storage,
  permissions,
  options
) {
  const {
    isURL = false,
    generateCredentials = true
  } = options || {};
  let obj;
  let storagesArray = [];
  try {
    storagesArray = await getStorages(storages);
  } catch (e) {
    console.warn(e.message);
  }
  const filteredStoragesArray = storagesArray && typeof storagesArray.filter === 'function'
    ? storagesArray.filter(s => !s.shared)
    : undefined;
  if (!isURL && !Number.isNaN(Number(storage))) {
    const storageId = Number(storage);
    if (filteredStoragesArray) {
      obj = filteredStoragesArray.find(findStorageByIdentifierFn(storageId));
    }
    if (!obj) {
      obj = {id: storageId};
    }
  } else if (
    typeof storage === 'string' &&
    filteredStoragesArray
  ) {
    obj = filteredStoragesArray.find(findStorageByPathFn(storage));
  } else if (typeof storage === 'object' && storage.id) {
    obj = {...storage};
  }
  if (obj) {
    const objectStorage = new ObjectStorage(obj, permissions);
    if (generateCredentials) {
      await objectStorage.initialize(permissions);
    }
    return objectStorage;
  }
  return undefined;
}

async function getStorageFileAccessInfo (path) {
  const objectStorage = await createObjectStorageWrapper(
    storagesRequest,
    path,
    {write: false, read: true},
    {isURL: true, generateCredentials: false}
  );
  if (objectStorage) {
    if (objectStorage.pathMask) {
      const e = (new RegExp(`^${objectStorage.pathMask}/(.+)$`, 'i')).exec(path);
      if (e && e.length) {
        return {
          objectStorage,
          path: e[1]
        };
      }
    }
    return {
      objectStorage,
      path
    };
  }
  return undefined;
}

export {ObjectStorage, getStorageFileAccessInfo};
