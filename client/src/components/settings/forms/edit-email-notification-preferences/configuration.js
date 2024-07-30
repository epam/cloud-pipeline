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
const HighConsumedNetworkBandwidthType = 'HIGH_CONSUMED_NETWORK_BANDWIDTH';
const IdleRunType = 'IDLE_RUN';
const IdleRunPausedType = 'IDLE_RUN_PAUSED';
const IdleRunStoppedType = 'IDLE_RUN_STOPPED';
const LongPausedType = 'LONG_PAUSED';
const LongPausedStoppedType = 'LONG_PAUSED_STOPPED';

const NotificationTypes = {
  HighConsumedResourcesType,
  HighConsumedNetworkBandwidthType,
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
  name: 'Threshold of disk consumption (%)',
  hint: (
    <div>
      <b>Specifies disk threshold (in %)</b> above which the notification will be sent<br />
      and the corresponding run will be marked by the
      <span
        className="cp-tag critical"
        style={{
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
        className="cp-tag critical"
        style={{
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
        className="cp-tag warning"
        style={{
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

const SystemNotificationsExcludeParamsPreference = {
  preference: 'system.notifications.exclude.params',
  type: 'excludeParamsControl',
  name: 'Exclude by parameters',
  hint: (
    <div>
      <b>Runs with parameters which matches any of the rules won't trigger the IDLE notification.</b>
      <br />
      Each parameter has a corresponding value and a comparison operator.
      <br />
      If any of the rules can be applied to the Run - IDLE notifications will be skipped.
    </div>
  )
};

// system.max.pod.bandwidth.minutes
const SystemMaxPodBandwidthMinutesPreference = {
  preference: 'system.max.pod.bandwidth.minutes',
  type: 'number',
  min: 0,
  name: 'Network bandwidth measurement period (min)',
  hint: (
    <div>
      Network bandwidth is calculated as an average within specified <b>period</b>
    </div>
  )
};
// system.pod.bandwidth.limit
const SystemPodBandwidthLimitPreference = {
  preference: 'system.pod.bandwidth.limit',
  type: 'number',
  min: 0,
  name: 'Network bandwidth limit (bytes/sec)',
  hint: (
    <div>
      If the network bandwidth is more than specified <b>limit</b>,
      the configured <b>action</b> will be performed
    </div>
  )
};
// system.pod.bandwidth.action
const SystemPodBandwidthActionPreference = {
  preference: 'system.pod.bandwidth.action',
  type: 'enum',
  name: 'Action',
  enum: ['NOTIFY', 'LIMIT_BANDWIDTH'],
  hint: (
    <div>
      Sets the <b>action</b> to perform with the instance having the high network bandwidth
      (above the configured limit).
    </div>
  )
};
// system.pod.bandwidth.action.backoff.period
const SystemPodBandwidthActionBackoffPeriodPreference = {
  preference: 'system.pod.bandwidth.action.backoff.period',
  type: 'number',
  min: -1,
  name: 'Action delay (min)',
  hint: (
    <div>
      <b>Specifies a duration in minutes.</b><br />
      This duration starts after the <b>high network bandwidth</b> event is registered.<br />
      This is a delay before the configured action will be performed.
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
  SystemNotificationsExcludeInstanceTypesPreference,
  SystemNotificationsExcludeParamsPreference,
  SystemMaxPodBandwidthMinutesPreference,
  SystemPodBandwidthLimitPreference,
  SystemPodBandwidthActionPreference,
  SystemPodBandwidthActionBackoffPeriodPreference
];

const NotificationPreferences = {
  [HighConsumedResourcesType]: [
    SystemDiskConsumeThresholdPreference.preference,
    SystemMemoryConsumeThresholdPreference.preference
  ],
  [HighConsumedNetworkBandwidthType]: [
    SystemMaxPodBandwidthMinutesPreference.preference,
    SystemPodBandwidthLimitPreference.preference,
    SystemPodBandwidthActionPreference.preference,
    SystemPodBandwidthActionBackoffPeriodPreference.preference
  ],
  [IdleRunType]: [
    SystemMaxIdleTimeoutMinutesPreference.preference,
    SystemIdleActionTimeoutMinutesPreference.preference,
    SystemIdleCPUThresholdPreference.preference,
    SystemIdleActionPreference.preference,
    SystemNotificationsExcludeInstanceTypesPreference.preference,
    SystemNotificationsExcludeParamsPreference.preference
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
    SystemLongPausedActionPreference.preference,
    SystemLongPausedActionTimeoutMinutesPreference.preference
  ]
};

export {
  NotificationTypes,
  NotificationPreferences,
  Preferences,
  PreferencesSectionTitle
};
