/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import auditStorageAccessManager from '../../../../utils/audit-storage-access';

const second = 1000;
const minute = 60 * second;
const REGENERATE_URLS_TIMEOUT = 10 * minute;

class HCSURLsManager {
  /**
   * @param {ObjectStorage} objectStorage
   */
  constructor (objectStorage) {
    /**
     * @type {ObjectStorage}
     */
    this.objectStorage = objectStorage;
    /**
     * @type {string}
     */
    this.omeTiff = undefined;
    /**
     * @type {string}
     */
    this.offsetsJson = undefined;
    /**
     * @type {string}
     */
    this.omeTiffURL = undefined;
    /**
     * @type {string}
     */
    this.offsetsJsonURL = undefined;
    this.listeners = [];
    /**
     * @type {string}
     */
    this.error = undefined;
  }

  changeObjectStorage (objectStorage) {
    const {
      id: currentId
    } = this.objectStorage || {};
    const {
      id
    } = objectStorage || {};
    if (currentId !== id && objectStorage) {
      this.objectStorage = objectStorage;
      this.clearTimeouts();
      this.omeTiff = undefined;
      this.omeTiffURL = undefined;
      this.currentPromise = undefined;
    }
  }

  setActiveURL = (url, offsets) => {
    if (this.omeTiff === url && this.offsetsJson === offsets && this.currentPromise) {
      return this.currentPromise;
    }
    this.omeTiff = url;
    this.offsetsJson = offsets;
    this.omeTiffURL = undefined;
    this.offsetsJsonURL = undefined;
    this.currentPromise = this.generateURLs();
    return this.currentPromise;
  }

  destroy = () => {
    this.clearTimeouts();
    this.listeners = undefined;
    this.currentPromise = undefined;
    this.objectStorage = undefined;
  };

  addURLsGeneratedListener = (listener) => {
    this.removeURLsGeneratedListener(listener);
    this.listeners.push(listener);
  };

  removeURLsGeneratedListener = (listener) => {
    this.listeners = this.listeners.filter(aListener => aListener !== listener);
  };

  clearTimeouts = () => {
    clearTimeout(this.urlsRegenerationTimer);
    this.urlsRegenerationTimer = undefined;
  };

  generateOMETiffURL () {
    if (!this.objectStorage) {
      this.omeTiffURL = undefined;
      return Promise.resolve();
    }
    const promise = this.objectStorage.generateFileUrl(this.omeTiff);
    promise
      .then((url) => {
        this.omeTiffURL = url;
        this.error = undefined;
      })
      .catch((e) => {
        this.error = e.message;
      });
    return promise;
  }

  generateOffsetsJsonURL () {
    if (!this.objectStorage) {
      this.offsetsJsonURL = undefined;
      return Promise.resolve();
    }
    const promise = this.objectStorage.generateFileUrl(this.offsetsJson);
    promise
      .then((url) => {
        this.offsetsJsonURL = url;
      })
      .catch(() => {});
    return promise;
  }

  reportReadAccess = () => {
    if (!this._reported && this.objectStorage) {
      this._reported = true;
      auditStorageAccessManager.reportReadAccess({
        storageId: this.objectStorage.id,
        path: this.omeTiff,
        reportStorageType: 'S3'
      }, {
        storageId: this.objectStorage.id,
        path: this.offsetsJson,
        reportStorageType: 'S3'
      });
    }
  };

  generateURLs = async () => {
    this.clearTimeouts();
    this._reported = false;
    await Promise.all([
      this.generateOMETiffURL(),
      this.generateOffsetsJsonURL()
    ]);
    this.listeners
      .filter(aListener => typeof aListener === 'function')
      .forEach(aListener => aListener(this));
    this.urlsRegenerationTimer = setTimeout(
      () => this.generateURLs(),
      REGENERATE_URLS_TIMEOUT
    );
  };
}

export default HCSURLsManager;
