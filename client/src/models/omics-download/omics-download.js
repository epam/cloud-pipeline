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

import {
  OmicsClient,
  GetReferenceMetadataCommand,
  GetReadSetMetadataCommand,
  GetReferenceCommand,
  GetReadSetCommand
} from '@aws-sdk/client-omics';
import {observable, action} from 'mobx';
import fetchTempCredentials from '../s3-upload/fetch-temp-credentials';
import Credentials from '../s3-upload/credentials';

const FETCH_CREDENTIALS_MAX_ATTEMPTS = 12;

const REFERENCE = 'REFERENCE';

const ServiceTypes = {
  omicsRef: 'AWS_OMICS_REF',
  omicsSeq: 'AWS_OMICS_SEQ'
};

class OmicsStorage {
  _omics;
  _credentials;
  regionName;
  _storage;
  readSetId;
  storeId;

  @observable downloadError = null;

  get omics () {
    return this._omics;
  }

  constructor (config) {
    this.regionName = config.region;
    this._storage = config.storage;
    this.readSetId = config.readSetId;
    this.storeId = config.storeId;
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

  @action
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

  @action
  async downloadFiles (storeType, source) {
    if (!this.omics || !this.readSetId || !this.storeId) return;
    try {
      let filesInfo;
      if (storeType === ServiceTypes.omicsSeq) {
        filesInfo = await this.getReadSetMetadata();
      } else if (storeType === ServiceTypes.omicsRef) {
        filesInfo = await this.getReferenceMetadata();
      }
      if (filesInfo) {
        const files = [];
        const filesArray = Object.entries(filesInfo.files);
        for (let i = 0; i < filesArray.length; i++) {
          const [fileSource, fileData] = filesArray[i];
          if (!source || fileSource === source) {
            const fileBlob = await this.getAllParts(storeType, fileSource, fileData);
            files.push({
              name: filesInfo.fileName,
              type: filesInfo.fileType,
              blob: fileBlob,
              fileSource: fileSource
            });
          }
        }
        return files;
      }
    } catch (err) {
      this.downloadError = err.message;
    }
  }

  @action
  async getReadSetMetadata () {
    try {
      const input = {
        id: this.readSetId,
        sequenceStoreId: this.storeId
      };
      const command = new GetReadSetMetadataCommand(input);
      const response = await this._omics.send(command);
      if (response) {
        const filesInfo = {
          files: response.files,
          fileType: response.fileType,
          fileName: response.name
        };
        return filesInfo;
      }
      return false;
    } catch (err) {
      this.downloadError = err.message;
    }
  }

  @action
  async getReferenceMetadata () {
    try {
      const input = {
        id: this.readSetId,
        referenceStoreId: this.storeId
      };
      const command = new GetReferenceMetadataCommand(input);
      const response = await this._omics.send(command);
      if (response) {
        const filesInfo = {
          files: response.files,
          fileType: REFERENCE,
          fileName: response.name
        };
        return filesInfo;
      }
      return false;
    } catch (err) {
      this.downloadError = err.message;
    }
  }

  async getAllParts (storeType, source, info) {
    const readableStreams = [];
    for (let i = 1; i <= info.totalParts; i++) {
      let readableStream;
      if (storeType === ServiceTypes.omicsSeq) {
        readableStream = await this.getReadSet(source, i);
      } else if (storeType === ServiceTypes.omicsRef) {
        readableStream = await this.getReference(source, i);
      }
      readableStreams.push(readableStream);
    }
    const blobs = [];
    for (const readableStream of readableStreams) {
      const chunks = [];
      const reader = readableStream.getReader();
      const readChunk = async () => {
        const {value, done} = await reader.read();
        if (!done) {
          chunks.push(value);
          await readChunk();
        }
      };
      await readChunk();
      blobs.push(new Blob(chunks));
    }
    return new Blob(blobs, {type: 'application/octet-stream'});
  }

  @action
  async getReference (fileName, partNumber) {
    try {
      const input = {
        id: this.readSetId,
        referenceStoreId: this.storeId,
        file: fileName.toUpperCase(),
        partNumber: Number(partNumber)
      };
      const command = new GetReferenceCommand(input);
      const response = await this._omics.send(command);
      return response.payload;
    } catch (err) {
      this.downloadError = err.message;
    }
  }

  @action
  async getReadSet (fileName, partNumber) {
    try {
      const input = {
        id: this.readSetId,
        sequenceStoreId: this.storeId,
        file: fileName.toUpperCase(),
        partNumber: Number(partNumber)
      };
      const command = new GetReadSetCommand(input);
      const response = await this._omics.send(command);
      return response.payload;
    } catch (err) {
      this.downloadError = err.message;
    }
  }
}

export default OmicsStorage;
