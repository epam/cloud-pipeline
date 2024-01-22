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

import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import roleModel from './roleModel';
import MetadataLoad from '../models/metadata/MetadataLoad';
import {CP_CAP_LIMIT_MOUNTS} from '../components/pipelines/launch/form/utilities/parameters';
import {correctLimitMountsParameterValue} from './limit-mounts/get-limit-mounts-storages';

const FILTER_NON_SENSITIVE_STORAGES = false;

class CurrentUserAttributes {
  @observable userInfo;
  @observable attributes = {};
  @observable loaded = false;
  @observable request;
  constructor (authenticatedUserInfo, dataStorageAvailable) {
    this.userInfo = authenticatedUserInfo;
    this.dataStorages = dataStorageAvailable;
    if (authenticatedUserInfo) {
      this.userInfo
        .fetchIfNeededOrWait()
        .then(() => {
          this.refresh(true);
        });
    }
  }

  @computed
  get user () {
    if (this.userInfo && this.userInfo.loaded) {
      return this.userInfo.value;
    }
    return undefined;
  }

  refresh (force = false) {
    if (this.user) {
      if (!this.request) {
        this.request = new MetadataLoad(this.user.id, 'PIPELINE_USER');
      }
      if (!this.refreshPromise || force) {
        this.refreshPromise = new Promise((resolve) => {
          this.dataStorages
            .fetchIfNeededOrWait()
            .then(() => this.request.fetch())
            .then(() => {
              if (this.request.error) {
                throw new Error(this.request.error);
              } else if (this.request.loaded) {
                const [info] = this.request.value || [];
                const {
                  data = {}
                } = info || {};
                this.attributes = data;
                this.loaded = true;
              }
            })
            .catch(e => {
              console.warn(`Error fetching user attributes: ${e.message}`);
            })
            .then(() => resolve());
        });
      }
      return this.refreshPromise;
    }
    return Promise.resolve();
  }

  hasAttribute (name) {
    return (this.loaded && this.attributes && this.attributes[name]);
  }

  getAttributeValue (name, allowSensitive = true) {
    if (this.hasAttribute(name)) {
      const value = this.attributes[name].value;
      if (
        FILTER_NON_SENSITIVE_STORAGES &&
        value &&
        !/^none$/i.test(value) &&
        !allowSensitive &&
        this.dataStorages &&
        this.dataStorages.loaded
      ) {
        const dataStorages = this.dataStorages.value || [];
        return correctLimitMountsParameterValue(
          value || '',
          dataStorages,
          {allowSensitive, keepUnmappedIdentifiers: true}
        );
      }
      return value;
    }
    return undefined;
  }

  extendLaunchParameters (parameters, allowSensitive = true) {
    if (
      this.hasAttribute(CP_CAP_LIMIT_MOUNTS) && (
        !parameters ||
        !parameters[CP_CAP_LIMIT_MOUNTS] ||
        !parameters[CP_CAP_LIMIT_MOUNTS].value
      )
    ) {
      return {
        ...(parameters || {}),
        [CP_CAP_LIMIT_MOUNTS]: {
          type: 'string',
          value: this.getAttributeValue(CP_CAP_LIMIT_MOUNTS, allowSensitive)
        }
      };
    }
    return parameters;
  }
}

const STORE_NAME = 'currentUserAttributes';

function withCurrentUserAttributes (options = {}) {
  const {
    injectUserInfo = false,
    observer: observe = false
  } = options;
  return (WrappedComponent) => {
    const component = observe ? observer(WrappedComponent) : WrappedComponent;
    const injectedComponent = inject(STORE_NAME)(component);
    return injectUserInfo ? roleModel.authenticationInfo(injectedComponent) : injectedComponent;
  };
}

export {
  STORE_NAME as CURRENT_USER_ATTRIBUTES_STORE,
  withCurrentUserAttributes
};

export default CurrentUserAttributes;
