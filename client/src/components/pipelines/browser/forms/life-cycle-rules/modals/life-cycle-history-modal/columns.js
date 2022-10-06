/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Icon,
  Tooltip
} from 'antd';
import {DESTINATIONS} from '../life-cycle-edit-modal';
// eslint-disable-next-line max-len
import {EXECUTION_STATUSES} from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesExecutionLoad';
import displayDate from '../../../../../../../utils/displayDate';

const FORMAT = 'YYYY-MM-DD';

const STATUS_CONFIG = {
  [EXECUTION_STATUSES.NOTIFICATION_SENT]: {
    type: 'mail',
    className: 'cp-primary',
    description: 'Notification sent'
  },
  [EXECUTION_STATUSES.RUNNING]: {
    type: 'clock-circle-o',
    className: 'cp-primary',
    description: 'Running'
  },
  [EXECUTION_STATUSES.SUCCESS]: {
    type: 'check-circle-o',
    className: 'cp-success',
    description: 'Success'
  },
  [EXECUTION_STATUSES.FAILED]: {
    type: 'exclamation-circle-o',
    className: 'cp-error',
    description: 'Failed'
  }
};

const columns = [{
  title: 'Date',
  dataIndex: 'date',
  key: 'date',
  render: (aDate) => displayDate(aDate)
}, {
  title: 'Action',
  dataIndex: 'action',
  key: 'action',
  render: (action, record) => {
    if (action === 'Transition') {
      return (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'nowrap'
          }}
        >
          <span>
            {action}
          </span>
          {STATUS_CONFIG[record.status] ? (
            <Tooltip
              title={STATUS_CONFIG[record.status].description}
            >
              <Icon
                type={STATUS_CONFIG[record.status].type}
                className={STATUS_CONFIG[record.status].className}
                style={{marginLeft: 5, fontSize: 'larger'}}
              />
            </Tooltip>
          ) : null}
        </div>
      );
    }
    return action;
  }
}, {
  title: 'User',
  dataIndex: 'user',
  key: 'user'
}, {
  title: 'Path',
  dataIndex: 'file',
  key: 'file'
}, {
  title: 'Destination',
  dataIndex: 'destination',
  key: 'destination',
  render: (destination) => (
    <span
      className={destination === DESTINATIONS.DELETION
        ? 'cp-error'
        : ''
      }
    >
      {destination}
    </span>
  )
}, {
  title: 'Prolongation, days',
  dataIndex: 'prolongation',
  key: 'prolongation'
}, {
  title: 'Renewed transition',
  dataIndex: 'transition',
  key: 'transition',
  render: (aDate) => displayDate(aDate, FORMAT)
}];

export default columns;
