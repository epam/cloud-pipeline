/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Checkbox, Icon, InputNumber, Popover, Select} from 'antd';
import styles from './preference-control.css';

const IdleTypes = {
  ABSOLUTE: 'ABSOLUTE',
  GPU: 'GPU',
  CPU: 'CPU'
};

const IdleTypeLabels = {
  [IdleTypes.ABSOLUTE]: 'IDLE_RUN, IDLE_RUN_PAUSED, IDLE_RUN_STOPPED',
  [IdleTypes.GPU]: 'IDLE_GPU_RUN',
  [IdleTypes.CPU]: 'IDLE_CPU_RUN'
};

const IdleActions = ['NOTIFY', 'PAUSE', 'PAUSE_OR_STOP', 'STOP'];

const Hints = {
  [IdleTypes.ABSOLUTE]: {
    actionTimeoutMinutes: (
      <div>
        <b>Specifies a duration in minutes.</b><br />
        This duration starts after the idle state of the run is detected.<br />
        This is a delay before the configured action of the <i>idle</i> run will be performed.
      </div>
    ),
    action: (
      <div>
        Sets the <b>action</b> to perform with the idle run
        (CPU or GPU utilization below the configured threshold).
      </div>
    )
  },
  [IdleTypes.GPU]: {
    gracePeriodMinutes: (
      <div>
        <b>Specifies a duration in minutes.</b><br />
        This is the period over which GPU activity is monitored.<br />
        After this period, if no GPU activity is observed, the run will be marked by the
        <span
          className="cp-tag warning"
          style={{padding: '2px 5px', borderRadius: 5, margin: 3, lineHeight: 1}}
        >
          IDLE_GPU
        </span>
        label.
      </div>
    ),
    actionTimeoutMinutes: (
      <div>
        <b>Specifies a duration in minutes.</b><br />
        This duration starts after the GPU idle state is detected.<br />
        This is a delay before the configured action of the <i>GPU idle</i> run will be performed.
      </div>
    ),
    action: (
      <div>
        Sets the <b>action</b> to perform with the GPU idle run
        (no GPU activity observed during the monitoring period).
      </div>
    )
  },
  [IdleTypes.CPU]: {
    thresholdPercent: (
      <div>
        <b>Specifies the percentage of CPU utilization</b>, below which
        the run is considered CPU idle.
      </div>
    ),
    gracePeriodMinutes: (
      <div>
        <b>Specifies a duration in minutes.</b><br />
        {/* eslint-disable-next-line max-len */}
        This is the period over which CPU utilization is monitored.<br />
        After this period, if CPU utilization stays below the threshold, the run will be marked by the
        <span
          className="cp-tag warning"
          style={{padding: '2px 5px', borderRadius: 5, margin: 3, lineHeight: 1}}
        >
          IDLE_CPU
        </span>
        label.
      </div>
    ),
    actionTimeoutMinutes: (
      <div>
        <b>Specifies a duration in minutes.</b><br />
        This duration starts after the CPU idle state is detected.<br />
        This is a delay before the configured action of the <i>CPU idle</i> run will be performed.
      </div>
    ),
    action: (
      <div>
        Sets the <b>action</b> to perform with the CPU idle run
        (CPU utilization below the configured threshold).
      </div>
    )
  }
};

function renderHint (content) {
  if (!content) {
    return null;
  }
  return (
    <Popover content={content} placement="left">
      <Icon
        style={{
          marginLeft: 5,
          marginRight: 10,
          fontSize: 'larger',
          cursor: 'pointer'
        }}
        type="question-circle"
      />
    </Popover>
  );
}

function IdleTypeSection ({typeKey, config = {}, onChange, disabled}) {
  const hints = Hints[typeKey] || {};
  const update = (field) => (value) => {
    onChange({...config, type: typeKey, [field]: value});
  };

  const hasGracePeriod = typeKey === IdleTypes.GPU || typeKey === IdleTypes.CPU;
  const hasCpuThreshold = typeKey === IdleTypes.CPU;

  return (
    <div>
      <div style={{fontWeight: 'bold', margin: '5px 0'}}>
        {IdleTypeLabels[typeKey]}
      </div>
      <div className={styles.controlRow}>
        <span className={styles.label}>Enable action</span>
        <Checkbox
          disabled={disabled}
          checked={!!config.enabled}
          onChange={(e) => update('enabled')(e.target.checked)}
        />
        {renderHint(hints.enabled)}
      </div>
      {hasCpuThreshold && (
        <div className={styles.controlRow}>
          <span className={styles.label}>CPU threshold (%)</span>
          <InputNumber
            disabled={disabled}
            className={styles.control}
            value={config.thresholdPercent}
            min={0}
            max={100}
            onChange={update('thresholdPercent')}
          />
          {renderHint(hints.thresholdPercent)}
        </div>
      )}
      {hasGracePeriod && (
        <div className={styles.controlRow}>
          <span className={styles.label}>Max duration of idle (min)</span>
          <InputNumber
            disabled={disabled}
            className={styles.control}
            value={config.gracePeriodMinutes}
            min={0}
            onChange={update('gracePeriodMinutes')}
          />
          {renderHint(hints.gracePeriodMinutes)}
        </div>
      )}
      <div className={styles.controlRow}>
        <span className={styles.label}>Action delay (min)</span>
        <InputNumber
          disabled={disabled}
          className={styles.control}
          value={config.actionTimeoutMinutes}
          min={0}
          onChange={update('actionTimeoutMinutes')}
        />
        {renderHint(hints.actionTimeoutMinutes)}
      </div>
      <div className={styles.controlRow}>
        <span className={styles.label}>Action</span>
        <Select
          disabled={disabled}
          className={styles.control}
          value={config.action}
          onChange={update('action')}
        >
          {IdleActions.map((a) => (
            <Select.Option key={a} value={a}>{a}</Select.Option>
          ))}
        </Select>
        {renderHint(hints.action)}
      </div>
    </div>
  );
}

IdleTypeSection.propTypes = {
  typeKey: PropTypes.string,
  config: PropTypes.object,
  onChange: PropTypes.func,
  disabled: PropTypes.bool
};

const AllIdleTypes = [IdleTypes.ABSOLUTE, IdleTypes.GPU, IdleTypes.CPU];

function IdleMonitoringSettingsControl ({value = {}, onChange, pending, visibleTypes}) {
  const types = visibleTypes && visibleTypes.length ? visibleTypes : AllIdleTypes;

  const handleTypeChange = (typeKey) => (typeConfig) => {
    onChange({...value, [typeKey]: typeConfig});
  };

  return (
    <div>
      {types.map((typeKey) => (
        <IdleTypeSection
          key={typeKey}
          typeKey={typeKey}
          config={value[typeKey]}
          onChange={handleTypeChange(typeKey)}
          disabled={pending}
        />
      ))}
    </div>
  );
}

IdleMonitoringSettingsControl.propTypes = {
  value: PropTypes.object,
  onChange: PropTypes.func,
  pending: PropTypes.bool,
  visibleTypes: PropTypes.arrayOf(PropTypes.string)
};

export {IdleTypes};
export default IdleMonitoringSettingsControl;
