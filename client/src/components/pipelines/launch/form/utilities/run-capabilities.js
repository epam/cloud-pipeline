import React from 'react';
import PropTypes from 'prop-types';
import {Icon, Alert} from 'antd';
import Dropdown from 'rc-dropdown';
import Menu, {MenuItem, SubMenu} from 'rc-menu';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {booleanParameterIsSetToValue} from './parameter-utilities';
import {
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_SINGULARITY,
  CP_CAP_DESKTOP_NM,
  CP_CAP_MODULES,
  CP_DISABLE_HYPER_THREADING,
  CP_CAP_DCV,
  CP_CAP_DCV_WEB,
  CP_CAP_DCV_DESKTOP
} from './parameters';
import fetchToolOS from './fetch-tool-os';
import Capability from './capability';
import styles from './run-capabilities.css';

export const RUN_CAPABILITIES = {
  dinD: 'DinD',
  singularity: 'Singularity',
  systemD: 'SystemD',
  noMachine: 'NoMachine',
  module: 'Module',
  disableHyperThreading: 'Disable Hyper-Threading',
  dcv: 'NICE DCV'
};

export const RUN_CAPABILITIES_PARAMETERS = {
  [RUN_CAPABILITIES.dinD]: CP_CAP_DIND_CONTAINER,
  [RUN_CAPABILITIES.singularity]: CP_CAP_SINGULARITY,
  [RUN_CAPABILITIES.systemD]: CP_CAP_SYSTEMD_CONTAINER,
  [RUN_CAPABILITIES.noMachine]: CP_CAP_DESKTOP_NM,
  [RUN_CAPABILITIES.module]: CP_CAP_MODULES,
  [RUN_CAPABILITIES.disableHyperThreading]: CP_DISABLE_HYPER_THREADING,
  [RUN_CAPABILITIES.dcv]: CP_CAP_DCV
};

const CAPABILITIES_DEPENDENCIES = {
  [RUN_CAPABILITIES.dcv]: [CP_CAP_DCV_DESKTOP, CP_CAP_DCV_WEB, CP_CAP_SYSTEMD_CONTAINER]
};

const PLATFORM_SPECIFIC_CAPABILITIES = {
  default: [
    RUN_CAPABILITIES.dinD,
    RUN_CAPABILITIES.singularity,
    RUN_CAPABILITIES.systemD,
    RUN_CAPABILITIES.dcv,
    RUN_CAPABILITIES.noMachine,
    RUN_CAPABILITIES.module,
    RUN_CAPABILITIES.disableHyperThreading
  ],
  windows: []
};

const CAPABILITIES_OS_FILTERS = {
  [RUN_CAPABILITIES.systemD]: ['centos*'],
  [RUN_CAPABILITIES.dcv]: [
    'centos 7*',
    'ubuntu 18.04',
    'ubuntu 20.04'
  ]
};

const CAPABILITIES_CLOUD_FILTERS = {
  [RUN_CAPABILITIES.dcv]: ['aws']
};

function parseOSMask (mask) {
  if (/^all$/i.test(mask)) {
    return /.*/;
  }
  const regExpValue = mask
    .trim()
    .replace(/\./g, '\\.')
    .replace(/\*/g, '.*');
  return new RegExp(`^${regExpValue}$`, 'i');
}

function getAllPlatformCapabilities (preferences, platformInfo = {}) {
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
    capability.os.some(capabilityOS => parseOSMask(capabilityOS).test(os));
  const filterByCloudProvider = capability => provider === undefined ||
    !capability.cloud ||
    capability.cloud.length === 0 ||
    capability.cloud.some(p => p.toLowerCase() === provider.toLowerCase());
  const mapCapability = (capability) => {
    const {
      capabilities = [],
      ...capabilityInfo
    } = capability;
    const enabledByOS = filterByOS(capability);
    const enabledByCloudProvider = filterByCloudProvider(capability);
    return {
      ...capabilityInfo,
      capabilities: capabilities.map(mapCapability),
      disabled: !enabledByOS || !enabledByCloudProvider
    };
  };
  return capabilities.map(o => ({
    value: o,
    name: o,
    os: CAPABILITIES_OS_FILTERS[o],
    cloud: CAPABILITIES_CLOUD_FILTERS[o]
  }))
    .concat((custom || []).filter(filterCustomCapability))
    .map(mapCapability);
}

function plainList (capabilities = []) {
  const getPlain = (capability) => {
    const {capabilities: nested = []} = capability;
    return [capability, ...nested.map(getPlain).reduce((r, c) => ([...r, ...c]), [])];
  };
  return (capabilities || [])
    .map(getPlain)
    .reduce((r, c) => ([...r, ...c]), []);
}

function getPlatformSpecificCapabilities (preferences, platformInfo = {}) {
  return plainList(
    getAllPlatformCapabilities(preferences, platformInfo)
      .filter(capability => !capability.disabled)
  );
}

@inject('preferences', 'dockerRegistries')
@observer
class RunCapabilities extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    disabled: PropTypes.bool,
    values: PropTypes.oneOfType([PropTypes.object, PropTypes.arrayOf(PropTypes.string)]),
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
      preferences,
      className,
      style
    } = this.props;
    const {os} = this.state;
    const capabilities = getPlatformSpecificCapabilities(
      preferences,
      {platform, os, provider}
    );
    const all = getAllPlatformCapabilities(preferences, {platform, os, provider});
    const filteredValues = (values || [])
      .filter(value => capabilities.find(o => o.value === value));
    const toggleValue = (value) => {
      const selected = [...filteredValues];
      const index = selected.indexOf(value);
      if (index >= 0) {
        selected.splice(index, 1);
      } else {
        selected.push(value);
      }
      this.onSelectionChanged(selected);
    };
    const onCapabilityClick = ({domEvent, key}) => {
      domEvent.stopPropagation();
      domEvent.preventDefault();
      toggleValue(key);
    };
    const renderCapability = (capability) => {
      const {
        capabilities = []
      } = capability;
      if (capabilities.length === 0) {
        return (
          <MenuItem
            key={capability.value}
            value={capability.value}
            title={capability.description || capability.name}
            disabled={capability.disabled}
          >
            <Capability
              capability={capability}
              selected={filteredValues.includes(capability.value)}
              style={{width: '100%'}}
            />
          </MenuItem>
        );
      }
      return (
        <SubMenu
          key={capability.value}
          value={capability.value}
          title={(
            <Capability
              capability={capability}
              selected={filteredValues.includes(capability.value)}
              style={{width: '100%', paddingRight: 20}}
              nested={
                capabilities
                  .filter(child => filteredValues.includes(child.value))
                  .map(child => child.value)
              }
            />
          )}
          disabled={capability.disabled}
          onTitleClick={onCapabilityClick}
          onClick={onCapabilityClick}
        >
          {
            capabilities.map(renderCapability)
          }
        </SubMenu>
      );
    };
    return (
      <Dropdown
        overlay={(
          <div>
            <Menu
              mode="vertical"
              selectedKeys={[]}
              onClick={onCapabilityClick}
            >
              {all.map(renderCapability)}
            </Menu>
          </div>
        )}
        trigger={['click']}
      >
        <div
          tabIndex={0}
          className={
            classNames(
              styles.runCapabilitiesInput,
              'cp-run-capabilities-input',
              {
                disabled,
                [styles.disabled]: disabled
              },
              className
            )
          }
          style={style}
        >
          {
            filteredValues.length === 0 && '\u00A0'
          }
          {
            plainList(all)
              .filter(capability => filteredValues.includes(capability.value))
              .map((capability) => (
                <div
                  key={capability.value}
                  className={
                    classNames(
                      styles.runCapabilitiesInputTag,
                      'cp-run-capabilities-input-tag'
                    )
                  }
                >
                  <Capability capability={capability} />
                  <Icon
                    type="close"
                    className={styles.runCapabilitiesInputTagClose}
                    onClick={(domEvent) => onCapabilityClick({domEvent, key: capability.value})}
                  />
                </div>
              ))
          }
        </div>
      </Dropdown>
    );
  }
}

export function isCapability (parameterName, preferences) {
  if (Object.values(RUN_CAPABILITIES_PARAMETERS).includes(parameterName)) {
    return true;
  }
  if (!preferences) {
    return false;
  }
  return !!plainList(preferences.launchCapabilities)
    .find(o => o.value === parameterName);
}

export function isCustomCapability (parameterName, preferences) {
  if (!preferences) {
    return false;
  }
  return !!plainList(preferences.launchCapabilities).find(o => o.value === parameterName);
}

export function getCustomCapability (parameterName, preferences) {
  if (!preferences) {
    return undefined;
  }
  return plainList(preferences.launchCapabilities).find(o => o.value === parameterName);
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
  const enable = (parameter) => {
    parameters[parameter] = {
      type: 'boolean',
      value: true
    };
  };
  const platformSpecificCapabilities = getPlatformSpecificCapabilities(preferences, {platform});
  capabilities
    .map(capability => platformSpecificCapabilities.find(psc => psc.value === capability))
    .filter(Boolean)
    .forEach(capability => {
      const parameterName = capability.custom
        ? capability.value
        : RUN_CAPABILITIES_PARAMETERS[capability.value];
      enable(parameterName);
      const dependencies = capability.custom
        ? []
        : (CAPABILITIES_DEPENDENCIES[capability.value] || []);
      dependencies.forEach(dependency => enable(dependency));
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
  const parseStringValues = (string) => `${string || ''}`
    .split(',')
    .map(o => o.trim())
    .filter(o => o.length);
  const mergeValues = (newValue, previousValue) => {
    const values = [...(new Set([
      ...parseStringValues(newValue),
      ...parseStringValues(previousValue)
    ]))];
    return values.join(',');
  };
  for (const customCapability of customCapabilities) {
    const customCapabilityParameters = Object.entries(customCapability.params || {});
    for (const [parameter, value] of customCapabilityParameters) {
      if (value !== undefined) {
        const type = getParameterType(value);
        const previous = result[parameter] ? result[parameter].value : undefined;
        result[parameter] = {
          type,
          value: type === 'boolean'
            ? value
            : mergeValues(`${value}`, previous)
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
      (preferences ? plainList(preferences.launchCapabilities) : [])
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

export function hasCapabilityDisclaimers (values, preferences) {
  const disclaimers = getDisclaimersList(values, preferences);
  return disclaimers.length > 0;
}

function getDisclaimersList (values, preferences) {
  const capabilities = getAllPlatformCapabilities(preferences);
  const filteredValuesDisclaimers = [];
  for (let i = 0; i < values.length; i++) {
    const value = values[i];
    const disclaimer = capabilities.find(o => o.value === value).disclaimer;
    if (disclaimer) {
      filteredValuesDisclaimers.push(disclaimer);
    }
  }
  return filteredValuesDisclaimers;
}

@inject('preferences')
@observer
export class CapabilitiesDisclaimer extends React.Component {
  static propTypes = {
    values: PropTypes.oneOfType([PropTypes.object, PropTypes.arrayOf(PropTypes.string)])
  };

  static defaultProps = {
    values: null
  };

  render () {
    const {
      values,
      preferences,
      parameters
    } = this.props;
    const filteredValuesDisclaimers = getDisclaimersList(values, preferences);
    return (
      filteredValuesDisclaimers.length
        ? (
          <Alert
            type="warning"
            message={
              <div>
                {filteredValuesDisclaimers.map(disclaimer => (
                  <p key={disclaimer}>{disclaimer}</p>
                ))}
              </div>
            }
          />
        )
        : null
    );
  }
}

export default RunCapabilities;
