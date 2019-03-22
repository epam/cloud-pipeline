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

import Remote from '../basic/Remote';
import {computed} from 'mobx';
import defer from '../../utils/defer';
import GoogleApi from '../google/GoogleApi';

export default class FireCloudMethodSnapshotConfigurations extends Remote {
  googleApi;
  initialized = false;
  constructor (googleApi, namespace, method, snapshot) {
    super();
    this.googleApi = googleApi;
    this.url = `/firecloud/methods/${namespace}/${method}/${snapshot}/configurations`;
  }

  _initialize = async () => {
    if (!this.initialized) {
      this.initialized = true;
      await this.googleApi.initialize();
      this.googleApi.listenSignInStatusUpdate(this.onSignInStatusChanged);
    }
  };

  @computed
  get loaded () {
    if (this.isSignedIn) {
      this._fetchIfNeeded();
      return this._loaded;
    }
    return false;
  }

  @computed
  get isSignedIn () {
    if (this.googleApi) {
      return this.googleApi.isSignedIn;
    }
    return false;
  }

  onSignInStatusChanged = () => {
    this.fetch();
  };

  async fetch () {
    await this._initialize();
    let headers = this.constructor.fetchOptions.headers;
    if (!headers) {
      headers = {};
    }
    if (this.googleApi.userAuthEnabled) {
      const refreshToken = GoogleApi.getRefreshToken();
      if (refreshToken) {
        headers['Firecloud-Token'] = refreshToken;
      }
    }
    this.constructor.fetchOptions.headers = headers;
    if (this.isSignedIn) {
      await super.fetch();
      if (this.error && this.error.toLowerCase().indexOf('an error during google authorization') >= 0) {
        GoogleApi.setRefreshToken('');
      }
    } else {
      await defer();
      this._pending = true;
      await defer();
      this.update({payload: [], message: 'Unauthorized'});
      if (this.error && this.error.toLowerCase().indexOf('an error during google authorization') >= 0) {
        GoogleApi.setRefreshToken('');
      }
      this._value = [];
      this._pending = false;
    }
  }
}
