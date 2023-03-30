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

import {S3Storage} from '../models/s3Storage/s3Storage';
import DataStorageItemContent from '../models/dataStorage/DataStorageItemContent';
import GenerateDownloadUrl from '../models/dataStorage/GenerateDownloadUrl';
import auditStorageAccessManager from './audit-storage-access';

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
  constructor (options = {}) {
    const {
      id,
      type,
      path,
      region,
      delimiter = '/'
    } = options;
    this.id = id;
    this.type = type;
    this.region = region;
    this.path = path;
    this.delimiter = delimiter;
    this.s3Storage = undefined;
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
      auditStorageAccessManager.reportReadAccess({
        storageId: this.id,
        path: file,
        reportStorageType: 'S3'
      });
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
}

function findStorageByIdentifierFn (storageId) {
  return function predicate (storage) {
    return storage.id === Number(storageId);
  };
}

function findStorageByPathFn (storagePath) {
  const pathMask = storagePath && storagePath.endsWith('/')
    ? storagePath.slice(0, -1)
    : (storagePath || '');
  const pathMaskRegExp = new RegExp(`^${pathMask}$`, 'i');
  return function predicate (storage) {
    return pathMaskRegExp.test(storage.pathMask);
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
 * @return {Promise<ObjectStorage|undefined>}
 */
export async function createObjectStorageWrapper (storages, storage, permissions) {
  let obj;
  let storagesArray = [];
  try {
    storagesArray = await getStorages(storages);
  } catch (e) {
    console.warn(e.message);
  }
  if (!Number.isNaN(Number(storage))) {
    const storageId = Number(storage);
    if (storagesArray && typeof storagesArray.find === 'function') {
      obj = storagesArray.find(findStorageByIdentifierFn(storageId));
    }
    if (!obj) {
      obj = {id: storageId};
    }
  } else if (
    typeof storage === 'string' &&
    storagesArray &&
    typeof storagesArray.find === 'function'
  ) {
    obj = storagesArray.find(findStorageByPathFn(storage));
  } else if (typeof storage === 'object' && storage.id) {
    obj = {...storage};
  }
  if (obj) {
    const objectStorage = new ObjectStorage(obj);
    await objectStorage.initialize(permissions);
    return objectStorage;
  }
  return undefined;
}

export {ObjectStorage};
