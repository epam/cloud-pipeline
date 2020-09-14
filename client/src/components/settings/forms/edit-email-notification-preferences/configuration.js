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

const NotificationTypes = {
  HighConsumedResourcesType,
  IdleRunType,
  IdleRunPausedType,
  IdleRunStoppedType,
  LongPausedType
};

const IdleNotificationsTitle = (
  <div>
    {/* eslint-disable-next-line max-len */}
    Common <b>Idle</b>-notifications ( <i>IDLE_RUN</i>, <i>IDLE_RUN_PAUSED</i>, <i>IDLE_RUN_STOPPED</i> ) settings
  </div>
);

const PreferencesSectionTitle = {
  [IdleRunType]: IdleNotificationsTitle,
  [IdleRunPausedType]: IdleNotificationsTitle,
  [IdleRunStoppedType]: IdleNotificationsTitle
};

const SystemDiskConsumeThresholdPreference = {
  preference: 'system.disk.consume.threshold',
  type: 'number',
  min: 0,
  max: 100,
  name: 'Threshold of disk consume (%)'
};

const SystemMemoryConsumeThresholdPreference = {
  preference: 'system.memory.consume.threshold',
  type: 'number',
  min: 0,
  max: 100,
  name: 'Threshold of memory consume (%)'
};

const SystemMaxIdleTimeoutMinutesPreference = {
  preference: 'system.max.idle.timeout.minutes',
  type: 'number',
  min: 0,
  name: 'Max duration of idle (min)'
};

const SystemIdleActionTimeoutMinutesPreference = {
  preference: 'system.idle.action.timeout.minutes',
  type: 'number',
  min: 0,
  name: 'Resend/action delay (min)'
};

const SystemIdleCPUThresholdPreference = {
  preference: 'system.idle.cpu.threshold',
  type: 'number',
  min: 0,
  max: 100,
  name: 'CPU idle threshold (%)'
};

const SystemIdleActionPreference = {
  preference: 'system.idle.action',
  type: 'string',
  name: 'Action'
};

const Preferences = [
  SystemDiskConsumeThresholdPreference,
  SystemMemoryConsumeThresholdPreference,
  SystemMaxIdleTimeoutMinutesPreference,
  SystemIdleActionTimeoutMinutesPreference,
  SystemIdleCPUThresholdPreference,
  SystemIdleActionPreference
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
    SystemIdleActionPreference.preference
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
  [LongPausedType]: []
};

export {
  NotificationTypes,
  NotificationPreferences,
  Preferences,
  PreferencesSectionTitle
};
