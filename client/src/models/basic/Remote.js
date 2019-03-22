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

class Remote {
  static defaultValue = {};
  static fetchOptions = {
    mode: 'cors',
    credentials: 'include'
  };
  static prefix = SERVER + API_PATH;
  static auto = false;
  static isJson = true;

  url;

  @observable failed = false;
  @observable error = undefined;

  constructor() {
    if (this.constructor.auto) {
      this.fetch();
    }
  }

  @observable _pending = true;
  @computed
  get pending() {
    this._fetchIfNeeded();
    return this._pending;
  }

  @observable _loaded = false;
  @computed
  get loaded() {
    this._fetchIfNeeded();
    return this._loaded;
  }

  @observable _value = this.constructor.defaultValue;
  @computed
  get value () {
    this._fetchIfNeeded();
    return this._value;
  }

  @observable _response = undefined;
  @computed
  get response () {
    this._fetchIfNeeded();
    return this._response;
  }

  _loadRequired = !this.constructor.auto;

  async _fetchIfNeeded () {
    if (this._loadRequired) {
      this._loadRequired = false;
      await this.fetch();
    }
  }

  async fetchIfNeededOrWait () {
    if (this._loadRequired) {
      await this._fetchIfNeeded();
    } else if (this._fetchPromise) {
      await this._fetchPromise;
    }
  }

  invalidateCache () {
    this._loadRequired = true;
  }

  _fetchPromise = null;

  async fetch () {
    this._loadRequired = false;
    if (!this._fetchPromise) {
      this._fetchPromise = new Promise(async (resolve) => {
        this._pending = true;
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
          this.update(data);
        } catch (e) {
          this.failed = true;
          this.error = e.toString();
        }

        this._pending = false;
        this._fetchPromise = null;
        resolve();
      });
    }
    return this._fetchPromise;
  }

  async silentFetch () {
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
      this.update(data);
    } catch (e) {
      this.failed = true;
      this.error = e;
    }
  }

  postprocess(value) {
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
    } else {
      this.error = value.message;
      this.failed = true;
      this._loaded = false;
    }
  }
}

export default Remote;
