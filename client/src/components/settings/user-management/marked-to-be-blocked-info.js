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
import PropTypes from 'prop-types';
import {observer, inject} from 'mobx-react';
import moment from 'moment-timezone';
import {Popover} from 'antd';
import displayDate from '../../../utils/displayDate';

function MarkedToBeBlockedInfo (
  {
    className,
    style,
    externalBlockDate,
    preferences
  }
) {
  if (!externalBlockDate) {
    return null;
  }
  const markedDate = moment.utc(externalBlockDate);
  const gracePeriodDays = preferences.loaded
    ? preferences.systemLdapUserBlockMonitorGracePeriodDays
    : 0;
  const infos = [
    (
      <div
        key="marked"
      >
        User was marked on <b>{displayDate(externalBlockDate, 'D MMMM YYYY')}</b>
      </div>
    )
  ];
  if (markedDate && markedDate.isValid()) {
    infos.push((
      <div
        key="grace-period"
      >
        Grace period is <b>{gracePeriodDays} day{gracePeriodDays === 1 ? '' : 's'}</b>
      </div>
    ));
    const blockedDay = moment(markedDate).add(gracePeriodDays, 'day');
    infos.push((
      <div
        key="block"
      >
        User will be blocked on <b>{displayDate(blockedDay, 'D MMMM YYYY')}</b>
      </div>
    ));
    return (
      <Popover
        content={(
          <div>
            {infos}
          </div>
        )}
      >
        <span
          className={className}
          style={style}
        >
          User will be blocked on <b>{displayDate(blockedDay, 'D MMMM YYYY')}</b>
        </span>
      </Popover>
    );
  }
  return null;
}

MarkedToBeBlockedInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  externalBlockDate: PropTypes.string
};

export default inject('preferences')(observer(MarkedToBeBlockedInfo));
