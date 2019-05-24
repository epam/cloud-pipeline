/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import AWS from 'aws-sdk/index';
import DataStorageTempCredentials from '../dataStorage/DataStorageTempCredentials';
import Credentials from './Credentials';

const asyncForEach = async (array, callback) => {
  for (let index = 0; index < array.length; index++) {
    await callback(array[index], index, array);
  }
};

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
    if (this._storage) {
      return this.updateCredentials();
    }
  }

  get prefix () {
    return this._prefix;
  }

  set prefix (value) {
    if (value.endsWith('/')) {
      this._prefix = value;
    } else {
      this._prefix = value ? `${value}/` : '';
    }
  }

  updateCredentials = async () => {
    let success = true;
    const request = new DataStorageTempCredentials(this._storage.id);
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

  listObjects = () => {
    let params = {
      Bucket: this._storage.path,
      Prefix: this._prefix
    };
    if (this._storage.delimiter) {
      params.Delimiter = this._storage.delimiter;
    }

    return this._s3.listObjects(params).promise();
  };

  getStorageObject = async (key, startBytes, endBytes) => {
    const params = {
      Bucket: this._storage.path,
      Key: key,
      Range: `bytes=${startBytes}-${endBytes}`
    };
    return this._s3.getObject(params).promise();
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

  uploadStorageObject = async (name, body, uploadProgress) => {
    const params = {
      Body: body,
      Bucket: this._storage.path,
      Key: this.prefix + name
    };
    const upload = this._s3.upload(params);
    upload.on('httpUploadProgress', uploadProgress);
    return upload.promise();
  };

  putStorageObject = async (name, buffer) => {
    const params = {
      Body: buffer,
      Bucket: this._storage.path,
      Key: this._prefix + name
    };

    return this._s3.putObject(params).promise();
  };

  renameStorageObject = async (key, newName) => {
    let success = true;

    let params = {
      Bucket: this._storage.path,
      Prefix: key
    };

    if (key.endsWith(this._storage.delimiter)) {
      try {
        const data = await this._s3.listObjects(params).promise();
        await asyncForEach(data.Contents, async file => {
          params = {
            Bucket: this._storage.path,
            CopySource: this._storage.path + this._storage.delimiter + file.Key,
            Key: file.Key.replace(key, this._prefix + newName + this._storage.delimiter)
          };

          await this._s3.copyObject(params).promise();
          await this.deleteStorageObjects([file.Key]);
        });
        success = true;
      } catch (err) {
        success = false;
        return Promise.reject(new Error(err.message));
      }
    } else {
      params = {
        Bucket: this._storage.path,
        CopySource: this._storage.path + this._storage.delimiter + key,
        Key: this._prefix + newName
      };
      try {
        await this._s3.copyObject(params).promise();
        await this.deleteStorageObjects([key]);
      } catch (err) {
        success = false;
        return Promise.reject(new Error(err.message));
      }
    }
    return success;
  };

  deleteStorageObjects = async (keys) => {
    let deleteObjects = [];
    let success = true;

    try {
      await asyncForEach(keys, async key => {
        if (key.endsWith(this._storage.delimiter)) {
          let params = {
            Bucket: this._storage.path,
            Prefix: key
          };

          const data = await this._s3.listObjects(params).promise();
          data.Contents.forEach(content => {
            deleteObjects.push({
              Key: content.Key
            });
          });
        } else {
          deleteObjects.push({
            Key: key
          });
        }
      });

      const params = {
        Bucket: this._storage.path,
        Delete: {
          Objects: deleteObjects
        }
      };

      await this._s3.deleteObjects(params).promise();
    } catch (err) {
      success = false;
      return Promise.reject(new Error(err.message));
    }
    return success;
  };

}

export default new S3Storage();
