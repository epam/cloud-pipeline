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

function parseCoordinates (key) {
  const e = /^([\d]+)_([\d]+)$/.exec(key);
  if (e && e.length >= 3) {
    return {
      x: Number(e[1]),
      y: Number(e[2])
    };
  }
  return undefined;
}

/**
 * @typedef {Object} HCSSequenceInfo
 * @property {string|number} storageId
 * @property {string} omeTiffFileName
 * @property {string} offsetsJsonFileName
 * @property {string} wellsMapFileName
 */

/**
 *
 * @param {HCSSequenceInfo} sequence
 * @returns {Promise<unknown>}
 */
export default function fetchSequenceWellsInfo (sequence) {
  const {
    storageId,
    wellsMapFileName
  } = sequence;
  if (!storageId || !wellsMapFileName) {
    // eslint-disable-next-line
    return Promise.reject(new Error('`storageId` and `wellsMapFileName` must be specified for HCS sequence'));
  }
  return new Promise(async (resolve, reject) => {
    const request = new DataStorageItemContent(storageId, wellsMapFileName);
    try {
      await request.fetch();
      if (!request.loaded || request.error || !request.value || !request.value.content) {
        // eslint-disable-next-line
        throw new Error(request.error || `Error fetching wells info ${wellsMapFileName} (storage #${storageId})`);
      }
      const text = atob(request.value.content);
      const json = JSON.parse(text);
      resolve(
        Object.keys(json || {})
          .map(key => ({
            key,
            parsed: parseCoordinates(key)
          }))
          .filter(o => o.parsed)
          .map(({key, parsed}) => ({
            ...parsed,
            ...json[key]
          }))
          .map((wellInfo, index) => ({
            id: `well_${index}`,
            x: wellInfo.x,
            y: wellInfo.y,
            width: Number(wellInfo.width),
            height: Number(wellInfo.height),
            radius: wellInfo.round_radius ? Number(wellInfo.round_radius) : undefined,
            images: Object.keys(wellInfo.to_ome_wells_mapping || {})
              .map(key => ({key, parsed: parseCoordinates(key)}))
              .filter(({parsed}) => parsed)
              .map(({key, parsed}) => ({
                ...parsed,
                id: (wellInfo.to_ome_wells_mapping || {})[key]
              }))
          }))
      );
    } catch (e) {
      reject(e);
    }
  });
}
