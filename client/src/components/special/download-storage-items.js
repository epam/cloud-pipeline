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

/**
 * @typedef {Object} StorageItem
 * @property {string|number} storageId
 * @property {string} path
 */

import React from 'react';
import {message} from 'antd';
import GenerateDownloadUrls from '../../models/dataStorage/GenerateDownloadUrls';
import auditStorageAccessManager from '../../utils/audit-storage-access';

/**
 * @param {StorageItem[]} items
 * @returns {Promise<string[]>}
 */
async function downloadSingleStorageItems (items = []) {
  if (items.length === 0) {
    return Promise.resolve([]);
  }
  const storageId = items[0].storageId;
  const request = new GenerateDownloadUrls(storageId);
  await request.send({
    paths: items.map(item => item.downloadOverride || item.path)
  });
  if (request.error) {
    throw new Error(request.error);
  }
  return (request.value || []).map(o => o.url);
}

/**
 *
 * @param {StorageItem[]} items
 * @returns {Promise<void>}
 */
async function downloadStorageItems (items) {
  const storageIds = [...new Set(items.map(item => Number(item.storageId)))]
    .filter((id) => !Number.isNaN(id));
  const results = await Promise.all(
    storageIds.map((storageId) => downloadSingleStorageItems(
      items.filter(item => Number(item.storageId) === storageId)
    ))
  );
  const links = results.reduce((r, c) => ([...r, ...c]), []);
  links.forEach((link) => window.open(link, '_blank'));
  auditStorageAccessManager.reportReadAccess(...items.map((item) => ({
    storageId: item.storageId,
    path: item.downloadOverride || item.path,
    reportStorageType: 'S3'
  })));
}

export default async function handleDownloadItems (preferences, items = []) {
  const hide = message.loading('Downloading...', 0);
  try {
    if (preferences) {
      await preferences.fetchIfNeededOrWait();
    }
    const {maximum} = preferences
      ? preferences.facetedFilterDownload
      : undefined;
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
      await downloadStorageItems(items);
    }
  } catch (error) {
    message.error(error.message, 5);
  } finally {
    hide();
  }
}
