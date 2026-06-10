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
  S3Client,
  CreateMultipartUploadCommand,
  UploadPartCommand,
  CompleteMultipartUploadCommand,
  AbortMultipartUploadCommand,
  GetObjectCommand,
} from '@aws-sdk/client-s3';
import {getSignedUrl} from '@aws-sdk/s3-request-presigner';
import Credentials from './credentials';
import DataStorageTagsUpdate from '../dataStorage/tags/DataStorageTagsUpdate';
import fetchTempCredentials from './fetch-temp-credentials';
import displaySize from '../../utils/displaySize';
import auditStorageAccessManager from '../../utils/audit-storage-access';
import preferences from '../preferences/PreferencesLoad';

const KB = 1024;
const MB = 1024 * KB;
const GB = 1024 * MB;
const TB = 1024 * GB;
const S3_MAX_FILE_SIZE_TB = 5;

const UPLOAD_CONCURRENCY_LIMIT = 5;
const S3_MIN_UPLOAD_CHUNK_SIZE_MB = 5;

const MAX_FILE_SIZE = S3_MAX_FILE_SIZE_TB * TB;
const MAX_FILE_SIZE_DESCRIPTION = displaySize(MAX_FILE_SIZE, false);

const FETCH_CREDENTIALS_MAX_ATTEMPTS = 12;
const PRESIGNED_URL_EXPIRES_IN = 3600;

export {MAX_FILE_SIZE_DESCRIPTION};

const SECOND = 1000;
const MINUTE = 60 * SECOND;

function isExpiredTokenError(error) {
  const code = error?.name || error?.Code || error?.code || '';
  return /^ExpiredToken$/i.test(code);
}

function createS3Client(region, credentials) {
  return new S3Client({
    region,
    credentials,
    // Avoid aws-chunked encoding on Blob bodies (WHEN_SUPPORTED auto-adds CRC32 streaming checksum).
    requestChecksumCalculation: async () => 'WHEN_REQUIRED',
    requestHandler: {
      requestTimeout: 10 * MINUTE,
    },
  });
}

class S3Storage {
  _s3;
  _region;
  _storage;
  /**
   * @private {TempCredentialsStorageObject}
   */
  _storageObject;
  _prefix;
  _credentials;
  _signedUrlCache = new Map();
  _pendingSignedUrls = new Map();

  /**
   * @param {Object} [storage]
   * @param {TempCredentialsStorageObject} [storageObject]
   */
  constructor(storage, storageObject) {
    if (storage) {
      this.storage = storage;
    }
    this._storageObject = storageObject;
  }

  get storage() {
    return this._storage;
  }

  set storage(value) {
    this._storage = value;
  }

  get prefix() {
    return this._prefix || '';
  }

  set prefix(value) {
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
              write: this._storage.write === undefined ? true : this._storage.write,
            },
            this._storageObject,
          )
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
          payload.expiration,
        );
      } else {
        this._credentials = new Credentials(
          payload.keyID,
          payload.accessKey,
          payload.token,
          payload.expiration,
          this.updateCredentials,
        );
      }
      this._region = payload.region || this._storage.region;
      this.clearSignedUrlCache();
      if (this._credentials && this._region) {
        this._s3 = createS3Client(this._region, this._credentials);
      }
    } catch (err) {
      success = false;
      return Promise.reject(new Error(err.message));
    }
    return success;
  };

  refreshCredentialsIfNeeded = async () => {
    if (this._credentials) {
      this._credentials.get();
    }
    if (!this._s3 || !this._credentials || this._credentials.needsRefresh()) {
      await this.updateCredentials();
    }
  };

  sendCommand = async (command, options) => {
    await this.refreshCredentialsIfNeeded();
    if (!this._s3) {
      throw new Error('s3 storage wrapper is not initialized');
    }
    try {
      return await this._s3.send(command, options);
    } catch (error) {
      if (isExpiredTokenError(error)) {
        await this.updateCredentials();
        return this._s3.send(command, options);
      }
      throw error;
    }
  };

  clearSignedUrlCache = () => {
    this._signedUrlCache.clear();
    this._pendingSignedUrls.clear();
  };

  objectKey = (file = '') => this.prefix + file;

  getSignedUrlAsync = async (file = '') => {
    await this.refreshCredentialsIfNeeded();
    if (!this._s3) {
      return undefined;
    }
    const key = this.objectKey(file);
    const cached = this._signedUrlCache.get(key);
    if (cached) {
      return cached;
    }
    const pending = this._pendingSignedUrls.get(key);
    if (pending) {
      return pending;
    }
    const promise = getSignedUrl(
      this._s3,
      new GetObjectCommand({
        Bucket: this._storage.path,
        Key: key,
      }),
      {expiresIn: PRESIGNED_URL_EXPIRES_IN},
    )
      .then((url) => {
        this._signedUrlCache.set(key, url);
        this._pendingSignedUrls.delete(key);
        return url;
      })
      .catch((error) => {
        this._pendingSignedUrls.delete(key);
        throw error;
      });
    this._pendingSignedUrls.set(key, promise);
    return promise;
  };

  /**
   * Sync cache lookup for callers that cannot await (e.g. tile viewer callbacks).
   * A blocking wait loop is not possible in the browser — the event loop must run for
   * the async presigner to complete. On cache miss, kicks off background presigning.
   */
  getSignedUrl = (file = '') => {
    this._credentials?.get();
    if (!this._credentials || this._credentials.needsRefresh() || !this._region) {
      return undefined;
    }
    const key = this.objectKey(file);
    const cached = this._signedUrlCache.get(key);
    if (cached) {
      return cached;
    }
    if (this._s3 && !this._pendingSignedUrls.has(key)) {
      void this.getSignedUrlAsync(file);
    }
    return undefined;
  };

  completeMultipartUploadStorageObject = async (name, parts, uploadId) => {
    const params = {
      Bucket: this._storage.path,
      Key: this.prefix + name,
      MultipartUpload: {
        Parts: parts,
      },
      UploadId: uploadId,
    };
    return this.sendCommand(new CompleteMultipartUploadCommand(params));
  };

  abortMultipartUploadStorageObject = async (name, uploadId) => {
    const params = {
      Bucket: this._storage.path,
      Key: this.prefix + name,
      UploadId: uploadId,
    };
    return this.sendCommand(new AbortMultipartUploadCommand(params));
  };

  createMultipartUpload = async (name, tags) => {
    const tagging = Object.entries(tags)
      .filter(([, value]) => !!value)
      .map(([key, value]) => `${key}=${encodeURIComponent(value)}`);
    const params = {
      ACL: 'bucket-owner-full-control',
      Bucket: this._storage.path,
      Key: this.prefix + name,
      Tagging: tagging.length > 0 ? tagging.join('&') : undefined,
    };
    return this.sendCommand(new CreateMultipartUploadCommand(params));
  };

  multipartUploadStorageObject = async (name, body, partNumber, uploadId, uploadProgress) => {
    const params = {
      Body: body,
      Bucket: this._storage.path,
      Key: this.prefix + name,
      PartNumber: partNumber,
      UploadId: uploadId,
    };
    const abortController = new AbortController();
    const total = body.size ?? body.byteLength ?? 0;
    const promise = this.sendCommand(new UploadPartCommand(params), {
      abortSignal: abortController.signal,
    }).then((data) => {
      if (uploadProgress && total > 0) {
        uploadProgress({loaded: total, total});
      }
      return data;
    });
    return {
      abort: () => abortController.abort(),
      promise,
    };
  };

  doUpload = async (file, options, callbacks) => {
    const {
      uploadID: currentUploadID,
      partNumber: currentPartNumber,
      multipartParts = [],
      owner,
      fileName = file.name,
    } = options;
    if (this.storage) {
      const path = [this.prefix, fileName].filter((o) => o.length).join('/');
      auditStorageAccessManager.reportWriteAccess({
        fullPath: `s3://${this.storage.path}/${path}`,
        storageId: this.storage.id,
      });
    }
    const {onPartError, onProgress, setAbort, setMultipartUploadParts} = callbacks;
    await preferences.fetchIfNeededOrWait();
    const chunkCountPreference = preferences.uiUploadChunkCount;
    const chunkSizePreference =
      Math.max(
        S3_MIN_UPLOAD_CHUNK_SIZE_MB,
        preferences.uiUploadChunkSizeMB || S3_MIN_UPLOAD_CHUNK_SIZE_MB,
      ) * MB;
    const chunkSize =
      Math.ceil(file.size / chunkSizePreference) > chunkCountPreference
        ? Math.ceil(file.size / chunkCountPreference)
        : chunkSizePreference;
    const upload = async (uploadID, part = 0) => {
      const chunks = [];
      let last = false;
      const startPosition = part * chunkSize;
      const updatePercent = () => {
        const loaded = startPosition + chunks.reduce((l, c) => l + c.loaded, 0);
        const percent = loaded / file.size;
        if (onProgress) {
          onProgress(percent);
        }
      };
      for (let c = 0; c < UPLOAD_CONCURRENCY_LIMIT; c++) {
        const partNumber = c + part;
        const start = partNumber * chunkSize;
        if (start > file.size) {
          last = true;
          break;
        }
        const end = Math.min((partNumber + 1) * chunkSize, file.size);
        chunks.push({
          body: file.slice(start, end),
          partNumber,
          total: end - start,
          loaded: 0,
        });
      }
      const next = last ? null : part + UPLOAD_CONCURRENCY_LIMIT;
      const promises = await Promise.all(
        chunks.map(async (chunk) => {
          const {abort, promise} = await this.multipartUploadStorageObject(
            fileName,
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
            },
          );
          const wrappedPromise = promise.then(
            (data) => ({
              partNumber: chunk.partNumber,
              payload: {
                ETag: data.ETag,
                PartNumber: chunk.partNumber + 1,
              },
            }),
            (error) => ({partNumber: chunk.partNumber, error: error.message}),
          );
          return {abort, promise: wrappedPromise};
        }),
      );
      const abort = promises
        .map((entry) => entry.abort)
        .filter(Boolean)
        .reduce(
          (a, c) => () => {
            c();
            return a();
          },
          () => this.abortMultipartUploadStorageObject(fileName, uploadID),
        );
      return {
        abort,
        next,
        promise: Promise.all(promises.map((p) => p.promise)),
      };
    };
    const fileTags = {CP_OWNER: owner};
    const startUpload = () => {
      return new Promise((resolve, reject) => {
        if (file.size > MAX_FILE_SIZE) {
          reject(new Error(`error: Maximum ${MAX_FILE_SIZE_DESCRIPTION} per file`));
        } else {
          this.createMultipartUpload(fileName, fileTags).then((data) => {
            resolve(data.UploadId);
          }, reject);
        }
      });
    };
    const updateDataStorageTags = () => {
      const request = new DataStorageTagsUpdate(this._storage.id, this.prefix + fileName);
      return new Promise((resolve) => {
        request
          .send(fileTags)
          .then(() => {
            if (request.error) {
              console.warn(
                `Error updating data storage item (${this.prefix + fileName}) tags:`,
                request.error,
              );
            }
            resolve();
          })
          .catch((e) => {
            console.warn(
              `Error updating data storage item (${this.prefix + fileName}) tags:`,
              e.message,
            );
            resolve();
          });
      });
    };
    const finishUpload = async (uploadID, parts) => {
      try {
        await this.completeMultipartUploadStorageObject(fileName, parts, uploadID);
        await updateDataStorageTags();
        return undefined;
      } catch (error) {
        if (onPartError) {
          onPartError(parts.length, error.message);
        }
        return error.message;
      }
    };
    const continueUpload = async (uploadID, part = 0) => {
      const {abort, next, promise} = await upload(uploadID, part);
      if (setAbort) {
        setAbort(abort);
      }
      const parts = await promise;
      const errorParts = parts.filter((part) => !!part.error);
      if (errorParts.length > 0) {
        const partNumber = Math.min(...errorParts.map((p) => p.partNumber));
        const errorPart = errorParts.find((p) => p.partNumber === partNumber);
        if (onPartError) {
          onPartError(partNumber, errorPart.error);
        }
        return errorPart.error;
      }
      multipartParts.push(...parts.map((part) => part.payload));
      if (setMultipartUploadParts) {
        setMultipartUploadParts(uploadID, multipartParts);
      }
      if (next !== null) {
        return continueUpload(uploadID, next);
      }
      return finishUpload(uploadID, multipartParts);
    };
    if (currentUploadID && currentPartNumber !== undefined && currentPartNumber !== null) {
      return continueUpload(currentUploadID, currentPartNumber);
    }
    const uploadID = await startUpload();
    if (setMultipartUploadParts) {
      setMultipartUploadParts(uploadID, []);
    }
    return continueUpload(uploadID);
  };
}

export default S3Storage;
