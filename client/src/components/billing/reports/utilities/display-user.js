/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import {inject, observer} from 'mobx-react';

function getUserDisplayInfo (userName, users, preferences) {
  if (!users || !userName) {
    return userName;
  }
  let nameAttribute;
  if (preferences) {
    nameAttribute = preferences.getPreferenceValue('billing.reports.user.name.attribute');
  }
  if (nameAttribute && users.loaded) {
    const [user] = (users.value || [])
      .filter(u => (u.userName || '').toLowerCase() === userName.toLowerCase());
    if (user && user.attributes && user.attributes.hasOwnProperty(nameAttribute)) {
      return user.attributes[nameAttribute];
    }
  }
  return userName;
}

function DisplayUser ({userName, users, preferences}) {
  const displayName = getUserDisplayInfo(userName, users, preferences);
  return (
    <span>
      {displayName}
    </span>
  );
}

export {getUserDisplayInfo};
export default inject('users', 'preferences')(observer(DisplayUser));
