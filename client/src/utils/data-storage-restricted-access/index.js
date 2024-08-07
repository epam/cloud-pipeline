/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import preferences from '../../models/preferences/PreferencesLoad';
import authenticatedUserInfo from '../../models/user/WhoAmI';
import {
  ContextualPreferenceSearch,
  names as ContextualPreferences
} from '../../models/utils/ContextualPreference';

/**
 * @typedef {Object} ContextualPreferenceResource
 * @property {string|number} resourceId
 * @property {string} level
 */

/**
 * @param {number} storageId
 * @returns {Promise<undefined|boolean>}
 */
async function safelyCheckRestrictedAccessForStorage (storageId) {
  try {
    const request = new ContextualPreferenceSearch();
    await request.send({
      preferences: [ContextualPreferences.storageManagementRestrictedAccess],
      resource: {
        level: 'STORAGE',
        resourceId: storageId
      }
    });
    if (request.loaded && request.value) {
      const {
        value
      } = request.value;
      return `${value}`.toLowerCase() === 'true';
    }
  } catch (error) {
    // empty
  }
  return undefined;
}

class DataStorageRestrictedAccess {
  constructor () {
    this.storagePreferenceCheckPromises = new Map();
  }

  clear () {
    this.storagePreferenceCheckPromises.clear();
  }

  userAdminCheck = () => {
    if (!this.userAdminCheckPromise) {
      this.userAdminCheckPromise = new Promise((resolve) => {
        authenticatedUserInfo
          .fetchIfNeededOrWait()
          .then(() => {
            const {
              admin
            } = authenticatedUserInfo.value || {};
            resolve(admin ? false : undefined);
          })
          .catch(() => resolve(undefined));
      });
    }
    return this.userAdminCheckPromise;
  };

  globalPreferenceCheck = async () => {
    await preferences.fetchIfNeededOrWait();
    return preferences.storageManagementRestrictedAccess;
  };

  storagePreferenceCheck = (storageId) => {
    if (!storageId || Number.isNaN(Number(storageId))) {
      return Promise.resolve(undefined);
    }
    const id = Number(storageId);
    if (!this.storagePreferenceCheckPromises[id]) {
      this.storagePreferenceCheckPromises[id] = safelyCheckRestrictedAccessForStorage(id);
    }
    return this.storagePreferenceCheckPromises[id];
  };

  performPrioritizedChecks = async (...checks) => {
    if (checks.length === 0) {
      return Promise.resolve(undefined);
    }
    const [
      highPriorityCheck,
      ...lowPriorityChecks
    ] = checks;
    if (typeof highPriorityCheck !== 'function') {
      return this.performPrioritizedChecks(...lowPriorityChecks);
    }
    const result = await highPriorityCheck();
    if (typeof result === 'boolean') {
      return result;
    }
    return this.performPrioritizedChecks(...lowPriorityChecks);
  };

  check = (storageId) => {
    if (!storageId || Number.isNaN(Number(storageId))) {
      return undefined;
    }
    return this.performPrioritizedChecks(
      this.userAdminCheck,
      () => this.storagePreferenceCheck(storageId),
      this.globalPreferenceCheck
    );
  }
}

const checker = new DataStorageRestrictedAccess();

export function clearCache () {
  checker.clear();
}

export default function dataStorageRestrictedAccessCheck (storageId) {
  return checker.check(storageId);
}
