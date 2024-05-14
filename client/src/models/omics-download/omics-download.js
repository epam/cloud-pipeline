/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import {OmicsClient, GetReadSetMetadataCommand} from '@aws-sdk/client-omics';
import fetchTempCredentials from '../s3-upload/fetch-temp-credentials';
import Credentials from '../s3-upload/credentials';

const FETCH_CREDENTIALS_MAX_ATTEMPTS = 12;

export const ItemType = {
  FILE: 'File',
  FOLDER: 'Folder'
};

class OmicsStorage {
  _omics;
  _credentials;
  regionName;
  _storage;
  readSetId;
  sequenceStoreId;

  get omics () {
    return this._omics;
  }

  constructor (config) {
    this.regionName = config.region;
    this._storage = config.storage;
    this.readSetId = config.readSetId;
    this.sequenceStoreId = config.sequenceStoreId;
  }

  async createClient () {
    await this.setCredentials();
    if (this._credentials) {
      return this.setOmicsClient();
    }
  }

  async getCredentials () {
    try {
      const updateCredentialsAttempt = (attempt = 0, error = undefined) => {
        if (attempt >= FETCH_CREDENTIALS_MAX_ATTEMPTS) {
          return Promise.reject(error || new Error('credentials API is not available'));
        }
        return new Promise((resolve, reject) => {
          fetchTempCredentials(
            this._storage.id,
            {
              read: this._storage.read === undefined ? true : this._storage.read,
              write: this._storage.write === undefined ? true : this._storage.write
            })
            .then(resolve)
            .catch((e) => {
              updateCredentialsAttempt(attempt + 1, e)
                .then(resolve)
                .catch(reject);
            });
        });
      };
      const {error, payload} = await updateCredentialsAttempt();
      if (error) {
        return Promise.reject(error);
      }
      return Promise.resolve(payload);
    } catch (err) {
      return Promise.reject(err);
    }
  }

  async setCredentials () {
    const credentials = await this.getCredentials()
      .then(cred => cred)
      .catch(err => {
        this.downloadError = err.message;
        return undefined;
      });
    if (!this._credentials) {
      this._credentials = new Credentials(
        credentials.keyID,
        credentials.accessKey,
        credentials.token,
        credentials.expiration,
        this.getCredentials
      );
    } else if (this._credentials.needsRefresh) {
      this._credentials.update(
        credentials.keyID,
        credentials.accessKey,
        credentials.token,
        credentials.expiration
      );
    }
  }

  setOmicsClient () {
    if (this._credentials && this.regionName) {
      this._omics = new OmicsClient({
        omics: '2022-11-28',
        region: this.regionName,
        credentials: this._credentials
      });
      return true;
    }
    return false;
  }

  async getFilesMetadata (files) {
    if (!this.omics || !this.readSetId || !this.sequenceStoreId) return;
    try {
      const filesInfo = await this.getReadSetMetadata();
      if (filesInfo) {
        const metadata = [];
        const filesSources = Object.keys(filesInfo);
        for (const file of files) {
          const source = file.sourceName;
          if (!source) {
            for (const fileSource of filesSources) {
              metadata.push({
                path: file.path,
                itemPath: file.type === ItemType.FILE ? file.path : `${file.path}/${fileSource}`
              });
            }
          } else if (filesSources.includes(source)) {
            metadata.push({
              path: file.path,
              itemPath: file.type === ItemType.FILE ? file.path : `${file.path}/${source}`
            });
          }
        }
        return metadata;
      }
      return [];
    } catch (err) {
      return err;
    }
  }

  async getReadSetMetadata () {
    try {
      const input = {
        id: this.readSetId,
        sequenceStoreId: this.sequenceStoreId
      };
      const command = new GetReadSetMetadataCommand(input);
      const response = await this._omics.send(command);
      if (response) {
        const filesInfo = response.files;
        return filesInfo;
      }
      return false;
    } catch (err) {
      return err;
    }
  }
}

export default OmicsStorage;
