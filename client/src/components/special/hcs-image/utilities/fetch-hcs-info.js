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

import DataStorageItemContent from '../../../../models/dataStorage/DataStorageItemContent';
import storages from '../../../../models/dataStorage/DataStorageAvailable';
import S3Storage from '../../../../models/s3-upload/s3-storage';
import HCSImageSequence from './hcs-image-sequence';

async function parseStorageFullPath (fullPath) {
  const e = /^(.+):\/\/([^/]+)\/(.+)$/.exec(fullPath);
  if (e && e.length >= 4) {
    const mask = `${e[1]}://${e[2]}`;
    const relative = e[3];
    await storages.fetchIfNeededOrWait();
    const storage = (storages.value || [])
      .find(o => (o.pathMask || '').toLowerCase() === mask.toLowerCase());
    if (storage) {
      return {
        path: relative,
        storageId: storage.id
      };
    }
  }
  return {path: fullPath};
}

async function getS3StorageInfo (storageId) {
  await storages.fetchIfNeededOrWait();
  const storage = (storages.value || [])
    .find(o => o.id === Number(storageId));
  if (storage) {
    return /^s3$/i.test(storage.type) ? storage : undefined;
  }
  return undefined;
}

/**
 * @typedef {Object} HCSFileInfo
 * @property {string|number} storageId
 * @property {string} path
 */

/**
 *
 * @param {HCSFileInfo} options
 */
export default function fetchHCSInfo (options = {}) {
  const {
    storageId,
    path
  } = options;
  if (!storageId || !path) {
    return Promise.reject(new Error('`storageId` and `path` must be specified for HCS image'));
  }
  return new Promise(async (resolve, reject) => {
    const request = new DataStorageItemContent(storageId, path);
    try {
      await request.fetch();
      if (!request.loaded || request.error || !request.value || !request.value.content) {
        // eslint-disable-next-line
        throw new Error(request.error || `Error fetching contents for ${path} (storage #${storageId})`);
      }
      const text = atob(request.value.content);
      const json = JSON.parse(text);
      const {
        previewDir: directory,
        plate_height: height,
        plate_width: width,
        time_series_details: timeSeriesDetails = {}
      } = json;
      const {
        previewStorageId = storageId,
        path: previewDirectory
      } = await parseStorageFullPath(directory);
      const s3StorageInfo = await getS3StorageInfo(previewStorageId);
      const s3Storage = s3StorageInfo
        ? new S3Storage({...s3StorageInfo, write: false, read: true})
        : undefined;
      const sequences = Object.keys(timeSeriesDetails);
      if (sequences.length === 0) {
        throw new Error('No sequences found');
      }
      resolve({
        storageId: Number.isNaN(Number(previewStorageId))
          ? previewStorageId
          : Number(previewStorageId),
        directory: previewDirectory,
        width: Number.isNaN(Number(width)) ? 0 : Number(width),
        height: Number.isNaN(Number(height)) ? 0 : Number(height),
        sequences: sequences
          .map(sequence => new HCSImageSequence({
            storageId: previewStorageId,
            s3Storage,
            sequence,
            directory: (previewDirectory || '')
              .split('/')
              .concat(sequence)
              .filter(o => o.length)
              .join('/'),
            timeSeries: timeSeriesDetails[sequence] || []
          }))
      });
    } catch (e) {
      reject(e);
    }
  });
}
