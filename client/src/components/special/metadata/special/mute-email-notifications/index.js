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

import React from 'react';
import {inject} from 'mobx-react';
import PropTypes from 'prop-types';
import {Checkbox} from 'antd';
import moment from 'moment-timezone';
import {
  USER_NOTIFICATIONS_CONFIGURATION_ATTRIBUTE,
  parseMuteEmailNotificationValue,
  buildMuteEmailNotificationValue
} from '../../../../../models/notifications/CurrentUserNotifications';

function MuteEmailNotifications ({
  metadata,
  onChange,
  disabled,
  readOnly,
  style,
  userNotifications
}) {
  const {value} = metadata || {};
  const {
    muted
  } = parseMuteEmailNotificationValue(value);
  const handleChange = event => {
    const displayAfter = moment.utc();
    const payload = buildMuteEmailNotificationValue({
      muted: event.target.checked,
      displayAfter
    });
    userNotifications.setUserConfiguration(event.target.checked, displayAfter);
    onChange && onChange(payload);
  };
  return (
    <div
      style={
        Object.assign(
          {margin: '5px 0'},
          style || {}
        )
      }
    >
      <Checkbox
        onChange={handleChange}
        checked={muted}
        disabled={disabled || readOnly}
      >
        Mute email notifications
      </Checkbox>
    </div>
  );
}

MuteEmailNotifications.isOptional = true;
MuteEmailNotifications.metadataKey = USER_NOTIFICATIONS_CONFIGURATION_ATTRIBUTE;

MuteEmailNotifications.propTypes = {
  metadata: PropTypes.object,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  readOnly: PropTypes.bool,
  style: PropTypes.object
};

export default inject('userNotifications')(MuteEmailNotifications);
