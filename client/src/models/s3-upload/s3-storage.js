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

import AWS from 'aws-sdk/index';
import DataStorageTempCredentials from '../dataStorage/DataStorageTempCredentials';
import Credentials from './credentials';

class S3Storage {
  _s3;
  _storage;
  _prefix;
  _credentials;

  constructor (storage) {
    if (storage) {
      this.storage = storage;
    }
  };

  get storage () {
    return this._storage;
  };

  set storage (value) {
    this._storage = value;
  }

  get prefix () {
    return this._prefix;
  }

  set prefix (value) {
    if (value && value.endsWith('/')) {
      this._prefix = value;
    } else {
      this._prefix = value ? `${value}/` : '';
    }
  }

  updateCredentials = async () => {
    let success = true;
    const request = new DataStorageTempCredentials();
    let tempCredentials = {};
    try {
      await request.send([{
        id: this._storage.id,
        read: true,
        readVersion: false,
        write: true,
        writeVersion: false
      }]);
      if (request.error) {
        tempCredentials = {};
        return Promise.reject(new Error(request.error));
      } else {
        tempCredentials = request.value;
      }
      if (this._credentials) {
        this._credentials.accessKeyId = tempCredentials.keyID;
        this._credentials.secretAccessKey = tempCredentials.accessKey;
        this._credentials.sessionToken = tempCredentials.token;

        AWS.config.update({
          region: tempCredentials.region,
          credentials: this._credentials
        });
      } else {
        this._credentials = new Credentials(
          tempCredentials.keyID,
          tempCredentials.accessKey,
          tempCredentials.token,
          this.updateCredentials);

        AWS.config.update({
          region: tempCredentials.region,
          credentials: this._credentials
        });
      }
    } catch (err) {
      success = false;
      return Promise.reject(new Error(err.message));
    }
    this._s3 = new AWS.S3();
    return success;
  };

  completeMultipartUploadStorageObject = async (name, parts, uploadId) => {
    const params = {
      Bucket: this._storage.path,
      Key: this.prefix + name,
      MultipartUpload: {
        Parts: parts
      },
      UploadId: uploadId
    };
    const upload = this._s3.completeMultipartUpload(params);
    return upload.promise();
  };

  abortMultipartUploadStorageObject = (name, uploadId) => {
    const params = {
      Bucket: this._storage.path,
      Key: this.prefix + name,
      UploadId: uploadId
    };
    const upload = this._s3.abortMultipartUpload(params);
    return upload.promise();
  };

  createMultipartUpload = async (name) => {
    let params = {
      Bucket: this._storage.path,
      Key: this.prefix + name
    };
    return this._s3.createMultipartUpload(params).promise();
  };

  multipartUploadStorageObject = (name, body, partNumber, uploadId, uploadProgress) => {
    const params = {
      Body: body,
      Bucket: this._storage.path,
      Key: this.prefix + name,
      PartNumber: partNumber,
      UploadId: uploadId
    };
    const upload = this._s3.uploadPart(params);
    upload.on('httpUploadProgress', uploadProgress);
    return upload;
  };
}

export default S3Storage;
