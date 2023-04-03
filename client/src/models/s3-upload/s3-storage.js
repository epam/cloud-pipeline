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
import Credentials from './credentials';
import DataStorageTagsUpdate from '../dataStorage/tags/DataStorageTagsUpdate';
import fetchTempCredentials from './fetch-temp-credentials';
import displaySize from '../../utils/displaySize';
import auditStorageAccessManager from '../../utils/audit-storage-access';

const KB = 1024;
const MB = 1024 * KB;
const GB = 1024 * MB;
const TB = 1024 * GB;
const S3_MAX_FILE_SIZE_TB = 5;

const UPLOAD_CONCURRENCY_LIMIT = 9;
const S3_MIN_UPLOAD_CHUNK_SIZE = 5 * MB;
const S3_MAX_UPLOAD_CHUNKS_COUNT = 10000;

const MAX_FILE_SIZE = S3_MAX_FILE_SIZE_TB * TB;
const MAX_FILE_SIZE_DESCRIPTION = displaySize(MAX_FILE_SIZE, false);

const FETCH_CREDENTIALS_MAX_ATTEMPTS = 12;

export {MAX_FILE_SIZE_DESCRIPTION};

// https://github.com/aws/aws-sdk-js/issues/1895#issuecomment-518466151
AWS.util.update(AWS.S3.prototype, {
  reqRegionForNetworkingError (resp, done) {
    if (AWS.util.isBrowser() && resp.error) {
      if (/^ExpiredToken$/i.test(resp.error.code)) {
        // we got 'expired token' error; we don't need to stop uploading process by setting
        // error (via "done(error)")
        done();
      } else if (/^CredentialsError$/i.test(resp.error.code)) {
        const details = resp.error.originalError
          ? ` (${resp.error.originalError.message})`
          : '';
        done(
          `Could not load credentials${details}`
        );
      } else {
        done(resp.error.message);
      }
    } else {
      done();
    }
  }
});
// ====================================================================

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
    return this._prefix || '';
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
        return Promise.reject(new Error(error));
      }
      if (this._credentials) {
        this._credentials.update(
          payload.keyID,
          payload.accessKey,
          payload.token,
          payload.expiration
        );
      } else {
        this._credentials = new Credentials(
          payload.keyID,
          payload.accessKey,
          payload.token,
          payload.expiration,
          this.updateCredentials
        );
      }
      if (this._credentials) {
        AWS.config.update({
          region: payload.region || this._storage.region,
          credentials: this._credentials
        });
      }
    } catch (err) {
      success = false;
      return Promise.reject(new Error(err.message));
    }
    this._s3 = new AWS.S3({signatureVersion: 'v4'});
    return success;
  };

  refreshCredentialsIfNeeded = async () => {
    this._credentials.get();
    if (this._credentials.needsRefresh()) {
      await this.updateCredentials();
    }
  }

  getSignedUrl = (file = '') => {
    const params = {
      Bucket: this._storage.path,
      Key: this.prefix + file
    };
    this._credentials.get();
    if (this._credentials.needsRefresh()) {
      return undefined;
    }
    return this._s3.getSignedUrl('getObject', params);
  };

  completeMultipartUploadStorageObject = (name, parts, uploadId) => {
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

  createMultipartUpload = (name, tags) => {
    const tagging = Object.entries(tags)
      .filter(([, value]) => !!value)
      .map(([key, value]) => `${key}=${encodeURIComponent(value)}`);
    let params = {
      ACL: 'bucket-owner-full-control',
      Bucket: this._storage.path,
      Key: this.prefix + name,
      Tagging: tagging.length > 0 ? tagging.join('&') : undefined
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

  doUpload = (file, options, callbacks) => {
    if (this.storage) {
      const path = [this.prefix, file.name].filter((o) => o.length).join('/');
      auditStorageAccessManager.reportWriteAccess({fullPath: `s3://${this.storage.path}/${path}`});
    }
    const {
      uploadID: currentUploadID,
      partNumber: currentPartNumber,
      multipartParts = [],
      owner
    } = options;
    const {
      onPartError,
      onProgress,
      setAbort,
      setMultipartUploadParts
    } = callbacks;
    const chunkSize =
      Math.ceil(file.size / S3_MIN_UPLOAD_CHUNK_SIZE) > S3_MAX_UPLOAD_CHUNKS_COUNT
        ? Math.ceil(file.size / S3_MAX_UPLOAD_CHUNKS_COUNT)
        : S3_MIN_UPLOAD_CHUNK_SIZE;
    const upload = (uploadID, part = 0) => {
      const chunks = [];
      let last = false;
      const startPosition = part * chunkSize;
      const updatePercent = () => {
        const loaded = startPosition + chunks.reduce((l, c) => l + c.loaded, 0);
        const percent = loaded / file.size;
        onProgress && onProgress(percent);
      };
      for (let c = 0; c < UPLOAD_CONCURRENCY_LIMIT; c++) {
        const partNumber = c + part;
        const start = partNumber * chunkSize;
        if (start > file.size) {
          last = true;
          break;
        }
        const end = Math.min((partNumber + 1) * chunkSize, file.size);
        chunks.push(({
          body: file.slice(start, end),
          partNumber,
          total: end - start,
          loaded: 0
        }));
      }
      const next = last ? null : part + UPLOAD_CONCURRENCY_LIMIT;
      const promises = chunks.map(chunk => {
        const uploadStorageObject = this.multipartUploadStorageObject(
          file.name,
          chunk.body,
          chunk.partNumber + 1,
          uploadID,
          (e) => {
            const {loaded, total} = e;
            if (total > 0 && loaded > 0) {
              chunk.total = total;
              chunk.loaded = loaded;
            }
            updatePercent();
          }
        );
        const abort = uploadStorageObject.abort.bind(uploadStorageObject);
        const promise = new Promise((resolve) => {
          uploadStorageObject
            .promise()
            .then((data) => {
              resolve({
                partNumber: chunk.partNumber,
                payload: {
                  ETag: data.ETag,
                  PartNumber: chunk.partNumber + 1
                }
              });
            }, error => {
              resolve({partNumber: chunk.partNumber, error: error.message});
            });
        });
        return {abort, promise};
      });
      const abort = promises
        .map(promise => promise.abort)
        .filter(Boolean)
        .reduce(
          (a, c) => () => { c(); return a(); },
          () => this.abortMultipartUploadStorageObject(file.name, uploadID)
        );
      return {
        abort,
        next,
        promise: Promise.all(promises.map(p => p.promise))
      };
    };
    const fileTags = {CP_OWNER: owner};
    const startUpload = () => {
      return new Promise((resolve, reject) => {
        if (file.size > MAX_FILE_SIZE) {
          reject(new Error(`error: Maximum ${MAX_FILE_SIZE_DESCRIPTION} per file`));
        } else {
          this.createMultipartUpload(file.name, fileTags)
            .then((data) => {
              resolve(data.UploadId);
            }, reject);
        }
      });
    };
    const updateDataStorageTags = () => {
      const request = new DataStorageTagsUpdate(
        this._storage.id,
        this.prefix + file.name
      );
      return new Promise((resolve) => {
        request
          .send(fileTags)
          .then(() => {
            if (request.error) {
              console.warn(
                `Error updating data storage item (${this.prefix + file.name}) tags:`,
                request.error
              );
            }
            resolve();
          })
          .catch((e) => {
            console.warn(
              `Error updating data storage item (${this.prefix + file.name}) tags:`,
              e.message
            );
            resolve();
          });
      });
    };
    const finishUpload = (uploadID, parts, done) => {
      this.completeMultipartUploadStorageObject(
        file.name,
        parts,
        uploadID
      )
        .then(
          () => {
            updateDataStorageTags()
              .then(() => done());
          },
          (error) => {
            onPartError && onPartError(parts.length, error.message);
            done(error.message);
          }
        );
    };
    const continueUpload = (uploadID, part = 0) => {
      const {
        abort,
        next,
        promise
      } = upload(uploadID, part);
      setAbort && setAbort(abort);
      return new Promise((resolve) => {
        promise
          .then((parts) => {
            const errorParts = parts.filter(part => !!part.error);
            if (errorParts.length > 0) {
              const partNumber = Math.min(...errorParts.map(p => p.partNumber));
              const errorPart = errorParts.find(p => p.partNumber === partNumber);
              onPartError && onPartError(partNumber, errorPart.error);
              resolve(errorPart.error);
            } else {
              multipartParts.push(...parts.map(part => part.payload));
              setMultipartUploadParts && setMultipartUploadParts(uploadID, multipartParts);
              if (next !== null) {
                continueUpload(uploadID, next)
                  .then(resolve);
              } else {
                finishUpload(uploadID, multipartParts, resolve);
              }
            }
          });
      });
    };
    if (currentUploadID && currentPartNumber !== undefined && currentPartNumber !== null) {
      return continueUpload(currentUploadID, currentPartNumber);
    } else {
      return new Promise((resolve, reject) => {
        startUpload()
          .then((uploadID) => {
            setMultipartUploadParts && setMultipartUploadParts(uploadID, []);
            continueUpload(uploadID)
              .then(resolve);
          })
          .catch(reject);
      });
    }
  };
}

export default S3Storage;
