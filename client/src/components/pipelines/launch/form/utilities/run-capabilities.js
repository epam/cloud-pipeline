import React from 'react';
import PropTypes from 'prop-types';
import {Select} from 'antd';
import {booleanParameterIsSetToValue} from './parameter-utilities';
import {
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_SINGULARITY,
  CP_CAP_DESKTOP_NM,
  CP_CAP_MODULES
} from './parameters';

export const RUN_CAPABILITIES = {
  dinD: 'DinD',
  singularity: 'Singularity',
  systemD: 'SystemD',
  noMachine: 'NoMachine',
  module: 'Module'
};

export const RUN_CAPABILITIES_PARAMETERS = {
  [RUN_CAPABILITIES.dinD]: CP_CAP_DIND_CONTAINER,
  [RUN_CAPABILITIES.singularity]: CP_CAP_SINGULARITY,
  [RUN_CAPABILITIES.systemD]: CP_CAP_SYSTEMD_CONTAINER,
  [RUN_CAPABILITIES.noMachine]: CP_CAP_DESKTOP_NM,
  [RUN_CAPABILITIES.module]: CP_CAP_MODULES
};

const OS_SPECIFIC_CAPABILITIES = {
  default: [
    RUN_CAPABILITIES.dinD,
    RUN_CAPABILITIES.singularity,
    RUN_CAPABILITIES.systemD,
    RUN_CAPABILITIES.noMachine,
    RUN_CAPABILITIES.module
  ],
  windows: []
};

function getPlatformSpecificCapabilities (platform) {
  if (OS_SPECIFIC_CAPABILITIES.hasOwnProperty(platform)) {
    return OS_SPECIFIC_CAPABILITIES[platform];
  }
  return OS_SPECIFIC_CAPABILITIES.default;
}

export default class RunCapabilities extends React.Component {
  static propTypes = {
    values: PropTypes.arrayOf(PropTypes.string),
    platform: PropTypes.string,
    onChange: PropTypes.func
  };

  static defaultProps = {
    values: null,
    onChange: null
  };

  onSelectionChanged = (values) => {
    const {onChange} = this.props;

    onChange && onChange(values || []);
  };

  render () {
    const {values, platform} = this.props;
    const capabilities = getPlatformSpecificCapabilities(platform);
    const filteredValues = (values || []).filter(value => capabilities.indexOf(value) >= 0);
    return (
      <Select
        allowClear
        mode="multiple"
        onChange={this.onSelectionChanged}
        placeholder="None selected"
        size="large"
        value={filteredValues || []}
        filterOption={
          (input, option) => option.props.children.toLowerCase().includes(input.toLowerCase())
        }
      >
        {capabilities.map(o => (<Select.Option key={o}>{o}</Select.Option>))}
      </Select>
    );
  }
}

export function getRunCapabilitiesSkippedParameters () {
  return Object.values(RUN_CAPABILITIES_PARAMETERS);
}

export function capabilityEnabled (parameters, capability) {
  return booleanParameterIsSetToValue(parameters, RUN_CAPABILITIES_PARAMETERS[capability]);
}

export function getEnabledCapabilities (parameters) {
  const result = [];
  Object.keys(RUN_CAPABILITIES_PARAMETERS)
    .forEach(capability => {
      if (capabilityEnabled(parameters, capability)) {
        result.push(capability);
      }
    });
  return result;
}

export function addCapability (capabilities, ...capability) {
  const result = (capabilities || []).slice();
  capability.forEach(c => {
    if (!result.includes(capability)) {
      result.push(capability);
    }
  });
  return result;
}

export function applyCapabilities (platform, parameters, capabilities = []) {
  if (!parameters) {
    parameters = {};
  }
  const platformSpecificCapabilities = getPlatformSpecificCapabilities(platform);
  capabilities
    .filter(capability => platformSpecificCapabilities.indexOf(capability) >= 0)
    .forEach(capability => {
      const parameterName = RUN_CAPABILITIES_PARAMETERS[capability];
      parameters[parameterName] = {
        type: 'boolean',
        value: true
      };
    });
  return parameters;
}

export function hasPlatformSpecificCapabilities (platform) {
  return getPlatformSpecificCapabilities(platform).length > 0;
}

export function checkRunCapabilitiesModified (capabilities1, capabilities2) {
  const sorted = array => [...(new Set((array || []).sort()))];
  const sorted1 = sorted(capabilities1 || []);
  const sorted2 = sorted(capabilities2 || []);
  if (sorted1.length !== sorted2.length) {
    return true;
  }
  for (let i = 0; i < sorted1.length; i++) {
    if (sorted1[i] !== sorted2[i]) {
      return true;
    }
  }
  return false;
}
