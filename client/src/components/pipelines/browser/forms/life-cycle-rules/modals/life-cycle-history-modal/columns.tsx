/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Tooltip} from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  MailOutlined,
} from '@ant-design/icons';
import type {TableColumnType} from 'antd';
import {DESTINATIONS} from '../life-cycle-edit-modal';
import {EXECUTION_STATUSES} from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesExecutionLoad';
import displayDate from '../../../../../../../utils/displayDate';

const FORMAT = 'YYYY-MM-DD';

interface StatusConfig {
  icon: React.ComponentType<{className?: string; style?: React.CSSProperties}>;
  className: string;
  description: string;
}

const STATUS_CONFIG: Record<string, StatusConfig> = {
  [EXECUTION_STATUSES.NOTIFICATION_SENT]: {
    icon: MailOutlined,
    className: 'cp-primary',
    description: 'Notification sent',
  },
  [EXECUTION_STATUSES.RUNNING]: {
    icon: ClockCircleOutlined,
    className: 'cp-primary',
    description: 'Running',
  },
  [EXECUTION_STATUSES.SUCCESS]: {
    icon: CheckCircleOutlined,
    className: 'cp-success',
    description: 'Success',
  },
  [EXECUTION_STATUSES.FAILED]: {
    icon: ExclamationCircleOutlined,
    className: 'cp-error',
    description: 'Failed',
  },
};

interface HistoryRow {
  date: unknown;
  action: string;
  user: string;
  file: string;
  prolongation?: string;
  transition?: unknown;
  destination?: string;
  status?: string;
}

const columns: TableColumnType<HistoryRow>[] = [
  {
    title: 'Date',
    dataIndex: 'date',
    key: 'date',
    render: (aDate) => displayDate(aDate),
  },
  {
    title: 'Action',
    dataIndex: 'action',
    key: 'action',
    render: (action: string, record: HistoryRow) => {
      if (action === 'Transition') {
        const config = record.status ? STATUS_CONFIG[record.status] : undefined;
        const StatusIconComponent = config ? config.icon : null;
        return (
          <div style={{display: 'flex', alignItems: 'center', flexWrap: 'nowrap'}}>
            <span>{action}</span>
            {StatusIconComponent && config ? (
              <Tooltip title={config.description}>
                <StatusIconComponent
                  className={config.className}
                  style={{marginLeft: 5, fontSize: 'larger'}}
                />
              </Tooltip>
            ) : null}
          </div>
        );
      }
      return action;
    },
  },
  {
    title: 'User',
    dataIndex: 'user',
    key: 'user',
  },
  {
    title: 'Path',
    dataIndex: 'file',
    key: 'file',
  },
  {
    title: 'Destination',
    dataIndex: 'destination',
    key: 'destination',
    render: (destination: string | undefined) => (
      <span className={destination === DESTINATIONS.DELETION ? 'cp-error' : ''}>
        {destination}
      </span>
    ),
  },
  {
    title: 'Prolongation, days',
    dataIndex: 'prolongation',
    key: 'prolongation',
  },
  {
    title: 'Renewed transition',
    dataIndex: 'transition',
    key: 'transition',
    render: (aDate) => displayDate(aDate, FORMAT),
  },
];

export default columns;
