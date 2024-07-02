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

import HCSImageSequence from './hcs-image-sequence';
import parseHCSFileParts from './parse-hcs-file-parts';
import {createObjectStorageWrapper} from '../../../../utils/object-storage';

/**
 * @typedef {Object} HCSInfoOptions
 * @property {number|string} storageId
 * @property {ObjectStorage} objectStorage
 * @property {string} directory
 * @property {string} sourceDirectory
 * @property {number} width
 * @property {number} height
 * @property {Object} timeSeriesDetails
 */

class HCSInfo {
  /**
   * @param {HCSInfoOptions} options
   */
  constructor (options = {}) {
    const {
      storageId,
      objectStorage,
      directory,
      sourceDirectory,
      width,
      height,
      timeSeriesDetails = {}
    } = options;
    const sequences = Object.keys(timeSeriesDetails);
    if (sequences.length === 0) {
      throw new Error('No sequences found');
    }
    /**
     * Storage identifier
     * @type {number|string}
     */
    this.storageId = Number.isNaN(Number(storageId))
      ? storageId
      : Number(storageId);
    /**
     * Object storage wrapper
     * @type {ObjectStorage}
     */
    this.objectStorage = objectStorage;
    /**
     * Preview directory
     * @type {string}
     */
    this.directory = directory;
    /**
     * Source directory
     * @type {string}
     */
    this.sourceDirectory = sourceDirectory;
    /**
     * Plate width
     * @type {number}
     */
    this.width = Number.isNaN(Number(width)) ? 0 : Number(width);
    /**
     * Plate height
     * @type {number}
     */
    this.height = Number.isNaN(Number(height)) ? 0 : Number(height);
    /**
     * Sequences info
     * @type {HCSImageSequence[]}
     */
    this.sequences = sequences
      .map(sequence => new HCSImageSequence({
        hcs: this,
        storageId,
        objectStorage,
        sequence,
        sourceDirectory,
        directory: (directory || '')
          .split(objectStorage ? objectStorage.delimiter : '/')
          .concat(sequence)
          .filter(o => o.length)
          .join(objectStorage ? objectStorage.delimiter : '/'),
        timeSeries: timeSeriesDetails[sequence] || []
      }));
    this.sequences
      .forEach(aSequence => aSequence.addURLsGeneratedListener(this.sequenceURLsRegenerated));
    this.listeners = [];
  }

  addURLsRegeneratedListener = (listener) => {
    this.removeURLsRegeneratedListener(listener);
    this.listeners.push(listener);
  };

  removeURLsRegeneratedListener = (listener) => {
    this.listeners = this.listeners.filter(aListener => aListener !== listener);
  };

  sequenceURLsRegenerated = (sequence) => {
    this.listeners
      .filter(aListener => typeof aListener === 'function')
      .forEach(aListener => aListener(sequence, this));
  };

  destroy () {
    this.listeners = undefined;
    this.sequences.forEach(aSequence => aSequence.destroy());
    this.sequences = undefined;
    this.objectStorage = undefined;
  }

  /**
   * @typedef {Object} HCSFileInfo
   * @property {string|number} storageId
   * @property {string} path
   * @property {Object} [storageInfo]
   */

  /**
   *
   * @param {HCSFileInfo} options
   */
  static fetch (options = {}) {
    const {
      storageInfo,
      storageId,
      path
    } = options;
    if ((!storageId && !storageInfo) || !path) {
      return Promise.reject(new Error('`storageId` and `path` must be specified for HCS image'));
    }
    return new Promise(async (resolve, reject) => {
      try {
        const objectStorage = await createObjectStorageWrapper(
          undefined,
          storageId || storageInfo,
          {write: false, read: true}
        );
        const hcsPathInfo = parseHCSFileParts(
          path,
          objectStorage.delimiter
        );
        if (!hcsPathInfo) {
          throw new Error('Not a .hcs file');
        }
        const json = await objectStorage.getFileContent(path, {json: true});
        const {
          previewDir: previewDirectory
        } = hcsPathInfo;
        const {
          sourceDir: sourceDirectory,
          plate_height: height,
          plate_width: width,
          time_series_details: timeSeriesDetails = {}
        } = json;
        resolve(
          new HCSInfo({
            storageId,
            directory: previewDirectory,
            sourceDirectory,
            width,
            height,
            objectStorage,
            timeSeriesDetails
          })
        );
      } catch (e) {
        reject(e);
      }
    });
  }
}

export default HCSInfo;
