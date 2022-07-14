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

import * as HCSConstants from './constants';
import HCSImageWell from './hcs-image-well';

/**
 * @typedef {Object} HCSTimeSeries
 * @property {string} id
 * @property {string} name
 */

/**
 * @typedef {Object} HCSImageSequenceOptions
 * @property {HCSInfo} hcs
 * @property {string|number} storageId
 * @property {string} sequence
 * @property {string} directory
 * @property {string} sourceDirectory
 * @property {ObjectStorage} objectStorage
 * @property {string[]} timeSeries
 */

class HCSImageSequence {
  /**
   * Initializes HCS Image Sequence object
   * @param {HCSImageSequenceOptions} options
   */
  constructor (options = {}) {
    const {
      hcs,
      storageId,
      sequence,
      directory,
      sourceDirectory,
      objectStorage,
      timeSeries = []
    } = options;
    this.hcs = hcs;
    this.storageId = Number.isNaN(Number(storageId))
      ? storageId
      : Number(storageId);
    this.sequence = sequence;
    this.id = sequence;
    this.directory = directory;
    this.sourceDirectory = sourceDirectory;
    /**
     * @type {ObjectStorage} object storage wrapper
     */
    this.objectStorage = objectStorage;
    /**
     * @type {HCSTimeSeries[]}
     */
    this.timeSeries = timeSeries.map((time, index) => ({
      id: index,
      name: time
    }));
    this.omeTiffFileName = [directory, HCSConstants.OME_TIFF_FILE_NAME]
      .join(objectStorage.delimiter || '/');
    this.offsetsJsonFileName = [directory, HCSConstants.OFFSETS_JSON_FILE_NAME]
      .join(objectStorage.delimiter || '/');
    this.overviewOmeTiffFileName = [directory, HCSConstants.OVERVIEW_OME_TIFF_FILE_NAME]
      .join(objectStorage.delimiter || '/');
    this.overviewOffsetsJsonFileName = [directory, HCSConstants.OVERVIEW_OFFSETS_JSON_FILE_NAME]
      .join(objectStorage.delimiter || '/');
    this.wellsMapFileName = [directory, HCSConstants.WELLS_MAP_FILE_NAME]
      .join(objectStorage.delimiter);
    this._fetch = undefined;
    this.wells = [];
    this.error = undefined;
    this.omeTiff = undefined;
    this.offsetsJson = undefined;
  }

  fetch () {
    if (!this._fetch) {
      this._fetch = new Promise((resolve, reject) => {
        this.generateWellsMapURL()
          .then(() => this.objectStorage.getFileContent(this.wellsMapFileName, {json: true}))
          .then(json => HCSImageWell.parseWellsInfo(json, this.hcs))
          .then(resolve)
          .catch(e => reject(
            new Error(`Error fetching sequence ${this.id} info: ${e.message}`)
          ));
      });
      this._fetch
        .then((wells = []) => {
          this.wells = wells.slice();
        })
        .catch(e => {
          this.error = e.message;
        });
    }
    return this._fetch;
  }

  generateWellsMapURL () {
    const promise = this.objectStorage.generateFileUrl(this.wellsMapFileName);
    promise
      .then((url) => {
        this.wellsMap = url;
      })
      .catch((e) => {
        this.error = e.message;
      });
    return promise;
  }

  generateOMETiffURL () {
    const promise = this.objectStorage.generateFileUrl(this.omeTiffFileName);
    promise
      .then((url) => {
        this.omeTiff = url;
      })
      .catch((e) => {
        this.error = e.message;
      });
    return promise;
  }

  generateOffsetsJsonURL () {
    const promise = this.objectStorage.generateFileUrl(this.offsetsJsonFileName);
    promise
      .then((url) => {
        this.offsetsJson = url;
      })
      .catch(() => {});
    return promise;
  }

  generateOverviewOMETiffURL () {
    const promise = this.objectStorage
      .generateFileUrl(this.overviewOmeTiffFileName);
    promise
      .then((url) => {
        this.overviewOmeTiff = url;
      })
      .catch((e) => {
        this.error = e.message;
      });
    return promise;
  }

  generateOverviewOffsetsJsonURL () {
    const promise = this.objectStorage
      .generateFileUrl(this.overviewOffsetsJsonFileName);
    promise
      .then((url) => {
        this.overviewOffsetsJson = url;
      })
      .catch(() => {});
    return promise;
  }

  resignDataURLs () {
    return Promise.all([
      this.generateOMETiffURL(),
      this.generateOffsetsJsonURL(),
      this.generateOverviewOMETiffURL(),
      this.generateOverviewOffsetsJsonURL()
    ]);
  }
}

export default HCSImageSequence;
