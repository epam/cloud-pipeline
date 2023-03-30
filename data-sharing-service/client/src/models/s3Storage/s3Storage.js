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
import Credentials from './Credentials';
import fetchTempCredentials from './fetch-temp-credentials';
import auditStorageAccessManager from '../../utils/audit-storage-access';

const FETCH_CREDENTIALS_MAX_ATTEMPTS = 12;

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
    if (this._storage) {
      return this.updateCredentials();
    }
  }

  get prefix () {
    return this._prefix || '';
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
    try {
      const updateCredentialsAttempt = (attempt = 0, error = undefined) => {
        if (attempt >= FETCH_CREDENTIALS_MAX_ATTEMPTS) {
          return Promise.reject(error || new Error('credentials API is not available'));
        }
        return new Promise((resolve, reject) => {
          fetchTempCredentials(
            this._storage.id,
            {
              read: true,
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
    const tagging = Object.entries(tags || {})
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
    auditStorageAccessManager.reportWriteAccess({
      fullPath: `s3://${this._storage.path}/${this.prefix + name}`
    });
    const upload = this._s3.uploadPart(params);
    upload.on('httpUploadProgress', uploadProgress);
    return upload;
  };
}

export {S3Storage};
export default new S3Storage();
