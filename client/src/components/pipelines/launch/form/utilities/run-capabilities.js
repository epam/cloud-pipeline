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
import fetchToolDefaultParameters from './fetch-tool-default-parameters';
import {RUN_CAPABILITIES} from '../../../../../models/preferences/PreferencesLoad';
import styles from './run-capabilities.css';
import parseCapabilityCloudSetting from './capabilities-utilities/parse-cloud-setting';

export {RUN_CAPABILITIES};

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
  [RUN_CAPABILITIES.systemD]: ['centos*', 'rocky*'],
  [RUN_CAPABILITIES.dcv]: [
    'centos 7*',
    'rocky*',
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
    provider,
    region
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
  const cloudMatches = (capabilityCloud) => {
    const {
      cloud: capabilityProvider,
      regionIdentifier: capabilityRegionName,
      regionId: capabilityRegionId
    } = parseCapabilityCloudSetting(capabilityCloud);
    return (
      !capabilityProvider ||
      !provider ||
      capabilityProvider.toLowerCase() === provider.toLowerCase()
    ) && (
      !capabilityRegionName ||
      !region ||
      capabilityRegionName.toLowerCase() === region.regionId.toLowerCase()
    ) && (
      !capabilityRegionId ||
      !region ||
      capabilityRegionId === region.id
    );
  };
  const filterByCloudProvider = capability => !capability.cloud ||
    capability.cloud.length === 0 ||
    capability.cloud.some(cloudMatches);
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

function getAllRequiredCapabilities (preferences) {
  return getAllPlatformCapabilities(preferences)
    .filter(capability => (capability.capabilities || []).length > 0);
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
    tagStyle: PropTypes.object,
    disabled: PropTypes.bool,
    values: PropTypes.arrayOf(PropTypes.string),
    onChange: PropTypes.func,
    dockerImage: PropTypes.string,
    dockerImageOS: PropTypes.string,
    provider: PropTypes.string,
    region: PropTypes.object,
    mode: PropTypes.string,
    showError: PropTypes.bool,
    getPopupContainer: PropTypes.func
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
    }
    if (this.props.values && !prevProps.values) {
      this.setInitialRequiredCapabilities();
    }
  }

  setInitialRequiredCapabilities = () => {
    const token = this.token = (this.token || 0) + 1;
    this.props.preferences
      .fetchIfNeededOrWait()
      .then(() => fetchToolDefaultParameters(
        this.props.dockerImage,
        this.props.dockerRegistries
      ))
      .then((parameters) => {
        const enabled = getEnabledCapabilities(parameters || {});
        const required = getAllRequiredCapabilities(this.props.preferences);
        const initialRequired = required
          .filter((capability) => enabled.includes(capability.value))
          .map((capability) => capability.value);
        if (token === this.token) {
          this.setState({
            initialRequiredCapabilities: initialRequired
          });
        }
      });
  };

  get allCapabilities () {
    const {
      provider,
      region,
      preferences
    } = this.props;
    const {
      os
    } = this.state;
    return getAllPlatformCapabilities(
      preferences,
      {
        os,
        provider,
        region
      }
    );
  }

  get filteredValues () {
    const {
      provider,
      region,
      preferences,
      values
    } = this.props;
    const {
      os
    } = this.state;
    const capabilities = getPlatformSpecificCapabilities(
      preferences,
      {
        os,
        provider,
        region
      }
    );
    return (values || [])
      .filter(value => capabilities.find(o => o.value === value));
  }

  get hasErrors () {
    const {
      values,
      preferences,
      mode,
      showError = true
    } = this.props;
    return checkRequiredCapabilitiesErrors(values, preferences) &&
    mode === RUN_CAPABILITIES_MODE.launch &&
    showError;
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

  onSelectionChanged = (values) => {
    const {
      platform,
      provider,
      region,
      preferences,
      onChange
    } = this.props;
    const {os} = this.state;
    const capabilities = getPlatformSpecificCapabilities(
      preferences,
      {
        platform,
        os,
        provider,
        region
      }
    );
    const filtered = (values || [])
      .filter(v => capabilities.find(o => o.value === v));
    onChange && onChange(filtered);
  };

  getCapabilityByValue = (value) => {
    return plainList(this.allCapabilities)
      .find(capability => capability.value === value);
  };

  capabilityIsRequired = (value) => {
    const current = this.getCapabilityByValue(value);
    if (current) {
      return (current.capabilities || []).length > 0;
    }
    return false;
  };

  renderRequiredCapabilities = () => {
    const {mode, tagStyle} = this.props;
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
            style={tagStyle}
          >
            {getCapabilityName(value)}
          </div>
        ))
    ];
  };

  toggleValue = (value) => {
    const {
      disabled,
      mode
    } = this.props;
    const {initialRequiredCapabilities} = this.state;
    const current = this.getCapabilityByValue(value);
    const parent = this.getCapabilityByValue(current.parentValue);
    const isRequired = this.capabilityIsRequired(value);
    const isSelected = !this.filteredValues.includes(value);
    // if component is disabled OR
    // we are trying to select / deselect parent capability (required) -
    // do nothing
    if (disabled || (mode === RUN_CAPABILITIES_MODE.launch && isRequired)) {
      return;
    }
    // first, select or deselect current value:
    let newSelection = (this.filteredValues || [])
      .filter((existing) => existing !== value);
    if (isSelected) {
      // add capability
      newSelection.push(value);
    }
    // If it is "parent" / "child", we need to handle special cases:
    // a) if we select parent capability (isRequired & isSelected)
    // b) if we de-select parent capability -
    //    do nothing, because we're in the EDIT mode or `value` is not a parent:
    //    (NOT (launch AND isRequired))
    // c) if we select child capability (parent is not null AND isSelected)
    // d) if we deselect child capability at launch mode - if none of child capabilities
    //    is selected, we should select parent capability (if it was selected INITIALLY)
    //    (parent is not null AND !isSelected AND launch)
    if (isRequired && isSelected) {
      // this is "parent" capability;
      // we should remove all child capabilities from selection
      const siblings = (current.capabilities || [])
        .map(capability => capability.value);
      // this is "parent" capability
      newSelection = newSelection.filter((existing) => !siblings.includes(existing));
    } else if (parent && isSelected) {
      // we selected child capability.
      // first, remove parent capability:
      newSelection = newSelection.filter((existing) => existing !== parent.value);
      // then, if it is not a multiple mode, remove other child capabilities:
      if (!parent.multiple) {
        const siblings = (parent.capabilities || [])
          .map(capability => capability.value);
        newSelection = newSelection
          .filter((existing) => existing === value || !siblings.includes(existing));
      }
    } else if (parent && !isSelected && mode === RUN_CAPABILITIES_MODE.launch) {
      // we de-selected child capability at launch mode;
      // if none of the child capabilities is selected - we need to select parent one
      const siblings = (parent.capabilities || [])
        .map(capability => capability.value);
      const initialSelection = !!initialRequiredCapabilities
        .find((initial) => initial === parent.value);
      if (
        initialSelection &&
        !newSelection.some((existing) => siblings.includes(existing)) &&
        !newSelection.includes(parent.value)
      ) {
        newSelection.push(parent.value);
      }
    }
    this.onSelectionChanged(newSelection);
  };

  render () {
    const {
      disabled,
      className,
      style,
      tagStyle,
      mode,
      getPopupContainer = () => document.body
    } = this.props;
    const onCapabilityClick = ({domEvent, key}) => {
      if (disabled) {
        return;
      }
      domEvent.stopPropagation();
      domEvent.preventDefault();
      this.toggleValue(key);
    };
    const renderCapability = (capability) => {
      const {
        capabilities = []
      } = capability;
      const selectable = this.capabilityIsRequired(capability.value)
        ? mode === RUN_CAPABILITIES_MODE.edit
        : true;
      if (
        !capability.custom &&
        this.props.preferences.hiddenRunCapabilities.includes(capability.value)
      ) {
        return null;
      }
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
              selected={
                selectable &&
                this.filteredValues.includes(capability.value)
              }
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
                getPopupContainer={getPopupContainer}
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
                    style={tagStyle}
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
      const current = new Set(this.values);
      const newValues = new Set(values);
      let changed = true;
      if (current.size === newValues.size) {
        changed = false;
        for (const aValue of current) {
          if (!newValues.has(aValue)) {
            changed = true;
          }
        }
      }
      if (!changed) {
        this.setState({disabled: false});
        return;
      }
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
          mode={RUN_CAPABILITIES_MODE.launch}
          disabled={readOnly || disabled}
          values={this.values}
          onChange={this.onChange}
          className={styles.runCapabilitiesMetadataInput}
          style={{minHeight: '28px', lineHeight: '28px'}}
          getPopupContainer={node => node}
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

function correctParametersForRequiredCapabilities (parameters, preferences) {
  const result = {...(parameters || {})};
  const enabled = getEnabledCapabilities(parameters);
  const required = getAllRequiredCapabilities(preferences);
  const removeCapability = (capability) => {
    if (result[capability]) {
      delete result[capability];
    }
  };
  enabled.forEach((capability) => {
    const parent = required
      .find((r) => (r.capabilities || []).map(o => o.value).includes(capability));
    if (parent && enabled.includes(parent.value)) {
      removeCapability(parent.value);
    }
  });
  return result;
}

export function correctRequiredCapabilities (capabilities, preferences) {
  const result = (capabilities || []).slice();
  const required = getAllRequiredCapabilities(preferences);
  const removeCapability = (capability) => {
    const idx = result.indexOf(capability);
    if (idx >= 0) {
      result.splice(idx, 1);
    }
  };
  capabilities.forEach((capability) => {
    const parent = required
      .find((r) => (r.capabilities || []).map(o => o.value).includes(capability));
    if (parent && capabilities.includes(parent.value)) {
      removeCapability(parent.value);
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
  return correctParametersForRequiredCapabilities(parameters, preferences);
}

export function updateCapabilities (
  parameters = {},
  capabilities = [],
  preferences,
  platform
) {
  const clearCapabilities = (parameters) => {
    return Object.entries(parameters)
      .filter(([key]) => !isCapability(key, preferences))
      .map(([key, value]) => ({[key]: value}))
      .reduce((r, c) => ({...r, ...c}), {});
  };
  parameters = clearCapabilities(parameters);
  return applyCapabilities(parameters, capabilities, preferences, platform);
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

export function checkRequiredCapabilitiesErrors (values, preferences) {
  const checkMissedDependency = (capability) => {
    return !capability.capabilities.some(({value}) => values.includes(value));
  };
  const requiredCapabilities = getAllPlatformCapabilities(preferences)
    .filter(capability => (values || []).includes(capability.value) &&
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
