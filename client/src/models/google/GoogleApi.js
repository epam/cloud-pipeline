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

import {observable, computed} from 'mobx';
import defer from '../../utils/defer';
import GenerateRefreshToken from './GenerateRefreshToken';

const REFRESH_TOKEN_KEY = 'refreshToken';

export default class GoogleApi {

  @observable _loaded = false;
  @computed
  get loaded () {
    this.initialize();
    return this._loaded;
  }

  @observable _error = false;
  @computed
  get error () {
    return this._error;
  }

  @observable _pending = false;
  @computed
  get pending () {
    this.initialize();
    return this._pending;
  }

  @observable _isSignedIn = false;
  @computed
  get isSignedIn () {
    this.initialize();
    return !this.userAuthEnabled || this._isSignedIn;
  }

  @observable _userAuthEnabled = false;
  @computed
  get userAuthEnabled () {
    this.initialize();
    return this._userAuthEnabled;
  }

  constructor (preferences) {
    this.gapi = window.gapi;
    this.preferences = preferences;
  }

  _initializePromise = null;

  initialize = async (forceSignIn = false) => {
    if (!this.gapi.client) {
      if (!this._initializePromise) {
        this._initializePromise = new Promise(async (resolve) => {
          await defer();
          await this.preferences.fetchIfNeededOrWait();
          const userAuthEnabledValue = this.preferences.getPreferenceValue('firecloud.enable.user.auth');
          this._userAuthEnabled = `${userAuthEnabledValue}`.toLowerCase() === 'true';
          if (!this._userAuthEnabled) {
            this._pending = false;
            this._loaded = true;
            this._error = null;
            resolve();
            return;
          }
          let scopes = [];
          let clientId = '';
          try {
            scopes = JSON.parse(this.preferences.getPreferenceValue('firecloud.api.scopes'));
          } catch (__) {}
          try {
            clientId = this.preferences.getPreferenceValue('google.client.id');
          } catch (__) {}
          this.scopes = scopes.join(' ');
          const onLoad = () => {
            this.gapi.client.init({
              clientId: clientId,
              scope: this.scopes
            }).then(() => {
              this.listenSignInStatusUpdate(this.updateSigninStatus);
              this.updateSigninStatus(this.gapi.auth2.getAuthInstance().isSignedIn.get());
              this._pending = false;
              this._loaded = true;
              this._error = null;
              resolve();
              if (forceSignIn) {
                return this.signIn();
              }
            })
              .catch((e) => {
                this._pending = false;
                this._loaded = false;
                this._error = e.details || e.error;
                console.error(this._error, e);
                resolve();
              });
          };
          const onError = () => {
            this._pending = false;
            resolve();
          };
          this._pending = true;
          this.gapi.load('client:auth2', {
            callback: onLoad,
            onerror: onError
          });
        });
      }
      return this._initializePromise;
    }
  };

  listenSignInStatusUpdate = (listener) => {
    if (!this.userAuthEnabled) {
      return;
    }
    if (listener) {
      this.gapi.auth2.getAuthInstance().isSignedIn.listen(listener);
    }
  };

  updateSigninStatus = async (isSignedIn) => {
    this._isSignedIn = isSignedIn;
  };

  signIn = async () => {
    await this.initialize();
    if (!this.userAuthEnabled) {
      localStorage.setItem(REFRESH_TOKEN_KEY, '');
      return;
    }
    if (!this.isSignedIn) {
      const {code} = await this.gapi.auth2.getAuthInstance({scopes: this.scopes}).grantOfflineAccess();
      if (code) {
        const request = new GenerateRefreshToken(code);
        await request.fetch();
        if (request.error) {
          localStorage.setItem(REFRESH_TOKEN_KEY, '');
        } else {
          const refreshToken = request.value.value;
          localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }
      }
    }
  };

  static getRefreshToken = () => {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  };

  static setRefreshToken = (token) => {
    return localStorage.setItem(REFRESH_TOKEN_KEY, token);
  };

  @computed
  get authenticatedGoogleUser () {
    if (!this.userAuthEnabled) {
      return null;
    }
    if (this.isSignedIn) {
      return this.gapi.auth2.getAuthInstance().currentUser.get().getBasicProfile().getName();
    }
    return null;
  }

  @computed
  get authenticatedGoogleUserAvatarUrl () {
    if (!this.userAuthEnabled) {
      return null;
    }
    if (this.isSignedIn) {
      return this.gapi.auth2.getAuthInstance().currentUser.get().getBasicProfile().getImageUrl();
    }
    return null;
  }

  signOut = async () => {
    await this.initialize();
    if (!this.userAuthEnabled) {
      localStorage.setItem(REFRESH_TOKEN_KEY, '');
      return;
    }
    if (this.isSignedIn) {
      this.gapi.auth2.getAuthInstance().signOut();
      localStorage.setItem(REFRESH_TOKEN_KEY, '');
    }
  };
}
