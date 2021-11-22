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
import fetchToolOS from './fetch-tool-os';

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

const PLATFORM_SPECIFIC_CAPABILITIES = {
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

const CAPABILITIES_OS_FILTERS = {
  [RUN_CAPABILITIES.systemD]: [/^centos.*$/]
};

const CAPABILITIES_CLOUD_FILTERS = {
};

function getPlatformSpecificCapabilities (preferences, platformInfo = {}) {
  const {
    platform,
    os,
    provider
  } = platformInfo;
  const capabilities = platform && PLATFORM_SPECIFIC_CAPABILITIES.hasOwnProperty(platform)
    ? PLATFORM_SPECIFIC_CAPABILITIES[platform]
    : PLATFORM_SPECIFIC_CAPABILITIES.default;
  const custom = preferences ? preferences.launchCapabilities : [];
  const filterCustomCapability = capability => (capability.platforms || []).length === 0 ||
    platform === undefined ||
    !capability.platforms ||
    capability.platforms.length === 0 ||
    capability.platforms.indexOf(platform) >= 0;
  const filterByOS = capability => os === undefined ||
    !capability.os ||
    capability.os.length === 0 ||
    capability.os.some(osRegExp => osRegExp.test(os));
  const filterByCloudProvider = capability => provider === undefined ||
    !capability.cloud ||
    capability.cloud.length === 0 ||
    capability.cloud.some(p => p.toLowerCase() === provider.toLowerCase());
  return capabilities.map(o => ({
    value: o,
    name: o,
    os: CAPABILITIES_OS_FILTERS[o] || [/.*/],
    cloud: CAPABILITIES_CLOUD_FILTERS[o]
  }))
    .concat((custom || []).filter(filterCustomCapability))
    .filter(filterByOS)
    .filter(filterByCloudProvider);
}

@inject('preferences', 'dockerRegistries')
@observer
class RunCapabilities extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    values: PropTypes.arrayOf(PropTypes.string),
    platform: PropTypes.string,
    onChange: PropTypes.func,
    dockerImage: PropTypes.string,
    dockerImageOS: PropTypes.string,
    provider: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
  };

  static defaultProps = {
    values: null,
    onChange: null
  };

  state = {
    os: undefined
  };

  componentDidMount () {
    this.fetchDockerImageOS();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.dockerImage !== this.props.dockerImage ||
      prevProps.dockerImageOS !== this.props.dockerImageOS
    ) {
      this.fetchDockerImageOS();
    } else if (prevProps.platform !== this.props.platform) {
      this.correctCapabilitiesSelection();
    }
  }

  fetchDockerImageOS () {
    const {
      dockerImageOS,
      dockerImage,
      dockerRegistries
    } = this.props;
    if (dockerImageOS) {
      this.setState({
        os: dockerImageOS
      }, this.correctCapabilitiesSelection);
    } else if (dockerImage) {
      fetchToolOS(dockerImage, dockerRegistries)
        .then(os => this.setState({os}, this.correctCapabilitiesSelection));
    } else {
      this.setState({
        os: undefined
      }, this.correctCapabilitiesSelection);
    }
  }

  correctCapabilitiesSelection = () => {
    this.onSelectionChanged(this.props.values);
  }

  onSelectionChanged = (values = []) => {
    const {
      platform,
      provider,
      preferences,
      onChange
    } = this.props;
    const {os} = this.state;
    const capabilities = getPlatformSpecificCapabilities(
      preferences,
      {platform, os, provider}
    );
    const filtered = values.filter(v => capabilities.find(o => o.value === v));
    onChange && onChange(filtered);
  };

  render () {
    const {
      disabled,
      values,
      platform,
      provider,
      preferences
    } = this.props;
    const {os} = this.state;
    const capabilities = getPlatformSpecificCapabilities(
      preferences,
      {platform, os, provider}
    );
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
  const platformSpecificCapabilities = getPlatformSpecificCapabilities(preferences, {platform});
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
  return getPlatformSpecificCapabilities(preferences, {platform}).length > 0;
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

export default RunCapabilities;
