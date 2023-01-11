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
  CP_CAP_DCV_DESKTOP,
  CP_CAP_RUN_CAPABILITIES
} from './parameters';
import fetchToolOS from './fetch-tool-os';
import Capability from './capability';
import {mergeUserRoleAttributes} from '../../../../../utils/attributes/merge-user-role-attributes';
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

export const RUN_CAPABILITIES_MODE = {
  launch: 'launch',
  edit: 'edit'
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

// We can specify here disclaimers for predefined capabilities (DinD, NoMachine, etc.)
const PREDEFINED_CAPABILITIES_DISCLAIMERS = {
  [RUN_CAPABILITIES.dinD]: undefined,
  [RUN_CAPABILITIES.singularity]: undefined,
  [RUN_CAPABILITIES.systemD]: undefined,
  [RUN_CAPABILITIES.noMachine]: undefined,
  [RUN_CAPABILITIES.module]: undefined,
  [RUN_CAPABILITIES.disableHyperThreading]: undefined,
  [RUN_CAPABILITIES.dcv]: undefined
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
  const mapCapability = (capability, parent) => {
    const {
      capabilities = [],
      ...capabilityInfo
    } = capability;
    const enabledByOS = filterByOS(capability);
    const enabledByCloudProvider = filterByCloudProvider(capability);
    return {
      ...capabilityInfo,
      capabilities: capabilities.map(c => mapCapability(c, capability)),
      disabled: !enabledByOS || !enabledByCloudProvider,
      parentValue: parent?.value
    };
  };
  return capabilities.map(o => ({
    value: o,
    name: o,
    os: CAPABILITIES_OS_FILTERS[o],
    cloud: CAPABILITIES_CLOUD_FILTERS[o]
  }))
    .concat((custom || []).filter(filterCustomCapability))
    .map(capability => mapCapability(capability));
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
    onChange: null,
    initialRequiredCapabilities: []
  };

  state = {
    os: undefined
  };

  componentDidMount () {
    this.fetchDockerImageOS();
    this.setInitialRequiredCapabilities();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.dockerImage !== this.props.dockerImage ||
      prevProps.dockerImageOS !== this.props.dockerImageOS
    ) {
      this.fetchDockerImageOS();
      this.setInitialRequiredCapabilities();
    } else if (prevProps.platform !== this.props.platform) {
      this.correctCapabilitiesSelection();
      this.setInitialRequiredCapabilities();
    }
  }

  setInitialRequiredCapabilities = () => {
    const {values} = this.props;
    this.setState({
      initialRequiredCapabilities: (values || [])
        .filter(this.capabilityIsRequired)
    });
  };

  get allCapabilities () {
    const {
      platform,
      provider,
      preferences
    } = this.props;
    const {
      os
    } = this.state;
    return getAllPlatformCapabilities(preferences, {platform, os, provider});
  }

  get filteredValues () {
    const {
      platform,
      provider,
      preferences,
      values
    } = this.props;
    const {
      os
    } = this.state;
    const capabilities = getPlatformSpecificCapabilities(
      preferences,
      {platform, os, provider}
    );
    return (values || [])
      .filter(value => capabilities.find(o => o.value === value));
  }

  get hasErrors () {
    const {
      values,
      preferences,
      mode
    } = this.props;
    return checkRequiredCapabilitiesErrors(values, preferences) &&
    mode === RUN_CAPABILITIES_MODE.launch;
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

  getCapabilityByValue = (value) => {
    return plainList(this.allCapabilities)
      .find(capability => capability.value === value);
  };

  capabilityIsRequired = (value) => {
    const current = this.getCapabilityByValue(value);
    if (current) {
      return current.custom &&
        (current.capabilities || []).length > 0;
    }
    return false;
  };

  renderRequiredCapabilities = () => {
    const {mode} = this.props;
    const getCapabilityName = value => {
      const current = this.getCapabilityByValue(value) || {};
      return current.name || value;
    };
    return [
      this.filteredValues
        .filter(this.capabilityIsRequired)
        .map(value => (
          <div
            key={value}
            className={
              classNames(
                styles.runCapabilitiesInputTag,
                'cp-run-capabilities-input-tag',
                {'required': mode === RUN_CAPABILITIES_MODE.launch},
                {'tag-placeholder': mode === RUN_CAPABILITIES_MODE.edit}
              )
            }
          >
            {getCapabilityName(value)}
          </div>
        ))
    ];
  };

  render () {
    const {
      disabled,
      className,
      style,
      mode
    } = this.props;
    const {initialRequiredCapabilities} = this.state;
    const toggleValue = (value) => {
      const current = this.getCapabilityByValue(value);
      const parent = this.getCapabilityByValue(current.parentValue);
      const isRequired = this.capabilityIsRequired(value);
      const isSelected = this.filteredValues.includes(value);
      if (disabled || (mode === RUN_CAPABILITIES_MODE.launch && isRequired)) {
        return;
      }
      const correctSelection = (selection) => {
        if (parent) {
          const parentIsRequired = initialRequiredCapabilities
            .includes(parent.value);
          const siblings = parent.capabilities
            .filter(capability => capability.value !== current.value)
            .map(capability => capability.value);
          const newSelection = parent.multiple
            ? selection.filter(value => value !== current.parentValue)
            : selection.filter(value => value !== current.parentValue &&
              !siblings.includes(value));
          const siblingsHasSelection = newSelection
            .some(value => siblings.includes(value));
          if (isSelected && !siblingsHasSelection && parentIsRequired) {
            newSelection.push(current.parentValue);
          }
          return newSelection;
        }
        if (
          isRequired &&
          mode === RUN_CAPABILITIES_MODE.edit
        ) {
          const childValues = (current.capabilities || [])
            .map(capability => capability.value);
          return selection.filter(value => !childValues.includes(value));
        }
        return selection;
      };
      let capabilities = correctSelection([...this.filteredValues]);
      if (isSelected) {
        capabilities = capabilities.filter(v => v !== value);
      } else {
        capabilities.push(value);
      }
      this.onSelectionChanged(capabilities);
    };
    const onCapabilityClick = ({domEvent, key}) => {
      if (disabled) {
        return;
      }
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
              selected={this.filteredValues.includes(capability.value)}
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
              selected={this.filteredValues.includes(capability.value)}
              style={{width: '100%', paddingRight: 20}}
              nested={
                capabilities
                  .filter(child => this.filteredValues.includes(child.value))
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
      <div
        className={className}
      >
        <Dropdown
          overlay={(
            <div>
              <Menu
                mode="vertical"
                selectedKeys={[]}
                onClick={onCapabilityClick}
              >
                {this.allCapabilities.map(renderCapability)}
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
                  'cp-error': this.hasErrors,
                  [styles.disabled]: disabled
                }
              )
            }
            style={style}
          >
            {
              this.filteredValues.length === 0 && '\u00A0'
            }
            {
              plainList(this.allCapabilities)
                .filter(o => this.filteredValues.includes(o.value) &&
                  !this.capabilityIsRequired(o.value)
                )
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
            {this.renderRequiredCapabilities()}
          </div>
        </Dropdown>
        {this.hasErrors ? (
          <div className={classNames(styles.errorText, 'cp-error')}>
            Some of the required values are missing
          </div>
        ) : null}
        <CapabilitiesDisclaimer
          values={this.filteredValues}
          style={{marginTop: 5}}
        />
      </div>
    );
  }
}

export class RunCapabilitiesMetadataPreference extends React.Component {
  static propTypes = {
    readOnly: PropTypes.bool,
    metadata: PropTypes.shape({value: PropTypes.string}),
    onChange: PropTypes.func
  };

  state = {
    disabled: false
  };

  get values () {
    const {metadata = {}} = this.props;
    const {value} = metadata;
    if (value && typeof value === 'string') {
      return value.split(',');
    }
    return [];
  }

  onChange = (values = []) => {
    const {onChange} = this.props;
    if (typeof onChange !== 'function') {
      return;
    }
    this.setState({
      disabled: true
    }, () => {
      const result = onChange(values.join(','));
      if (result && typeof result.then === 'function') {
        result
          .catch(() => {})
          .then(() => this.setState({disabled: false}));
      } else {
        this.setState({disabled: false});
      }
    });
  };

  render () {
    const {readOnly} = this.props;
    const {disabled} = this.state;
    return (
      <div className={styles.runCapabilitiesMetadataContainer}>
        <div
          className="cp-text"
          style={{fontWeight: 'bold', marginRight: 5}}
        >
          Run capabilities:
        </div>
        <RunCapabilities
          disabled={readOnly || disabled}
          values={this.values}
          onChange={this.onChange}
          className={styles.runCapabilitiesMetadataInput}
          style={{minHeight: '28px', lineHeight: '28px'}}
        />
      </div>
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

/**
 * @returns {Promise<string[]>}
 */
export async function getUserCapabilities () {
  const capabilities = await mergeUserRoleAttributes(CP_CAP_RUN_CAPABILITIES);
  return (capabilities || '')
    .split(/[,;]/)
    .map((o) => o.trim())
    .filter((o) => o.length);
}

export async function applyUserCapabilities (parameters, preferences, platform) {
  const userCapabilities = await getUserCapabilities();
  return applyCapabilities(
    {...(parameters || {})},
    userCapabilities,
    preferences,
    platform
  );
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

export function checkRequiredCapabilitiesErrors (values = [], preferences) {
  const checkMissedDependency = (capability) => {
    return !capability.capabilities.some(({value}) => values.includes(value));
  };
  const requiredCapabilities = getAllPlatformCapabilities(preferences)
    .filter(capability => values.includes(capability.value) &&
      capability.capabilities.length > 0 &&
      capability.custom
    );
  return requiredCapabilities.some(checkMissedDependency);
}

function getDisclaimersList (options = {}) {
  const {
    values,
    preferences,
    parameters
  } = options;
  if (!preferences) {
    return [];
  }
  if ((!values || !values.length) && !parameters) {
    return [];
  }
  const capabilityNames = values && values.length
    ? values
    : getEnabledCapabilities(parameters || {});
  const capabilities = getAllPlatformCapabilities(preferences);
  const disclaimers = [];
  for (let i = 0; i < capabilityNames.length; i++) {
    const value = capabilityNames[i];
    if (PREDEFINED_CAPABILITIES_DISCLAIMERS[value]) {
      disclaimers.push(PREDEFINED_CAPABILITIES_DISCLAIMERS[value]);
    }
    const capability = capabilities.find(o => o.value === value);
    if (capability && capability.disclaimer) {
      disclaimers.push(capability.disclaimer);
    }
  }
  return disclaimers.filter(Boolean);
}

function CapabilitiesDisclaimerRenderer (
  {
    values,
    preferences,
    parameters,
    className,
    style,
    showIcon
  }
) {
  const filteredValuesDisclaimers = getDisclaimersList({values, parameters, preferences});
  return (
    filteredValuesDisclaimers.length
      ? (
        <Alert
          showIcon={showIcon}
          type="warning"
          className={className}
          style={style}
          message={
            <div>
              {filteredValuesDisclaimers.map((disclaimer, idx) => (
                <p
                  key={`disclaimer-${idx}`}
                  style={{marginBottom: 2}}
                >
                  {disclaimer}
                </p>
              ))}
            </div>
          }
        />
      )
      : null
  );
}

const CapabilitiesDisclaimer = inject('preferences')(observer(CapabilitiesDisclaimerRenderer));
CapabilitiesDisclaimer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  values: PropTypes.oneOfType([PropTypes.object, PropTypes.arrayOf(PropTypes.string)]),
  showIcon: PropTypes.bool
};

export {CapabilitiesDisclaimer};
export {CP_CAP_RUN_CAPABILITIES as METADATA_KEY};
export default RunCapabilities;
