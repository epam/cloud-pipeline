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

import GenerateDownloadUrls from '../../models/dataStorage/GenerateDownloadUrls';

/**
 * @param {StorageItem[]} items
 * @returns {Promise<string[]>}
 */
async function downloadSingleStorageItems (items = []) {
  console.log(items);
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
export default async function downloadStorageItems (items) {
  const storageIds = [...new Set(items.map(item => Number(item.storageId)))]
    .filter((id) => !Number.isNaN(id));
  const results = await Promise.all(
    storageIds.map((storageId) => downloadSingleStorageItems(
      items.filter(item => Number(item.storageId) === storageId)
    ))
  );
  const links = results.reduce((r, c) => ([...r, ...c]), []);
  links.forEach((link) => window.open(link, '_blank'));
}
