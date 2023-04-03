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

import { loadOmeTiff } from '@hms-dbmi/viv';
import { fromUrl as geoTiffFromUrl } from 'geotiff';
import WorkersPool from './workers.pool';

/**
 * @typedef {Object} OMETIFFLoaderOptions
 * @property {String} url
 * @property {String} offsetsUrl
 */

const MAX_CHANNELS = 40;
let sharedPool;
function getSharedWorkersPool() {
  if (!sharedPool) {
    sharedPool = new WorkersPool();
  }
  return sharedPool;
}

export class OffsetsFileNotFoundError extends Error {
  /**
     *
     * @param {String} offsetsUrl
     */
  constructor(offsetsUrl) {
    if (offsetsUrl) {
      super(`offsets.json file not found: ${offsetsUrl}`);
    } else {
      super('offsets.json file not specified');
    }
  }
}

/**
 *
 * @param url
 * @param metadata
 * @param data
 * @returns {Promise<number|*>}
 */
async function getTotalImageCount(url, metadata, data) {
  const tiff = await geoTiffFromUrl(url);
  const firstImage = await tiff.getImage(0);
  const hasSubIFDs = Boolean(firstImage?.fileDirectory?.SubIFDs);
  if (hasSubIFDs) {
    return metadata.reduce((sum, imageMetadata) => {
      const {
        Pixels: { SizeC, SizeT, SizeZ },
      } = imageMetadata;
      const numImagesPerResolution = SizeC * SizeT * SizeZ;
      return numImagesPerResolution + sum;
    }, 1);
  }
  const levels = data[0].length;
  const {
    Pixels: { SizeC, SizeT, SizeZ },
  } = metadata[0];
  const numImagesPerResolution = SizeC * SizeT * SizeZ;
  return numImagesPerResolution * levels;
}

/**
 * Fetches OME-TIFF info
 * @param {OMETIFFLoaderOptions} options
 * @returns {Promise<*|{data: null}>}
 */
export async function fetchSourceInfo(options = {}) {
  const {
    url,
    offsetsUrl,
  } = options;
  const offsetsRequest = offsetsUrl
    ? await fetch(offsetsUrl)
    : undefined;
  const offsetsRequestFailed = !offsetsRequest || !offsetsRequest.ok;
  const offsets = !offsetsRequestFailed
    ? await offsetsRequest.json()
    : undefined;
  const source = await loadOmeTiff(url, { offsets, images: 'all', pool: getSharedWorkersPool() });

  if (offsetsRequestFailed) {
    const totalImageCount = await getTotalImageCount(
      url,
      source.map((s) => s.metadata),
      source.map((s) => s.data),
    );
    if (totalImageCount > MAX_CHANNELS) {
      throw new OffsetsFileNotFoundError(offsetsUrl);
    }
  }
  return source;
}
