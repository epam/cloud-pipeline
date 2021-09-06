/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import {computed, observable} from 'mobx';
import Remote from '../basic/Remote';
import {SERVER} from '../../config';

class ImpersonateUser extends Remote {
  constructor (userName) {
    super();
    if (userName) {
      this.url = `/user/impersonation/start?username=${userName}`;
    } else {
      this.url = '/user/impersonation/stop';
    }
  }

  getData (response) {
    if (response.ok) {
      return Promise.resolve({status: 'OK'});
    }
    return super.getData(response);
  }
}

class GetImpersonatedUser extends Remote {
  constructor () {
    super();
    this.url = '/user/impersonation';
  }
}

class Impersonation {
  @observable impersonatedUser;
  @observable originalUser;

  constructor () {
    this.impersonatedInfo = new GetImpersonatedUser();
    this.impersonatedInfo
      .fetch()
      .then(() => {
        if (this.impersonatedInfo.loaded) {
          const {
            original,
            impersonated
          } = this.impersonatedInfo.value || {};
          this.impersonatedUser = impersonated;
          this.originalUser = original;
        }
      })
      .catch(() => {});
  }

  @computed
  get isImpersonated () {
    return !!this.impersonatedUser;
  }

  @computed
  get impersonatedUserName () {
    return this.impersonatedUser
      ? this.impersonatedUser.userName
      : undefined;
  }

  impersonate (userName) {
    return new Promise((resolve) => {
      const request = new ImpersonateUser(userName);
      request
        .fetch()
        .then(() => {
          if (request.error) {
            resolve(request.error);
          } else {
            window.location = SERVER;
            resolve();
          }
        })
        .catch(() => {
          window.location = SERVER;
          resolve();
        });
    });
  }

  stopImpersonation () {
    return this.impersonate();
  }
}

const impersonation = new Impersonation();
export default impersonation;
