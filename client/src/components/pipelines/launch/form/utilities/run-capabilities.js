import React from 'react';
import PropTypes from 'prop-types';
import {Select} from 'antd';
import {inject, observer} from 'mobx-react';
import {booleanParameterIsSetToValue} from './parameter-utilities';
import {
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_SINGULARITY,
  CP_CAP_DESKTOP_NM,
  CP_CAP_MODULES,
  CP_DISABLE_HYPER_THREADING
} from './parameters';

export const RUN_CAPABILITIES = {
  dinD: 'DinD',
  singularity: 'Singularity',
  systemD: 'SystemD',
  noMachine: 'NoMachine',
  module: 'Module',
  disableHyperThreading: 'Disable Hyper-Threading'
};

export const RUN_CAPABILITIES_PARAMETERS = {
  [RUN_CAPABILITIES.dinD]: CP_CAP_DIND_CONTAINER,
  [RUN_CAPABILITIES.singularity]: CP_CAP_SINGULARITY,
  [RUN_CAPABILITIES.systemD]: CP_CAP_SYSTEMD_CONTAINER,
  [RUN_CAPABILITIES.noMachine]: CP_CAP_DESKTOP_NM,
  [RUN_CAPABILITIES.module]: CP_CAP_MODULES,
  [RUN_CAPABILITIES.disableHyperThreading]: CP_DISABLE_HYPER_THREADING
};

const OS_SPECIFIC_CAPABILITIES = {
  default: [
    RUN_CAPABILITIES.dinD,
    RUN_CAPABILITIES.singularity,
    RUN_CAPABILITIES.systemD,
    RUN_CAPABILITIES.noMachine,
    RUN_CAPABILITIES.module,
    RUN_CAPABILITIES.disableHyperThreading
  ],
  windows: []
};

function getPlatformSpecificCapabilities (platform, preferences) {
  const capabilities = platform && OS_SPECIFIC_CAPABILITIES.hasOwnProperty(platform)
    ? OS_SPECIFIC_CAPABILITIES[platform]
    : OS_SPECIFIC_CAPABILITIES.default;
  const custom = preferences ? preferences.launchCapabilities : [];
  const filterCustomCapability = capability => (capability.platforms || []).length === 0 ||
    platform === undefined ||
    capability.platforms.indexOf(platform) >= 0;
  return capabilities.map(o => ({
    value: o,
    name: o
  }))
    .concat((custom || []).filter(filterCustomCapability));
}

@inject('preferences')
@observer
class RunCapabilities extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    values: PropTypes.arrayOf(PropTypes.string),
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
    const {disabled, values, preferences} = this.props;
    const capabilities = getPlatformSpecificCapabilities(undefined, preferences);
    const filteredValues = (values || [])
      .filter(value => capabilities.find(o => o.value === value));
    return (
      <Select
        allowClear
        disabled={disabled}
        mode="multiple"
        onChange={this.onSelectionChanged}
        placeholder="None selected"
        size="large"
        value={filteredValues || []}
        filterOption={
          (input, option) => option.props.children.toLowerCase().includes(input.toLowerCase())
        }
      >
        {
          capabilities
            .map(capability => (
              <Select.Option
                key={capability.value}
                value={capability.value}
                title={capability.description || capability.name}
              >
                {capability.name}
              </Select.Option>
            ))
        }
      </Select>
    );
  }
}

export function isCustomCapability (parameterName, preferences) {
  if (!preferences) {
    return false;
  }
  return !!preferences.launchCapabilities.find(o => o.value === parameterName);
}

export function getCustomCapability (parameterName, preferences) {
  if (!preferences) {
    return undefined;
  }
  return preferences.launchCapabilities.find(o => o.value === parameterName);
}

export function getRunCapabilitiesSkippedParameters () {
  return Object.values(RUN_CAPABILITIES_PARAMETERS);
}

export function capabilityEnabled (parameters, capability) {
  return booleanParameterIsSetToValue(parameters, RUN_CAPABILITIES_PARAMETERS[capability]);
}

export function getEnabledCapabilities (parameters) {
  const result = [];
  const predefinedCapabilities = Object
    .entries(RUN_CAPABILITIES_PARAMETERS)
    .map(([name, parameterName]) => ({name, parameterName}));
  Object
    .keys(parameters || {})
    .forEach((parameterName) => {
      if (booleanParameterIsSetToValue(parameters, parameterName)) {
        const predefined = predefinedCapabilities.find(o => o.parameterName === parameterName);
        if (predefined) {
          result.push(predefined.name);
        } else {
          result.push(parameterName);
        }
      }
    });
  return result;
}

export function addCapability (capabilities, ...capability) {
  const result = (capabilities || []).slice();
  capability.forEach(c => {
    if (!result.includes(c)) {
      result.push(c);
    }
  });
  return result;
}

export function applyCapabilities (parameters, capabilities = [], preferences, platform) {
  if (!parameters) {
    parameters = {};
  }
  const platformSpecificCapabilities = getPlatformSpecificCapabilities(platform, preferences);
  capabilities
    .map(capability => platformSpecificCapabilities.find(psc => psc.value === capability))
    .filter(Boolean)
    .forEach(capability => {
      const parameterName = capability.custom
        ? capability.value
        : RUN_CAPABILITIES_PARAMETERS[capability.value];
      parameters[parameterName] = {
        type: 'boolean',
        value: true
      };
    });
  return parameters;
}

export function applyCustomCapabilitiesParameters (parameters, preferences) {
  const customCapabilities = getEnabledCapabilities(parameters)
    .map(capability => getCustomCapability(capability, preferences))
    .filter(Boolean);
  const result = {...(parameters || {})};
  const getParameterType = value => {
    if (typeof value === 'boolean') {
      return 'boolean';
    }
    return 'string';
  };
  for (const customCapability of customCapabilities) {
    const customCapabilityParameters = Object.entries(customCapability.params || {});
    for (const [parameter, value] of customCapabilityParameters) {
      if (value !== undefined) {
        const type = getParameterType(value);
        result[parameter] = {
          type,
          value: type === 'boolean' ? value : `${value}`
        };
      }
    }
  }
  return result;
}

export function hasPlatformSpecificCapabilities (platform, preferences) {
  return getPlatformSpecificCapabilities(platform, preferences).length > 0;
}

export function checkRunCapabilitiesModified (capabilities1, capabilities2, preferences) {
  const wellKnownCapabilities = Object.values(RUN_CAPABILITIES)
    .concat(
      (preferences ? preferences.launchCapabilities : [])
        .map(o => o.value)
    );
  const sorted = array => [
    ...(
      new Set((array || []).sort().filter(o => wellKnownCapabilities.includes(o)))
    )
  ];
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

export function dinDEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_DIND_CONTAINER);
}

export function singularityEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SINGULARITY);
}

export function systemDEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SYSTEMD_CONTAINER);
}

export function noMachineEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_DESKTOP_NM);
}

export function moduleEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_MODULES);
}

export function disableHyperThreadingEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_DISABLE_HYPER_THREADING);
}

export default RunCapabilities;
