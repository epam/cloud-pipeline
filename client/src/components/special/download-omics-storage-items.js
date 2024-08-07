/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

/**
 * @typedef {Object} StorageItem
 * @property {string|number} storageId
 * @property {string} path
 */

import React from 'react';
import {message} from 'antd';
import GenerateDownloadUrls from '../../models/dataStorage/GenerateDownloadUrls';
import auditStorageAccessManager from '../../utils/audit-storage-access';
import OmicsStorage, {ItemType} from '../../models/omics-download/omics-download';

export class OmicsMetadata {
  constructor (config, items) {
    this.region = config.region;
    this.storage = config.storageInfo;
    this.cloudStorageId = config.storageInfo.cloudStorageId;
    this.readSetId = config.readSetId;
    this.files = items;
  }

  async createOmicClient () {
    const config = {
      region: this.region,
      storage: this.storage,
      readSetId: this.readSetId,
      sequenceStoreId: this.cloudStorageId
    };
    try {
      this.omicsStorage = new OmicsStorage(config);
      return await this.omicsStorage.createClient();
    } catch (err) {
      message.error(err.message, 5);
      return false;
    }
  }

  async getMetadataInfo () {
    const clientCreated = await this.createOmicClient();
    if (clientCreated) {
      try {
        const filesInfo = await this.omicsStorage.getFilesMetadata(this.files);
        return filesInfo;
      } catch (err) {
        message.error(err.message, 5);
      }
    }
  }
}

async function getItemsInfo (config, items) {
  try {
    const omicsMetadata = new OmicsMetadata(config, items);
    const filesInfo = await omicsMetadata.getMetadataInfo();
    return filesInfo;
  } catch (error) {
    message.error(error.message, 5);
  }
}

async function getFolderFiles (items, config) {
  const itemsInfo = items.map(item => {
    const [readSetId, sourceName] = item.path.split('/');
    return {
      readSetId,
      sourceName,
      ...item
    };
  });
  let files = [];
  const readSetIds = [...new Set(itemsInfo.map(item => item.readSetId))];
  for (const readSetId of readSetIds) {
    const storageConfig = {
      readSetId,
      ...config
    };
    const storageItems = itemsInfo.filter(item => item.readSetId === readSetId);
    files = [...files, ...await getItemsInfo(storageConfig, storageItems)];
  }
  return files;
}

async function getStorageFiles (items, config) {
  const itemsType = items[0].type;
  if (itemsType === ItemType.FOLDER) {
    const files = await getFolderFiles(items, config);
    return files;
  } else {
    return items.map(item => ({
      itemPath: item.path,
      path: item.path
    }));
  }
}

/**
 *
 * @param {StorageItem[]} items
 * @param {Object} config
 * @returns {Promise<void>}
 */
export async function downloadStorageItems (items, config) {
  try {
    const storageInfo = {
      storageId: config.storageInfo.id,
      files: await getStorageFiles(items, config)
    };
    const links = await getItemsLinks(storageInfo);
    for (const link of links) {
      window.open(link, '_blank');
    }
    auditStorageAccessManager.reportReadAccess(...storageInfo.files
      .map(file => ({
        storageId: storageInfo.storageId,
        path: file.path,
        reportStorageType: 'S3'
      })));
  } catch (error) {
    message.error(error.message, 5);
  }
}

export default async function handleDownloadOmicsItems (preferences, items = [], config) {
  const hide = message.loading('Downloading...', 0);
  try {
    if (preferences) {
      await preferences.fetchIfNeededOrWait();
    }
    const {maximum} = (preferences
      ? preferences.facetedFilterDownload
      : undefined) || {};
    if (maximum && maximum < items.length) {
      message.info(
        (
          <span>
            {/* eslint-disable-next-line max-len */}
            It is allowed to download up to <b>{maximum}</b> file{maximum === 1 ? '' : 's'} at a time.
          </span>
        ),
        5
      );
    } else {
      await downloadStorageItems(items, config);
    }
  } catch (error) {
    message.error(error.message, 5);
  } finally {
    hide();
  }
}

/**
 * @param {StorageItem} storage
 * @returns {Promise<{name: string, url: string}[]>}
 */
async function getItemsLinks (storage) {
  const {storageId, files: items} = storage;
  if (items.length === 0) {
    return Promise.resolve([]);
  }

  const request = new GenerateDownloadUrls(storageId);
  await request.send({
    paths: items.map(item => item.itemPath)
  });
  if (request.error) {
    message.error(request.error, 5);
    return;
  }
  return (request.value || []).map(o => o.url);
}
