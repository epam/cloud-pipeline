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

import {SERVER, API_PATH} from '../../config';
import defer from '../../utils/defer';
import {observable, action, computed} from 'mobx';
import {authorization} from './Authorization';

class RemotePost {
  static fetchOptions = {
    mode: 'cors',
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=UTF-8;'
    }
  };
  static prefix = SERVER + API_PATH;
  static auto = false;
  static isJson = true;
  static noResponse = false;

  url;

  @observable _pending = false;
  @computed
  get pending () {
    return this._pending;
  }

  @observable _loaded = false;
  @computed
  get loaded () {
    return this._loaded;
  }

  @observable _response = undefined;
  @computed
  get response () {
    return this._response;
  }

  async fetch () {
    await this.send({});
  }

  _fetchIsExecuting = false;
  async send (body) {
    if (!this._postIsExecuting) {
      this._pending = true;
      this._postIsExecuting = true;
      const {prefix, fetchOptions} = this.constructor;
      try {
        await defer();
        let headers = fetchOptions.headers;
        if (!headers) {
          headers = {};
        }
        fetchOptions.headers = headers;
        let stringifiedBody;
        try {
          stringifiedBody = JSON.stringify(body);
        } catch (___) {}
        const response = await fetch(
          `${prefix}${this.url}`,
          {...fetchOptions, body: stringifiedBody}
        );
        if (!this.constructor.noResponse) {
          const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
          this.update(data);
        } else {
          this.update({status: 'OK', payload: {}});
        }
      } catch (e) {
        this.failed = true;
        this.error = e.toString();
      } finally {
        this._postIsExecuting = false;
      }

      this._pending = false;
      this._fetchIsExecuting = false;
    }
  }

  postprocess (value) {
    return value.payload;
  }

  @action
  update (value) {
    this._response = value;
    if (value.status && value.status === 401) {
      this.error = value.message;
      this.failed = true;
      if (authorization.isAuthorized()) {
        authorization.setAuthorized(false);
        console.log("Changing authorization to: " + authorization.isAuthorized());
        let url = `${SERVER}/saml/logout`;
        window.location = url;
      }
    } else if (value.status && value.status === 'OK') {
      this._value = this.postprocess(value);
      this._loaded = true;
      this.error = undefined;
      this.failed = false;
      if (!authorization.isAuthorized()) {
        authorization.setAuthorized(true);
        console.log("Changing authorization to: " + authorization.isAuthorized());
      }
    } else if (!this.constructor.isJson && value instanceof Blob) {
      this._loaded = true;
      this.error = undefined;
      this.failed = false;
      this._value = value;
    } else {
      this.error = value.message;
      this.failed = true;
      this._loaded = false;
    }
  }

  get value () {
    return this._value;
  }
}

export default RemotePost;
