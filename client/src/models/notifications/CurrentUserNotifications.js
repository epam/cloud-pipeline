/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import {action, computed, observable} from 'mobx';
import moment from 'moment-timezone';
import whoAmI from '../user/WhoAmI';
import continuousFetch from '../../utils/continuous-fetch';
import CurrentUserNotificationsPaging from './CurrentUserNotificationsPaging';
import MetadataLoad from '../metadata/MetadataLoad';
import MetadataUpdateKeys from '../metadata/MetadataUpdateKeys';

const DEFAULT_PAGE_SIZE = 20;
const FETCH_INTERVAL_SECONDS = 60;

/**
 * @param {string} value
 * @returns {{muted: boolean, displayAfter: moment.Moment|undefined}}
 */
export function parseMuteEmailNotificationValue (value) {
  try {
    if (/^(true|false)$/i.test(value)) {
      return {
        muted: /^true$/i.test(value)
      };
    }
    const json = JSON.parse(value);
    const {
      muted = false,
      displayAfter
    } = json;
    return {
      muted,
      displayAfter: displayAfter ? moment.utc(displayAfter) : undefined
    };
  } catch (_) {
    // empty
  }
  return {
    muted: false
  };
}

/**
 * @param {{muted: boolean, displayAfter: string|moment.Moment|undefined}} options
 * @returns {string}
 */
export function buildMuteEmailNotificationValue (options) {
  const {
    muted = false,
    displayAfter
  } = options;
  const value = {
    muted,
    displayAfter: displayAfter
      ? moment.utc(displayAfter).format('YYYY-MM-DD HH:mm:ss')
      : undefined
  };
  return JSON.stringify(value);
}

const USER_NOTIFICATIONS_CONFIGURATION_ATTRIBUTE = 'ui.notifications.mute';

export {USER_NOTIFICATIONS_CONFIGURATION_ATTRIBUTE};

class CurrentUserNotifications extends CurrentUserNotificationsPaging {
  @observable _hideNotificationsTill;
  @observable _muteNotifications = true;

  userNotificationsConfigurationPromise;

  constructor () {
    super(0, DEFAULT_PAGE_SIZE, false);
    this.userNotificationsConfigurationPromise = this.readUserConfiguration();
    continuousFetch({
      request: this,
      intervalMS: FETCH_INTERVAL_SECONDS * 1000
    });
  }

  @computed
  get hideNotificationsTill () {
    return this._hideNotificationsTill;
  }

  @computed
  get muted () {
    return this._muteNotifications;
  }

  @action
  hideNotifications (date = moment.utc()) {
    this._hideNotificationsTill = date;
    const attributeValue = buildMuteEmailNotificationValue({
      muted: this._muteNotifications,
      displayAfter: date
    });
    (async () => {
      try {
        await whoAmI.fetchIfNeededOrWait();
        if (!whoAmI.loaded) {
          throw new Error('error fetching authenticated user info');
        }
        const {
          id
        } = whoAmI.value || {};
        const metadata = new MetadataUpdateKeys();
        await metadata.send({
          entity: {
            entityId: id,
            entityClass: 'PIPELINE_USER'
          },
          data: {
            [USER_NOTIFICATIONS_CONFIGURATION_ATTRIBUTE]: {
              value: attributeValue,
              type: 'string'
            }
          }
        });
        if (!metadata.loaded) {
          throw new Error('error updating authenticated user attributes');
        }
      } catch (error) {
        console.warn(`Error updating user notifications configuration: ${error.message}`);
      }
    })();
  }

  @action
  setUserConfiguration = (muted = false, displayAfter) => {
    this._muteNotifications = muted;
    if (displayAfter) {
      this._hideNotificationsTill = displayAfter;
    }
  };

  readUserConfiguration = async () => {
    try {
      await whoAmI.fetchIfNeededOrWait();
      if (!whoAmI.loaded) {
        throw new Error('error fetching authenticated user info');
      }
      const {
        id
      } = whoAmI.value || {};
      const metadata = new MetadataLoad(id, 'PIPELINE_USER');
      await metadata.fetch();
      if (!metadata.loaded) {
        throw new Error('error fetching authenticated user attributes');
      }
      const [currentMetadata = {}] = metadata.value || [];
      const {
        data = {}
      } = currentMetadata;
      const attribute = data[USER_NOTIFICATIONS_CONFIGURATION_ATTRIBUTE] || {};
      const {
        muted,
        displayAfter
      } = parseMuteEmailNotificationValue(attribute.value);
      this.setUserConfiguration(muted, displayAfter);
    } catch (error) {
      console.warn(`Error reading user notifications configuration: ${error.message}`);
    }
  };

  onFetched;

  async preFetch () {
    await this.userNotificationsConfigurationPromise;
    await super.preFetch();
  }
}

export {DEFAULT_PAGE_SIZE};

export default new CurrentUserNotifications();
