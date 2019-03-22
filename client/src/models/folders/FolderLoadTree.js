/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {SERVER} from '../../config';
import Remote from '../basic/Remote';
import MetadataFolder from '../metadata/MetadataFolder';
import defer from '../../utils/defer';
import {action} from 'mobx';
import {authorization} from '../basic/Authorization';
import mapFolderChildrenMetadata from './mapFolderChildrenMetadata';

class FolderLoadTree extends Remote {

  constructor () {
    super();
    this.url = '/folder/loadTree';
  }

  async fetch () {
    this._loadRequired = false;
    if (!this._fetchPromise) {
      const originalPromise = new Promise(async (resolve) => {
        const {prefix, fetchOptions} = this.constructor;
        try {
          await defer();
          let headers = fetchOptions.headers;
          if (!headers) {
            headers = {};
          }
          fetchOptions.headers = headers;
          const response = await fetch(`${prefix}${this.url}`, fetchOptions);
          const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
          resolve({data, error: null});
        } catch (e) {
          resolve({data: null, error: e.toString()});
        }
      });
      const metadataPromise = new Promise(async (resolve) => {
        const request = new MetadataFolder();
        await request.fetch();
        resolve(request);
      });
      this._fetchPromise = new Promise(async (resolve) => {
        this._pending = true;
        const [originalResult, metadataResult] =
          await Promise.all([originalPromise, metadataPromise]);
        if (originalResult.error) {
          this.failed = true;
          this.error = originalResult.error;
        } else {
          this.update(originalResult.data, metadataResult);
        }
        this._pending = false;
        this._fetchPromise = null;
        resolve();
      });
    }
    return this._fetchPromise;
  }

  @action
  update (value, metadataRequest) {
    this._response = value;
    if (value.status && value.status === 401) {
      this.error = value.message;
      this.failed = true;
      if (authorization.isAuthorized()) {
        authorization.setAuthorized(false);
        console.log('Changing authorization to: ' + authorization.isAuthorized());
        window.location = `${SERVER}/saml/logout`;
      }
    } else if (value.status && value.status === 'OK') {
      this._value = this.postprocess(value);
      mapFolderChildrenMetadata(metadataRequest, this._value);
      this._loaded = true;
      this.error = undefined;
      this.failed = false;
      if (!authorization.isAuthorized()) {
        authorization.setAuthorized(true);
        console.log('Changing authorization to: ' + authorization.isAuthorized());
      }
    } else {
      this.error = value.message;
      this.failed = true;
      this._loaded = false;
    }
  }
}

export default new FolderLoadTree();
