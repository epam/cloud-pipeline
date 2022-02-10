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
 * @typedef {string[]} HCSTimeSeries
 */

import * as HCSConstants from './constants';
import fetchSequenceWellsInfo from './fetch-sequence-wells-info';
import generateHCSFileURLs from './generate-hcs-file-urls';

/**
 * @typedef {Object} HCSImageSequenceOptions
 * @property {string|number} storageId
 * @property {string} sequence
 * @property {string} directory
 * @property {S3Storage} s3Storage
 * @property {HCSTimeSeries[]} timeSeries
 */

class HCSImageSequence {
  /**
   * Initializes HCS Image Sequence object
   * @param {HCSImageSequenceOptions} options
   */
  constructor (options = {}) {
    const {
      storageId,
      sequence,
      directory,
      s3Storage,
      timeSeries = []
    } = options;
    this.storageId = Number.isNaN(Number(storageId))
      ? storageId
      : Number(storageId);
    this.sequence = sequence;
    this.id = sequence;
    this.directory = directory;
    this.s3Storage = s3Storage;
    this.timeSeries = timeSeries;
    this.omeTiffFileName = [directory, HCSConstants.OME_TIFF_FILE_NAME].join('/');
    this.offsetsJsonFileName = [directory, HCSConstants.OFFSETS_JSON_FILE_NAME].join('/');
    this.wellsMapFileName = [directory, HCSConstants.WELLS_MAP_FILE_NAME].join('/');
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
          .then(() => fetchSequenceWellsInfo(this))
          .then(resolve)
          .catch(reject);
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
    const promise = generateHCSFileURLs({
      s3Storage: this.s3Storage,
      storageId: this.storageId,
      path: this.wellsMapFileName
    });
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
    const promise = generateHCSFileURLs({
      s3Storage: this.s3Storage,
      storageId: this.storageId,
      path: this.omeTiffFileName
    });
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
    const promise = generateHCSFileURLs({
      s3Storage: this.s3Storage,
      storageId: this.storageId,
      path: this.offsetsJsonFileName
    });
    promise
      .then((url) => {
        this.offsetsJson = url;
      })
      .catch(() => {});
    return promise;
  }
}

export default HCSImageSequence;
