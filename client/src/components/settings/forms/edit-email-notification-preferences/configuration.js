/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

const HighConsumedResourcesType = 'HIGH_CONSUMED_RESOURCES';
const IdleRunType = 'IDLE_RUN';
const IdleRunPausedType = 'IDLE_RUN_PAUSED';
const IdleRunStoppedType = 'IDLE_RUN_STOPPED';
const LongPausedType = 'LONG_PAUSED';
const LongPausedStoppedType = 'LONG_PAUSED_STOPPED';

const NotificationTypes = {
  HighConsumedResourcesType,
  IdleRunType,
  IdleRunPausedType,
  IdleRunStoppedType,
  LongPausedType,
  LongPausedStoppedType
};

const IdleNotificationsTitle = (
  <div>
    {/* eslint-disable-next-line max-len */}
    Common <b>Idle</b>-notifications ( <i>IDLE_RUN</i>, <i>IDLE_RUN_PAUSED</i>, <i>IDLE_RUN_STOPPED</i> ) settings
  </div>
);

const PausedNotificationsTitle = (
  <div>
    {/* eslint-disable-next-line max-len */}
    Common <b>Long Paused</b>-notifications ( <i>LONG_PAUSED</i>, <i>LONG_PAUSED_STOPPED</i>) settings
  </div>
);

const PreferencesSectionTitle = {
  [IdleRunType]: IdleNotificationsTitle,
  [IdleRunPausedType]: IdleNotificationsTitle,
  [IdleRunStoppedType]: IdleNotificationsTitle,
  [LongPausedType]: PausedNotificationsTitle,
  [LongPausedStoppedType]: PausedNotificationsTitle
};

const SystemDiskConsumeThresholdPreference = {
  preference: 'system.disk.consume.threshold',
  type: 'number',
  min: 0,
  max: 100,
  name: 'Threshold of disk consumption (%)',
  hint: (
    <div>
      <b>Specifies disk threshold (in %)</b> above which the notification will be sent<br />
      and the corresponding run will be marked by the
      <span
        style={{
          color: '#ae1726',
          border: '1px solid #ae1726',
          padding: '2px 5px',
          borderRadius: 5,
          margin: 3,
          lineHeight: 1
        }}
      >
        PRESSURE
      </span>
      label.
    </div>
  )
};

const SystemMemoryConsumeThresholdPreference = {
  preference: 'system.memory.consume.threshold',
  type: 'number',
  min: 0,
  max: 100,
  name: 'Threshold of memory consumption (%)',
  hint: (
    <div>
      <b>Specifies memory threshold (in %)</b> above which the notification will be sent<br />
      and the corresponding run will be marked by the
      <span
        style={{
          color: '#ae1726',
          border: '1px solid #ae1726',
          padding: '2px 5px',
          borderRadius: 5,
          margin: 3,
          lineHeight: 1
        }}
      >
        PRESSURE
      </span>
      label.
    </div>
  )
};

const SystemMaxIdleTimeoutMinutesPreference = {
  preference: 'system.max.idle.timeout.minutes',
  type: 'number',
  min: 0,
  name: 'Max duration of idle (min)',
  hint: (
    <div>
      <b>Specifies a duration in minutes.</b><br />
      This is a period during which the <i>idle</i> state of the run is not being checked.<br />
      After this period, the System starts to verify CPU utilization of the running node<br />
      and mark the run by the
      <span
        style={{
          color: '#f79e2c',
          border: '1px solid #f79e2c',
          padding: '2px 5px',
          borderRadius: 5,
          margin: 3,
          lineHeight: 1
        }}
      >
        IDLE
      </span>
      label when the utilization is low.
    </div>
  )
};

const SystemIdleActionTimeoutMinutesPreference = {
  preference: 'system.idle.action.timeout.minutes',
  type: 'number',
  min: 0,
  name: 'Action delay (min)',
  hint: (
    <div>
      <b>Specifies a duration in minutes.</b><br />
      This duration starts after the <b>"Max duration of idle (min)"</b> is over.<br />
      This is a delay before the configured action of the <i>idle</i> run will be performed.<br />
    </div>
  )
};

const SystemIdleCPUThresholdPreference = {
  preference: 'system.idle.cpu.threshold',
  type: 'number',
  min: 0,
  max: 100,
  name: 'CPU idle threshold (%)',
  hint: (
    <div>
      <b>Specifies percentage of the CPU utilization</b>, below which an action with the
      <i style={{margin: 3}}>idle</i>
      run will be performed.
    </div>
  )
};

const SystemIdleActionPreference = {
  preference: 'system.idle.action',
  type: 'enum',
  name: 'Action',
  enum: ['NOTIFY', 'PAUSE', 'PAUSE_OR_STOP', 'STOP'],
  hint: (
    <div>
      Sets the <b>action</b> to perform with the instance with low CPU utilization
      (below the configured threshold).
    </div>
  )
};

const SystemMaxLongPausedTimeoutMinutesPreference = {
  preference: 'system.max.long.paused.timeout.minutes',
  type: 'number',
  min: 0,
  name: 'Max duration of pause (min)'
};

const SystemLongPausedActionTimeoutMinutesPreference = {
  preference: 'system.long.paused.action.timeout.minutes',
  type: 'number',
  min: 0,
  name: 'Action delay (min)'
};

const SystemLongPausedActionPreference = {
  preference: 'system.long.paused.action',
  type: 'enum',
  name: 'Action',
  enum: ['NOTIFY', 'STOP'],
  hint: (
    <div>
      Sets the <b>action</b> to perform with the instance
      having the long paused state (longer then configured threshold).
    </div>
  )
};

const SystemNotificationsExcludeInstanceTypesPreference = {
  preference: 'system.notifications.exclude.instance.types',
  type: 'string',
  name: 'Exclude instance types',
  hint: (
    <div>
      <b>Instances, listed here won't trigger the IDLE notification.</b><br />
      The list shall be provided as a comma-separated string, you can also use the wildcards.
      E.g. m5.xlarge,*.large
    </div>
  )
};

const Preferences = [
  SystemDiskConsumeThresholdPreference,
  SystemMemoryConsumeThresholdPreference,
  SystemMaxIdleTimeoutMinutesPreference,
  SystemIdleActionTimeoutMinutesPreference,
  SystemIdleCPUThresholdPreference,
  SystemIdleActionPreference,
  SystemMaxLongPausedTimeoutMinutesPreference,
  SystemLongPausedActionTimeoutMinutesPreference,
  SystemLongPausedActionPreference,
  SystemNotificationsExcludeInstanceTypesPreference
];

const NotificationPreferences = {
  [HighConsumedResourcesType]: [
    SystemDiskConsumeThresholdPreference.preference,
    SystemMemoryConsumeThresholdPreference.preference
  ],
  [IdleRunType]: [
    SystemMaxIdleTimeoutMinutesPreference.preference,
    SystemIdleActionTimeoutMinutesPreference.preference,
    SystemIdleCPUThresholdPreference.preference,
    SystemIdleActionPreference.preference,
    SystemNotificationsExcludeInstanceTypesPreference.preference
  ],
  [IdleRunPausedType]: [
    SystemMaxIdleTimeoutMinutesPreference.preference,
    SystemIdleActionTimeoutMinutesPreference.preference,
    SystemIdleCPUThresholdPreference.preference,
    SystemIdleActionPreference.preference
  ],
  [IdleRunStoppedType]: [
    SystemMaxIdleTimeoutMinutesPreference.preference,
    SystemIdleActionTimeoutMinutesPreference.preference,
    SystemIdleCPUThresholdPreference.preference,
    SystemIdleActionPreference.preference
  ],
  [LongPausedType]: [
    SystemLongPausedActionPreference.preference
  ],
  [LongPausedStoppedType]: [
    SystemLongPausedActionPreference.preference
  ]
};

export {
  NotificationTypes,
  NotificationPreferences,
  Preferences,
  PreferencesSectionTitle
};
