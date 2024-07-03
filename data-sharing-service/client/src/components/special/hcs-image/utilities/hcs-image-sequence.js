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
import HCSURLsManager from './hcs-urls-manager';
import HCSImageMetadataCache from './hcs-image-metadata-cache';

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
    const {
      width: plateWidth = 10,
      height: plateHeight = 10
    } = hcs || {};
    this.plateWidth = plateWidth;
    this.plateHeight = plateHeight;
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
    this.wellsMapFileName = [directory, HCSConstants.WELLS_MAP_FILE_NAME]
      .join(objectStorage.delimiter);
    this._fetch = undefined;
    this.wells = [];
    this.error = undefined;
    this.hcsURLsManager = new HCSURLsManager(this.objectStorage);
    this.hcsImageMetadataCache = new HCSImageMetadataCache(this.objectStorage);
  }

  reportReadAccess = () => this.hcsURLsManager.reportReadAccess();

  addURLsGeneratedListener = (listener) =>
    this.hcsURLsManager.addURLsGeneratedListener(listener);

  removeURLsGeneratedListener = (listener) =>
    this.hcsURLsManager.removeURLsGeneratedListener(listener);

  destroy () {
    this.hcsURLsManager.destroy();
    this.hcsImageMetadataCache.destroy();
    this.hcsURLsManager = undefined;
    this.hcsImageMetadataCache = undefined;
    this.wells.forEach(aWell => aWell.destroy());
    this.wells = undefined;
    this.objectStorage = undefined;
  }

  fetch () {
    if (!this._fetch) {
      this._fetch = new Promise((resolve, reject) => {
        this.generateWellsMapURL()
          .then(() => this.objectStorage.getFileContent(this.wellsMapFileName, {json: true}))
          .then(json => HCSImageWell.parseWellsInfo(
            json,
            this
          ))
          .then((wells = []) => {
            this.wells = wells.slice();
            return Promise.resolve();
          })
          .then(() => this.fetchMetadata())
          .then(resolve)
          .catch(e => {
            this.error = e.message;
            reject(
              new Error(`Error fetching sequence ${this.id} info: ${e.message}`)
            );
          });
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

  fetchMetadata = () => {
    if (!this.metadataPromise) {
      this.metadataPromise = new Promise((resolve) => {
        Promise.all(this.wells.map((aWell) => aWell.fetchMetadata()))
          .then(() => resolve())
          .catch(() => resolve());
      });
    }
    return this.metadataPromise;
  };
}

export default HCSImageSequence;
