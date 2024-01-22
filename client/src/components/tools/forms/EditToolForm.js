/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import PropTypes from 'prop-types';
import {
  Button,
  Checkbox,
  Col,
  Collapse,
  Form,
  Icon,
  Input,
  Modal,
  Row,
  Select,
  Tag
} from 'antd';
import classNames from 'classnames';

import ToolEndpointsFormItem from '../elements/ToolEndpointsFormItem';
import KubeLabels, {
  kubeLabelsHasChanges,
  prepareKubeLabelsPayload
} from '../elements/KubeLabels';
import CodeEditor from '../../special/CodeEditor';
import {getSpotTypeName} from '../../special/spot-instance-names';
import EditToolFormParameters from './EditToolFormParameters';
import styles from '../Tools.css';
import {names} from '../../../models/utils/ContextualPreference';
import {
  LimitMountsInput
} from '../../pipelines/launch/form/LimitMountsInput';
import {
  autoScaledClusterEnabled,
  hybridAutoScaledClusterEnabled,
  ConfigureClusterDialog,
  getSkippedSystemParametersList,
  getSystemParameterDisabledState,
  gridEngineEnabled,
  sparkEnabled,
  slurmEnabled,
  kubeEnabled,
  getAutoScaledPriceTypeValue,
  applyChildNodeInstanceParametersAsArray,
  parseChildNodeInstanceConfiguration
} from '../../pipelines/launch/form/utilities/launch-cluster';
import {
  CP_CAP_LIMIT_MOUNTS,
  CP_CAP_SGE,
  CP_CAP_SPARK,
  CP_CAP_SLURM,
  CP_CAP_KUBE,
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS,
  CP_CAP_AUTOSCALE_HYBRID,
  CP_CAP_AUTOSCALE_PRICE_TYPE,
  CP_CAP_RESCHEDULE_RUN
} from '../../pipelines/launch/form/utilities/parameters';
import AWSRegionTag from '../../special/AWSRegionTag';
import RunCapabilities, {
  RUN_CAPABILITIES,
  RUN_CAPABILITIES_MODE,
  getEnabledCapabilities,
  applyCapabilities,
  checkRunCapabilitiesModified,
  addCapability,
  hasPlatformSpecificCapabilities,
  isCustomCapability
} from '../../pipelines/launch/form/utilities/run-capabilities';
import {
  applyParametersArray,
  configurationChanged,
  getSkippedParameters as getGPUScalingSkippedParameters,
  readGPUScalingPreference
} from '../../pipelines/launch/form/utilities/enable-gpu-scaling';
import JobNotifications from '../../pipelines/launch/dialogs/job-notifications';
import {
  notificationArraysAreEqual
} from '../../pipelines/launch/dialogs/job-notifications/notifications-equal';
import {
  mapObservableNotification
} from '../../pipelines/launch/dialogs/job-notifications/job-notification';
import RescheduleRunControl, {
  rescheduleRunParameterValue
} from '../../pipelines/launch/form/utilities/reschedule-run-control';
import {getValidationError} from '../elements/EndpointInput';
import {getSelectOptions} from '../../special/instance-type-info';
import {
  correctLimitMountsParameterValue
} from '../../../utils/limit-mounts/get-limit-mounts-storages';

const Panels = {
  endpoints: 'endpoints',
  attributes: 'attributes',
  executionDefaults: 'exec',
  parameters: 'parameters'
};

const regionNotConfiguredValue = 'not_configured';

@Form.create()
@inject(
  'preferences',
  'awsRegions',
  'allowedInstanceTypes',
  'dataStorageAvailable',
  'spotToolInstanceTypes',
  'onDemandToolInstanceTypes',
  'runDefaultParameters'
)
@observer
export default class EditToolForm extends React.Component {
  static propTypes = {
    defaultPriceTypeIsSpot: PropTypes.bool,
    tool: PropTypes.shape({
      image: PropTypes.string,
      labels: PropTypes.object,
      description: PropTypes.string,
      mask: PropTypes.number,
      defaultCommand: PropTypes.string,
      endpoints: PropTypes.object
    }),
    allowSensitive: PropTypes.bool,
    mode: PropTypes.oneOf(['tool', 'version']),
    configuration: PropTypes.object,
    platform: PropTypes.string,
    onSubmit: PropTypes.func,
    readOnly: PropTypes.bool,
    onInitialized: PropTypes.func,
    executionEnvironmentDisabled: PropTypes.bool,
    dockerOSVersion: PropTypes.string,
    allowCommitVersion: PropTypes.bool
  };

  static defaultProps = {
    allowSensitive: false,
    mode: 'tool'
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 12}
    }
  };

  state = {
    labels: [],
    kubeLabels: [],
    initialKubeLabels: [],
    notifications: [],
    initialNotifications: [],
    kubeLabelsHasErrors: false,
    labelInputVisible: false,
    labelInputValue: '',
    endpointInputVisible: false,
    endpointInputValue: '',
    pending: false,
    configureClusterDialogVisible: false,
    openedPanels: [],
    nodesCount: 0,
    maxNodesCount: 0,
    autoScaledCluster: false,
    autoScaledPriceType: undefined,
    hybridAutoScaledClusterEnabled: false,
    gpuScalingConfiguration: undefined,
    childNodeInstanceConfiguration: undefined,
    gridEngineEnabled: false,
    sparkEnabled: false,
    slurmEnabled: false,
    kubeEnabled: false,
    launchCluster: false,
    rescheduleRun: undefined,
    runCapabilities: []
  };

  @observable defaultLimitMounts;
  @observable defaultCommand;
  @observable defaultProperties;
  @observable defaultSystemProperties;

  endpointControl;

  @computed
  get awsRegions () {
    if (this.props.awsRegions.loaded) {
      return (this.props.awsRegions.value || []).map(r => r);
    }
    return [];
  }

  get isWindowsPlatform () {
    return /^windows$/i.test(this.props.platform);
  }

  get hyperThreadingDisabled () {
    return (this.state.runCapabilities || [])
      .indexOf(RUN_CAPABILITIES.disableHyperThreading) >= 0;
  }

  showLabelInput = () => {
    this.setState({labelInputVisible: true}, () => this.input.focus());
  };

  handleLabelInputChange = (e) => {
    this.setState({labelInputValue: e.target.value});
  };

  handleLabelInputConfirm = () => {
    const state = this.state;
    const labelInputValue = state.labelInputValue;
    let labels = state.labels;
    if (labelInputValue && labels.indexOf(labelInputValue) === -1) {
      labels = [...labels, labelInputValue];
    }
    this.setState({
      labels,
      labelInputVisible: false,
      labelInputValue: ''
    });
  };

  saveLabelInputRef = input => {
    this.input = input;
  };

  onKubeLabelsChange = (labels = [], errors = {}) => {
    this.setState({
      kubeLabels: labels,
      kubeLabelsHasErrors: Object.keys(errors).length > 0
    });
  };

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err &&
        (!this.toolFormParameters || this.toolFormParameters.isValid) &&
        (!this.toolFormSystemParameters || this.toolFormSystemParameters.isValid) &&
        (!this.endpointControl || this.endpointControl.validate())) {
        let parameters = {};
        if (this.toolFormParameters && this.toolFormSystemParameters) {
          const params = [];
          params.push(...this.toolFormParameters.getValues(), ...this.toolFormSystemParameters.getValues());
          const toggleParameter = (parameter, value) => {
            const p = params.find((o) => o.name === parameter);
            if (p) {
              params.splice(params.indexOf(p), 1);
            }
            if (value) {
              params.push({
                name: parameter,
                value: true,
                type: 'boolean',
              });
            }
          }
          if (values.limitMounts) {
            params.push({
              name: CP_CAP_LIMIT_MOUNTS,
              value: values.limitMounts
            });
          }
          if (this.state.launchCluster && this.state.autoScaledCluster) {
            params.push({
              name: CP_CAP_AUTOSCALE,
              type: 'boolean',
              value: true
            });
            params.push({
              name: CP_CAP_AUTOSCALE_WORKERS,
              type: 'int',
              value: +this.state.maxNodesCount
            });
            if (this.state.autoScaledPriceType) {
              params.push({
                name: CP_CAP_AUTOSCALE_PRICE_TYPE,
                type: 'string',
                value: `${this.state.autoScaledPriceType}`
              });
            }
            if (this.state.hybridAutoScaledClusterEnabled) {
              params.push({
                name: CP_CAP_AUTOSCALE_HYBRID,
                type: 'boolean',
                value: true
              });
            }
            if (this.state.gpuScalingConfiguration) {
              applyParametersArray(this.state.gpuScalingConfiguration, params);
            } else if (this.state.childNodeInstanceConfiguration) {
              applyChildNodeInstanceParametersAsArray(
                params,
                this.state.childNodeInstanceConfiguration,
                this.state.hybridAutoScaledClusterEnabled
              );
            }
          }
          toggleParameter(CP_CAP_SGE, this.state.launchCluster && this.state.gridEngineEnabled);
          toggleParameter(CP_CAP_SPARK, this.state.launchCluster && this.state.sparkEnabled);
          toggleParameter(CP_CAP_SLURM, this.state.launchCluster && this.state.slurmEnabled);
          toggleParameter(CP_CAP_KUBE, this.state.launchCluster && this.state.kubeEnabled);
          if (this.state.launchCluster && this.state.kubeEnabled) {
            params.push({
              name: CP_CAP_DIND_CONTAINER,
              type: 'boolean',
              value: true
            });
            params.push({
              name: CP_CAP_SYSTEMD_CONTAINER,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.rescheduleRun !== undefined) {
            params.push({
              name: CP_CAP_RESCHEDULE_RUN,
              type: 'boolean',
              value: this.state.rescheduleRun
            });
          }
          parameters = applyCapabilities(
            parameters,
            this.state.runCapabilities,
            this.props.preferences,
            this.props.platform
          );
          for (let i = 0; i < params.length; i++) {
            parameters[params[i].name] = {
              type: params[i].type,
              value: params[i].value
            };
          }
        } else if (this.props.configuration && this.props.configuration.parameters) {
          for (let key in this.props.configuration.parameters) {
            if (this.props.configuration.parameters.hasOwnProperty(key)) {
              parameters[key] = {
                type: this.props.configuration.parameters[key].type,
                value: this.props.configuration.parameters[key].value,
                defaultValue: this.props.configuration.parameters[key].defaultValue,
                required: this.props.configuration.parameters[key].required
              };
            }
          }
        }
        const configuration = {
          parameters,
          node_count: this.state.launchCluster ? this.state.nodesCount : undefined,
          cloudRegionId: values.cloudRegionId === regionNotConfiguredValue
            ? null
            : +values.cloudRegionId,
          cmd_template: this.defaultCommand,
          instance_disk: values.disk,
          instance_size: values.instanceType,
          instance_image: values.instanceImage,
          is_spot: `${values.is_spot}` === 'true',
          kubeLabels: prepareKubeLabelsPayload(this.state.kubeLabels),
          notifications: this.state.notifications
        };
        this.setState({pending: true}, async () => {
          if (this.props.onSubmit) {
            await this.props.onSubmit(
              {
                endpoints: this.endpointControl ? values.endpoints : [],
                labels: this.state.labels,
                cpu: '1000mi',
                ram: '1Gi',
                allowSensitive: values.allowSensitive
              },
              configuration,
              values.allowCommit
            );
          }
          this.setState({pending: false});
        });
      } else if (err) {
        this.setState({openedPanels: [Panels.executionDefaults]});
      }
    });
  };

  getInitialValue = (field) => {
    switch (field) {
      case 'is_spot': return this.getPriceTypeInitialValue();
      case 'instance_size': return this.getInstanceTypeInitialValue();
      case 'instance_image': return this.getInstanceImageInitialValue();
      case 'instance_disk': return this.getDiskInitialValue();
      case 'allowSensitive': return this.getAllowSensitiveInitialValue();
      case 'allowCommit': return this.getAllowCommitInitialValue();
      default: return this.props.configuration ? this.props.configuration[field] : undefined;
    }
  };

  getInstanceTypeInitialValue = () => {
    return this.correctInstanceTypeValue(
      (this.props.configuration && this.props.configuration.instance_size) ||
      (this.props.tool && this.props.tool.instanceType)
    );
  };

  getInstanceTypeValue = () => {
    const name = this.props.form.getFieldValue('instanceType') || this.getInstanceTypeInitialValue();
    const [instanceType] = this.allowedInstanceTypes.filter(i => i.name === name);
    return instanceType;
  };

  getCloudProvider = () => {
    const instanceType = this.getInstanceTypeValue();
    if (this.props.awsRegions.loaded) {
      const [provider] = this.awsRegions
        .filter(a => (instanceType && a.id === instanceType.regionId) || !instanceType)
        .map(a => a.provider);
      return provider;
    }
    return null;
  };

  getCloudRegion = () => {
    const instanceType = this.getInstanceTypeValue();
    if (this.props.awsRegions.loaded) {
      return this.awsRegions
        .find(a => (instanceType && a.id === instanceType.regionId) || !instanceType);
    }
    return undefined;
  };

  getPriceTypeInitialValue = () => {
    return this.correctPriceTypeValue(
      this.props.configuration && this.props.configuration.is_spot !== undefined
        ? `${this.props.configuration.is_spot}`
        : `${this.props.defaultPriceTypeIsSpot}`
    );
  };

  getDiskInitialValue = () => {
    return (this.props.configuration && this.props.configuration.instance_disk) ||
      (this.props.tool && this.props.tool.disk) ||
      undefined;
  };

  getInstanceImageInitialValue = () => {
    return (this.props.configuration && this.props.configuration.instance_image) ||
      (this.props.tool && this.props.tool.instanceImage) ||
      undefined;
  };

  getCloudRegionInitialValue = () => {
    return this.props.configuration && this.props.configuration.cloudRegionId
      ? `${this.props.configuration.cloudRegionId}`
      : regionNotConfiguredValue;
  };

  getAllowCommitInitialValue = () => this.props.allowCommitVersion;

  getAllowSensitiveInitialValue = () => {
    if (this.props.mode === 'version') {
      return this.props.allowSensitive;
    }
    return this.props.tool
      ? `${this.props.tool.allowSensitive}`.toLowerCase() === 'true'
      : true;
  };

  getInitialCloudRegionNotAvailable = () => {
    const {getFieldValue} = this.props.form;
    const initialValue = this.getCloudRegionInitialValue();
    const currentValue = getFieldValue('cloudRegionId');
    return initialValue !== regionNotConfiguredValue &&
      currentValue === initialValue &&
      initialValue &&
      this.awsRegions.filter((region) => `${region.id}` === initialValue).length === 0;
  };

  rebuildComponent (props) {
    const state = this.state;
    state.labels = props.tool && props.tool.labels ? props.tool.labels.map(l => l) : [];
    this.defaultLimitMounts = null;
    this.defaultProperties = [];
    this.defaultSystemProperties = [];
    this.defaultCommand = (props.tool && props.tool.defaultCommand ? props.tool.defaultCommand : null);
    if (props.configuration) {
      (async () => {
        await this.props.runDefaultParameters.fetchIfNeededOrWait();
        await this.props.dataStorageAvailable.fetchIfNeededOrWait();
        await this.props.preferences.fetchIfNeededOrWait();
        await this.props.awsRegions.fetchIfNeededOrWait();
        state.maxNodesCount = props.configuration && props.configuration.parameters &&
          props.configuration.parameters[CP_CAP_AUTOSCALE_WORKERS]
            ? +props.configuration.parameters[CP_CAP_AUTOSCALE_WORKERS].value
            : 0;
        state.nodesCount = props.configuration.node_count;
        state.autoScaledCluster = props.configuration && autoScaledClusterEnabled(props.configuration.parameters);
        state.hybridAutoScaledClusterEnabled = props.configuration &&
          hybridAutoScaledClusterEnabled(props.configuration.parameters);
        const regions = this.props.awsRegions.loaded
          ? this.props.awsRegions.value
          : [];
        const instanceTypeName = (props.configuration ? props.configuration.instance_size : undefined) ||
          (props.tool ? props.tool.instanceType : undefined);
        const instanceType = this.allowedInstanceTypes.find(i => i.name === instanceTypeName);
        const [provider] = regions
          .filter(a => (instanceType && a.id === instanceType.regionId) || !instanceType)
          .map(a => a.provider);
        state.gpuScalingConfiguration = props.configuration
          ? readGPUScalingPreference({
            autoScaled: state.autoScaledCluster,
            hybrid: state.hybridAutoScaledClusterEnabled,
            provider,
            parameters: props.configuration.parameters
          }, this.props.preferences)
          : undefined;
        state.childNodeInstanceConfiguration = parseChildNodeInstanceConfiguration({
          gpuScaling: !!state.gpuScalingConfiguration,
          autoScaled: state.autoScaledCluster,
          hybrid: state.hybridAutoScaledClusterEnabled,
          provider,
          parameters: props.configuration.parameters
        });
        state.gridEngineEnabled = props.configuration && gridEngineEnabled(props.configuration.parameters);
        state.sparkEnabled = props.configuration && sparkEnabled(props.configuration.parameters);
        state.slurmEnabled = props.configuration && slurmEnabled(props.configuration.parameters);
        state.kubeEnabled = props.configuration && kubeEnabled(props.configuration.parameters);
        state.rescheduleRun = props.configuration &&
          rescheduleRunParameterValue(props.configuration.parameters);
        state.autoScaledPriceType = props.configuration &&
          getAutoScaledPriceTypeValue(props.configuration.parameters);
        state.launchCluster = state.nodesCount > 0 || state.autoScaledCluster;
        state.runCapabilities = getEnabledCapabilities(props.configuration.parameters);
        this.defaultCommand = props.configuration && props.configuration.cmd_template
          ? props.configuration.cmd_template
          : this.defaultCommand;
        const gpuScalingParameters = state.gpuScalingConfiguration
          ? getGPUScalingSkippedParameters(this.props.preferences)
          : [];
        const kubeLabels = Object
          .entries((this.props.configuration || {}).kubeLabels || {})
          .map(([key, value]) => ({key, value}));
        state.kubeLabels = kubeLabels;
        state.initialKubeLabels = kubeLabels;
        state.notifications = props.configuration
          ? (props.configuration.notifications || []).map(mapObservableNotification)
          : [];
        state.initialNotifications = (state.notifications || []).map(mapObservableNotification);
        if (props.configuration && props.configuration.parameters) {
          for (let key in props.configuration.parameters) {
            if (!props.configuration.parameters.hasOwnProperty(key) ||
              getSystemParameterDisabledState(this, key) ||
              gpuScalingParameters.includes(key)) {
              continue;
            }
            if (key === CP_CAP_LIMIT_MOUNTS) {
              if (this.props.dataStorageAvailable.loaded) {
                this.defaultLimitMounts = correctLimitMountsParameterValue(
                  props.configuration.parameters[CP_CAP_LIMIT_MOUNTS].value || '',
                  this.props.dataStorageAvailable.value || []
                );
              } else {
                this.defaultLimitMounts = props.configuration.parameters[CP_CAP_LIMIT_MOUNTS].value;
              }
              continue;
            }
            if (this.props.runDefaultParameters.loaded &&
              (this.props.runDefaultParameters.value || []).filter(p => p.name === key).length === 1) {
              this.defaultSystemProperties.push({
                name: key,
                type: props.configuration.parameters[key].type,
                value: props.configuration.parameters[key].value
              });
            } else {
              this.defaultProperties.push({
                name: key,
                type: props.configuration.parameters[key].type,
                value: props.configuration.parameters[key].value
              });
            }
          }
        }
        this.toolFormParameters && this.toolFormParameters.reset &&
        this.toolFormParameters.reset(this.defaultProperties);
        this.toolFormSystemParameters && this.toolFormSystemParameters.reset &&
        this.toolFormSystemParameters.reset(this.defaultSystemProperties);
        this.props.form.resetFields();
        this.setState(state);
      })();
    } else {
      this.props.form.resetFields();
      this.setState(state);
    }
  }

  componentDidMount () {
    this.reset();
    if (this.props.allowedInstanceTypes) {
      const isSpot = this.getPriceTypeInitialValue();
      const cloudRegionId = this.props.configuration && this.props.configuration.cloudRegionId
        ? this.props.configuration.cloudRegionId
        : undefined;
      this.props.allowedInstanceTypes.setParameters(
        {
          isSpot: `${isSpot}` === 'true',
          regionId: cloudRegionId,
          toolId: this.props.toolId
        }
      );
    }
    this.props.onInitialized && this.props.onInitialized(this);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {getFieldsValue} = this.props.form;
    if (prevProps.configuration !== this.props.configuration) {
      const cloudRegionId = this.props.configuration && this.props.configuration.cloudRegionId
        ? this.props.configuration.cloudRegionId
        : undefined;
      this.props.allowedInstanceTypes.setRegionId(cloudRegionId, true);
    }
    const fields = getFieldsValue();
    if ('instanceType' in fields && this.props.allowedInstanceTypes &&
      this.props.allowedInstanceTypes.loaded &&
      this.props.allowedInstanceTypes.changed) {
      this.correctAllowedInstanceValues();
      this.props.allowedInstanceTypes.handleChanged();
    }
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.tool !== this.props.tool || nextProps.configuration !== this.props.configuration) {
      this.reset(nextProps);
    }
  }

  correctAllowedInstanceValues = () => {
    const {getFieldValue} = this.props.form;
    const instanceType = getFieldValue('instanceType') || this.getInstanceTypeInitialValue();
    const priceType = getFieldValue('is_spot') || this.getPriceTypeInitialValue();
    this.props.form.setFieldsValue({
      instanceType: this.correctInstanceTypeValue(instanceType),
      is_spot: this.correctPriceTypeValue(priceType)
    });
  };

  handleIsSpotChange = (isSpot) => {
    if (this.props.allowedInstanceTypes && isSpot !== undefined && isSpot !== null) {
      this.props.allowedInstanceTypes.setIsSpot(`${isSpot}` === 'true');
    }
  };

  handleCloudRegionChange = (region) => {
    if (this.props.allowedInstanceTypes) {
      this.props.allowedInstanceTypes.setRegionId(
        region === regionNotConfiguredValue ? undefined : region,
        true
      );
    }
  };

  removeLabel = (label) => {
    const labels = this.state.labels;
    const index = labels.indexOf(label);
    if (index >= 0) {
      labels.splice(index, 1);
    }
    this.setState({labels});
  };

  defaultCommandEditorValueChanged = (code) => {
    this.defaultCommand = code;
  };

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  @computed
  get instanceTypes () {
    const isSpot = this.props.form.getFieldValue('is_spot') !== undefined
      ? `${this.props.form.getFieldValue('is_spot')}` === 'true'
      : `${this.getPriceTypeInitialValue()}` === 'true';
    let storeName = 'onDemandToolInstanceTypes';
    if (isSpot) {
      storeName = 'spotToolInstanceTypes';
    }
    if (!this.props[storeName].loaded) {
      return [];
    }
    const instanceTypes = [];
    for (let i = 0; i < (this.props[storeName].value || []).length; i++) {
      const instanceType = this.props[storeName].value[i];
      if (instanceTypes.filter(t => t.name === instanceType.name).length === 0) {
        instanceTypes.push(instanceType);
      }
    }
    return instanceTypes.sort((typeA, typeB) => {
      const vcpuCompared = typeA.vcpu - typeB.vcpu;
      const skuCompare = (a, b) => {
        return a.instanceFamily > b.instanceFamily
          ? 1
          : a.instanceFamily < b.instanceFamily ? -1 : 0;
      };
      return vcpuCompared === 0 ? skuCompare(typeA, typeB) : vcpuCompared;
    });
  }

  @computed
  get allowedInstanceTypes () {
    if (!this.props.allowedInstanceTypes.loaded || !this.props.allowedInstanceTypes.value[names.allowedToolInstanceTypes]) {
      return [];
    }
    const instanceTypes = [];
    for (let i = 0; i < (this.props.allowedInstanceTypes.value[names.allowedToolInstanceTypes] || []).length; i++) {
      const instanceType = this.props.allowedInstanceTypes.value[names.allowedToolInstanceTypes][i];
      if (instanceTypes.filter(t => t.name === instanceType.name).length === 0) {
        instanceTypes.push(instanceType);
      }
    }
    return instanceTypes.sort((typeA, typeB) => {
      const vcpuCompared = typeA.vcpu - typeB.vcpu;
      const skuCompare = (a, b) => {
        return a.instanceFamily > b.instanceFamily
          ? 1
          : a.instanceFamily < b.instanceFamily ? -1 : 0;
      };
      return vcpuCompared === 0 ? skuCompare(typeA, typeB) : vcpuCompared;
    });
  }

  @computed
  get allowedPriceTypes () {
    let availableMasterNodeTypes = [true, false];
    if (this.state.launchCluster && this.props.preferences.loaded) {
      availableMasterNodeTypes = this.props.preferences.allowedMasterPriceTypes;
    }
    let priceTypes = availableMasterNodeTypes.slice();
    if (this.props.allowedInstanceTypes.loaded && this.props.allowedInstanceTypes.value[names.allowedPriceTypes]) {
      priceTypes = [];
      for (let i = 0; i < (this.props.allowedInstanceTypes.value[names.allowedPriceTypes] || []).length; i++) {
        const isSpot = this.props.allowedInstanceTypes.value[names.allowedPriceTypes][i].toLowerCase() === 'spot';
        if (availableMasterNodeTypes.indexOf(isSpot) >= 0) {
          priceTypes.push(isSpot);
        }
      }
    }
    return priceTypes.map(isSpot => ({
      isSpot,
      name: getSpotTypeName(isSpot, this.getCloudProvider())
    }))
      .filter(v => availableMasterNodeTypes.indexOf(v.isSpot) >= 0);
  }

  @computed
  get multiplyValueBy () {
    if (this.state.launchCluster) {
      return (this.state.nodesCount || 0) + 1;
    } else {
      return 1;
    }
  }

  @computed
  get maxMultiplyValueBy () {
    if (this.state.launchCluster) {
      let value = this.state.maxNodesCount;
      if (!value || isNaN(value)) {
        value = 1;
      } else {
        value = +value;
      }
      return value + 1;
    } else {
      return 1;
    }
  }

  modified = () => {
    const arrayIsNullOrEmpty = (array) => {
      return !array || !array.length;
    };
    const compareArrays = (arr1, arr2) => {
      if ((!arrayIsNullOrEmpty(arr1) && arrayIsNullOrEmpty(arr2)) ||
        (arrayIsNullOrEmpty(arr1) && !arrayIsNullOrEmpty(arr2))) {
        return false;
      }
      if (arrayIsNullOrEmpty(arr1) && arrayIsNullOrEmpty(arr2)) {
        return true;
      }
      if (arr1.length !== arr2.length) {
        return false;
      }
      for (let i = 0; i < arr1.length; i++) {
        if (arr1[i] !== arr2[i]) {
          return false;
        }
      }
      return true;
    };
    const configurationFormFieldChanged = (field, formFieldName) => {
      formFieldName = formFieldName || field;
      const formField = this.props.form.getFieldValue(formFieldName);
      const toolField = this.getInitialValue(field);
      const formFieldValue = formField ? `${formField}` : null;
      const toolFieldValue = toolField ? `${toolField}` : null;
      return formFieldValue !== toolFieldValue;
    };
    const commandChanged = () => {
      let toolCommand;
      if (this.props.configuration &&
        this.props.configuration.cmd_template !== undefined &&
        this.props.configuration.cmd_template !== null) {
        toolCommand = this.props.configuration.cmd_template;
      } else {
        toolCommand = this.props.tool ? this.props.tool.defaultCommand : null;
      }
      const toolCommandValue = toolCommand ? `${toolCommand}` : '';
      const formCommandValue = this.defaultCommand ? `${this.defaultCommand}` : '';
      return toolCommandValue !== formCommandValue;
    };
    const limitMountsFieldChanged = () => {
      const fieldValue = this.props.form.getFieldValue('limitMounts');
      if (!this.defaultLimitMounts && !fieldValue) {
        return false;
      }
      return this.defaultLimitMounts !== fieldValue;
    };
    const cloudRegionFieldChanged = () => {
      return this.getCloudRegionInitialValue() !== this.props.form.getFieldValue('cloudRegionId');
    };
    const toolEndpointArray = this.props.tool ? (this.props.tool.endpoints || []).map(e => e) : null;
    const toolEndpointArrayFormValue = (this.props.form.getFieldValue('endpoints') || []).map(e => e);
    const toolLabelsArray = this.props.tool ? (this.props.tool.labels || []).map(l => l) : null;
    const nodesCount = this.props.configuration ? this.props.configuration.node_count : 0;
    const maxNodesCount = this.props.configuration && this.props.configuration.parameters &&
      this.props.configuration.parameters[CP_CAP_AUTOSCALE_WORKERS]
        ? +this.props.configuration.parameters[CP_CAP_AUTOSCALE_WORKERS].value
        : 0;
    const autoScaledCluster = this.props.configuration &&
      autoScaledClusterEnabled(this.props.configuration.parameters);
    const hybridAutoScaledCluster = this.props.configuration &&
      hybridAutoScaledClusterEnabled(this.props.configuration.parameters);
    const gpuScalingConfiguration = this.props.configuration
      ? readGPUScalingPreference({
        autoScaled: autoScaledCluster,
        hybrid: hybridAutoScaledCluster,
        provider: this.getCloudProvider(),
        parameters: this.props.configuration ? this.props.configuration.parameters : {}
      }, this.props.preferences)
      : undefined;
    const childNodeInstanceConfiguration = this.props.configuration
      ? parseChildNodeInstanceConfiguration({
        gpuScaling: !!gpuScalingConfiguration,
        autoScaled: autoScaledCluster,
        hybrid: hybridAutoScaledCluster,
        parameters: this.props.configuration.parameters
      }) : undefined;
    const gridEngineEnabledValue = this.props.configuration &&
      gridEngineEnabled(this.props.configuration.parameters);
    const sparkEnabledValue = this.props.configuration &&
      sparkEnabled(this.props.configuration.parameters);
    const slurmEnabledValue = this.props.configuration &&
      slurmEnabled(this.props.configuration.parameters);
    const kubeEnabledValue = this.props.configuration &&
      kubeEnabled(this.props.configuration.parameters);
    const rescheduleRunValue = this.props.configuration &&
      rescheduleRunParameterValue(this.props.configuration.parameters);
    const autoScaledPriceTypeValue = this.props.configuration &&
      getAutoScaledPriceTypeValue(this.props.configuration.parameters);
    const launchCluster = nodesCount > 0 || autoScaledCluster;
    const additionalCapabilitiesChanged = () => {
      return checkRunCapabilitiesModified(
        this.state.runCapabilities,
        getEnabledCapabilities(this.props.configuration.parameters),
        this.props.preferences
      );
    };

    return configurationFormFieldChanged('is_spot') ||
      configurationFormFieldChanged('instance_size', 'instanceType') ||
      configurationFormFieldChanged('instance_image', 'instanceImage') ||
      configurationFormFieldChanged('instance_disk', 'disk') ||
      configurationFormFieldChanged('allowSensitive') ||
      configurationFormFieldChanged('allowCommit') ||
      commandChanged() ||
      !compareArrays(toolEndpointArray, toolEndpointArrayFormValue) ||
      !compareArrays(toolLabelsArray, this.state.labels) ||
      (this.toolFormParameters && this.toolFormParameters.modified) ||
      (this.toolFormSystemParameters && this.toolFormSystemParameters.modified) ||
      !!launchCluster !== !!this.state.launchCluster ||
      !!autoScaledCluster !== !!this.state.autoScaledCluster ||
      !!hybridAutoScaledCluster !== !!this.state.hybridAutoScaledClusterEnabled ||
      configurationChanged(gpuScalingConfiguration, this.state.gpuScalingConfiguration) ||
      childNodeInstanceConfiguration !== this.state.childNodeInstanceConfiguration ||
      !!gridEngineEnabledValue !== !!this.state.gridEngineEnabled ||
      !!sparkEnabledValue !== !!this.state.sparkEnabled ||
      !!slurmEnabledValue !== !!this.state.slurmEnabled ||
      !!kubeEnabledValue !== !!this.state.kubeEnabled ||
      rescheduleRunValue !== this.state.rescheduleRun ||
      autoScaledPriceTypeValue !== this.state.autoScaledPriceType ||
      (this.state.launchCluster && nodesCount !== this.state.nodesCount) ||
      (this.state.launchCluster && this.state.autoScaledCluster && maxNodesCount !== this.state.maxNodesCount) ||
      limitMountsFieldChanged() || cloudRegionFieldChanged() || additionalCapabilitiesChanged() ||
      kubeLabelsHasChanges(
        this.state.initialKubeLabels,
        this.state.kubeLabels
      ) ||
      !notificationArraysAreEqual(this.state.notifications, this.state.initialNotifications);
  };

  initializeEndpointsControl = (control) => {
    this.endpointControl = control;
  };

  @observable toolFormParameters;
  @observable toolFormSystemParameters;

  onEditToolFormParametersInitialized = (form) => {
    this.toolFormParameters = form;
    form && form.reset && form.reset();
  };

  onEditToolFormSystemParametersInitialized = (form) => {
    this.toolFormSystemParameters = form;
    form && form.reset && form.reset();
  };

  openConfigureClusterDialog = () => {
    if (this.props.readOnly) {
      return;
    }
    this.setState({
      configureClusterDialogVisible: true
    });
  };

  closeConfigureClusterDialog = () => {
    this.setState({
      configureClusterDialogVisible: false
    });
  };

  onChangeClusterConfiguration = (configuration) => {
    const {
      launchCluster,
      autoScaledCluster,
      hybridAutoScaledClusterEnabled,
      gpuScalingConfiguration,
      childNodeInstanceConfiguration,
      nodesCount,
      maxNodesCount,
      gridEngineEnabled,
      sparkEnabled,
      slurmEnabled,
      kubeEnabled,
      autoScaledPriceType
    } = configuration;
    let {runCapabilities} = this.state;
    if (kubeEnabled) {
      runCapabilities = addCapability(
        runCapabilities,
        RUN_CAPABILITIES.dinD,
        RUN_CAPABILITIES.systemD
      );
    }
    this.setState({
      launchCluster,
      nodesCount,
      autoScaledCluster,
      hybridAutoScaledClusterEnabled,
      gpuScalingConfiguration,
      childNodeInstanceConfiguration,
      maxNodesCount,
      gridEngineEnabled,
      sparkEnabled,
      slurmEnabled,
      kubeEnabled,
      autoScaledPriceType,
      runCapabilities
    }, () => {
      this.closeConfigureClusterDialog();
      const priceType = this.props.form.getFieldValue('is_spot') || this.getPriceTypeInitialValue();
      this.props.form.setFieldsValue({
        is_spot: this.correctPriceTypeValue(priceType)
      });
    });
  };

  cpuMapper = cpu => this.hyperThreadingDisabled && !Number.isNaN(Number(cpu))
    ? (cpu / 2.0)
    : cpu;

  renderExecutionEnvironmentSummary = () => {
    const instanceTypeValue = this.props.form.getFieldValue('instanceType');
    const [instanceType] = this.instanceTypes.filter(t => t.name === instanceTypeValue);
    let cpu = 0;
    let ram = 0;
    let gpu = 0;
    if (instanceType) {
      cpu = +(instanceType.vcpu || 0);
      gpu = +(instanceType.gpu || 0);
      ram = +(instanceType.memory || 0);
    }
    let disk = +(this.props.form.getFieldValue('disk') || 0);
    let maxCPU = cpu;
    let maxRAM = ram;
    let maxGPU = gpu;
    let maxDISK = disk;
    if (this.state.launchCluster) {
      cpu *= this.multiplyValueBy;
      gpu *= this.multiplyValueBy;
      ram *= this.multiplyValueBy;
      disk *= this.multiplyValueBy;
      if (this.state.autoScaledCluster) {
        maxCPU *= this.maxMultiplyValueBy;
        maxGPU *= this.maxMultiplyValueBy;
        maxRAM *= this.maxMultiplyValueBy;
        maxDISK *= this.maxMultiplyValueBy;
      } else {
        maxCPU = maxRAM = maxGPU = maxDISK = 0;
      }
    } else {
      maxCPU = maxRAM = maxGPU = maxDISK = 0;
    }
    const lines = [];
    if (cpu) {
      maxCPU && maxCPU > cpu
        ? lines.push(<span>{this.cpuMapper(cpu)} - {this.cpuMapper(maxCPU)} <b>CPU</b></span>)
        : lines.push(<span>{this.cpuMapper(cpu)} <b>CPU</b></span>);
    }
    if (ram) {
      maxRAM && maxRAM > ram
        ? lines.push(<span>{ram} - {maxRAM} <b>RAM</b></span>)
        : lines.push(<span>{ram} <b>RAM</b></span>);
    }
    if (gpu) {
      maxGPU && maxGPU > gpu
        ? lines.push(<span>{gpu} - {maxGPU} <b>GPU</b></span>)
        : lines.push(<span>{gpu} <b>GPU</b></span>);
    }
    if (disk) {
      maxDISK && maxDISK > disk
        ? lines.push(<span>{disk} - {maxDISK} <b>Gb</b></span>)
        : lines.push(<span>{disk} <b>Gb</b></span>);
    }
    if (lines.length > 0) {
      return (
        <div className={styles.summaryContainer}>
          <div className={classNames(styles.summary, 'cp-exec-env-summary')}>
            {
              lines.map((l, index) => (
                <div key={index} className={classNames(styles.summaryItem, 'cp-exec-env-summary-item')}>
                  {l}
                </div>
              ))
            }
          </div>
        </div>
      );
    } else {
      return null;
    }
  };

  renderSeparator = (text) => {
    return (
      <Row type="flex" style={{margin: 10}}>
        <Col sm={{span: 12, offset: 6}} lg={{span: 24, offset: 0}}>
          <table style={{width: '100%'}}>
            <tbody>
            <tr>
              <td style={{width: '50%'}}>
                <div
                  className={classNames('cp-divider', 'tool-settings')}
                  style={{
                    margin: '0 5px',
                    verticalAlign: 'middle',
                    height: 1
                  }}>{'\u00A0'}</div>
              </td>
              <td style={{width: 1, whiteSpace: 'nowrap'}}><b>{text}</b></td>
              <td style={{width: '50%'}}>
                <div
                  className={classNames('cp-divider', 'tool-settings')}
                  style={{
                    margin: '0 5px',
                    verticalAlign: 'middle',
                    height: 1
                  }}>{'\u00A0'}</div>
              </td>
            </tr>
            </tbody>
          </table>
        </Col>
      </Row>
    );
  };

  correctInstanceTypeValue = (value) => {
    if (value !== undefined && value !== null) {
      const [v] = this.allowedInstanceTypes.filter(v => v.name === value);
      if (v !== undefined) {
        return v.name;
      }
      return null;
    }
    return value;
  };

  correctPriceTypeValue = (value) => {
    if (value !== undefined && value !== null) {
      let realValue = `${value}` === 'true';
      const allowedPriceTypes = this.allowedPriceTypes.map(p => p.isSpot);
      const [v] = allowedPriceTypes.filter(v => v === realValue);
      if (v !== undefined) {
        return `${v}`;
      } else if (allowedPriceTypes.length > 0) {
        return `${allowedPriceTypes[0]}`;
      } else {
        return undefined;
      }
    }
    return value;
  };

  correctSensitiveMounts = async (e) => {
    const {dataStorageAvailable, form} = this.props;
    const initialLimitMounts = (form.getFieldValue('limitMounts') || this.defaultLimitMounts || '');
    if (/^none$/i.test(initialLimitMounts)) {
      return;
    }
    const storages = initialLimitMounts
      .split(',')
      .map(id => +id);
    await dataStorageAvailable.fetchIfNeededOrWait();
    const hasSensitive = (dataStorageAvailable.value || [])
      .filter(s => s.sensitive && storages.indexOf(+s.id) >= 0)
      .length > 0;
    if (!e.target.checked && hasSensitive) {
      const cancel = () => {
        form.setFieldsValue({allowSensitive: true, limitMounts: initialLimitMounts});
      };
      const submit = () => {
        form.setFieldsValue({
          limitMounts: (dataStorageAvailable.value || [])
            .filter(s => !s.sensitive && storages.indexOf(+s.id) >= 0)
            .map(s => s.id)
            .join(',')
        });
      };
      Modal.confirm({
        title: 'Sensitive storages will be removed from Limit Mounts setting',
        onOk: submit,
        onCancel: cancel
      });
    }
  };

  onRunCapabilitiesSelect = (capabilities) => {
    this.setState({
      runCapabilities: (capabilities || []).slice()
    });
  };

  toggleDoNotMountStorages = (e) => {
    if (e.target.checked) {
      this.props.form.setFieldsValue({
        limitMounts: 'None'
      });
    } else {
      this.props.form.setFieldsValue({
        limitMounts: null
      });
    }
  };

  onChangeRescheduleRun = (value) => this.setState({
    rescheduleRun: value
  });

  renderExecutionEnvironment = () => {
    const renderExecutionEnvironmentSection = () => {
      const {getFieldDecorator, getFieldValue} = this.props.form;
      let allowSensitive = getFieldValue('allowSensitive');
      if (allowSensitive === undefined) {
        allowSensitive = this.getAllowSensitiveInitialValue();
      }
      const limitMountsValue = getFieldValue('limitMounts');
      return (
        <div>
          {this.renderSeparator('Execution defaults')}
          <Row>
            <Col sm={24} lg={12}>
              <Form.Item {...this.formItemLayout} label="Instance type" style={{marginBottom: 10}} required>
                {getFieldDecorator('instanceType',
                  {
                    rules: [
                      {
                        required: true,
                        message: 'Instance type is required'
                      }
                    ],
                    initialValue: this.getInstanceTypeInitialValue()
                  })(
                  <Select
                    disabled={this.state.pending || this.props.readOnly || (
                      this.props.allowedInstanceTypes &&
                      (
                        this.props.allowedInstanceTypes.changed ||
                        this.props.allowedInstanceTypes.pending
                      )
                    )}
                    showSearch
                    allowClear={false}
                    placeholder="Instance type"
                    optionFilterProp="children"
                    filterOption={
                      (input, option) =>
                      option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}>
                    {getSelectOptions(this.allowedInstanceTypes)}
                  </Select>
                )}
              </Form.Item>
              <Form.Item
                {...this.formItemLayout}
                label="Instance image"
                style={{marginBottom: 10}}
              >
                {getFieldDecorator('instanceImage',
                  {
                    initialValue: this.getInstanceImageInitialValue()
                  })(
                    <Input
                      disabled={this.state.pending || this.props.readOnly}
                    />
                )}
              </Form.Item>
              <Form.Item {...this.formItemLayout} label="Price type" style={{marginTop: 10, marginBottom: 10}}>
                {getFieldDecorator('is_spot',
                  {
                    initialValue: this.getPriceTypeInitialValue()
                  })(
                    <Select disabled={this.state.pending || this.props.readOnly} onChange={this.handleIsSpotChange}>
                      {
                        this.allowedPriceTypes
                          .map(t => <Select.Option key={`${t.isSpot}`}>{t.name}</Select.Option>)
                      }
                    </Select>
                )}
              </Form.Item>
              <Form.Item {...this.formItemLayout} label="Disk (Gb)" style={{marginTop: 10, marginBottom: 10}} required>
                {getFieldDecorator('disk',
                  {
                    rules: [
                      {
                        pattern: /^\d+(\.\d+)?$/,
                        message: 'Please enter a valid positive number'
                      },
                      {
                        validator: (rule, value, callback) => {
                          if (!isNaN(value)) {
                            if (+value > 15360) {
                              callback('Maximum value is 15360');
                              return;
                            } else if (+value < 15) {
                              callback('Minimum value is 15');
                              return;
                            }
                          }
                          callback();
                        }
                      },
                      {
                        required: true,
                        message: 'Disk size is required (minimum value is 15)'
                      }
                    ],
                    initialValue: this.getDiskInitialValue()
                  })(
                  <Input disabled={this.state.pending || this.props.readOnly} />
                )}
              </Form.Item>
              <Row style={{marginBottom: 10, marginTop: 10}}>
                <Col
                  xs={24}
                  sm={6}
                  style={{paddingRight: 10}}
                  className={classNames(
                    'cp-accent',
                    styles.toolSettingsTitle
                  )}
                >
                  Notifications:
                </Col>
                <Col xs={24} sm={12}>
                  <JobNotifications
                    value={this.state.notifications}
                    onChange={o => this.setState({notifications: o})}
                    linkStyle={{margin: 0}}
                  />
                </Col>
              </Row>
              {
                !this.isWindowsPlatform && (
                  <Form.Item
                    {...this.formItemLayout}
                    label="Limit mounts"
                    style={{marginTop: 10, marginBottom: 10}}
                  >
                    <div>
                      <Row type="flex" align="middle">
                        <Checkbox
                          checked={/^none$/i.test(limitMountsValue)}
                          onChange={this.toggleDoNotMountStorages}
                        >
                          Do not mount storages
                        </Checkbox>
                      </Row>
                      <Row
                        type="flex"
                        align="middle"
                        style={{display: /^none$/i.test(limitMountsValue) ? 'none' : undefined}}
                      >
                        <Form.Item
                          style={{marginBottom: 0, flex: 1}}
                        >
                          {getFieldDecorator('limitMounts',
                            {
                              initialValue: this.defaultLimitMounts
                            })(
                            <LimitMountsInput
                              allowSensitive={allowSensitive}
                              disabled={this.state.pending || this.props.readOnly}
                            />
                          )}
                        </Form.Item>
                      </Row>
                    </div>
                  </Form.Item>
                )
              }
              <Form.Item
                {...this.formItemLayout}
                label="Allow commit of the tool"
                style={{marginTop: 10, marginBottom: 10}}
              >
                {getFieldDecorator('allowCommit', {
                  initialValue: this.getAllowCommitInitialValue(),
                  valuePropName: 'checked'
                })(
                  <Checkbox
                    disabled={this.state.pending || this.props.readOnly}
                  />
                )}
              </Form.Item>
              {
                !this.isWindowsPlatform && (
                  <Form.Item
                    {...this.formItemLayout}
                    label="Allow sensitive storages"
                    style={{marginTop: 10, marginBottom: 10}}
                  >
                    {getFieldDecorator('allowSensitive',
                      {
                        initialValue: this.getAllowSensitiveInitialValue(),
                        valuePropName: 'checked'
                      })(
                      <Checkbox
                        disabled={
                          this.state.pending ||
                          this.props.readOnly ||
                          this.props.mode === 'version'
                        }
                        onChange={this.correctSensitiveMounts}
                      />
                    )}
                  </Form.Item>
                )
              }
              {
                <Row style={{marginBottom: 10, marginTop: 10}}>
                  <Col
                    xs={24}
                    sm={6}
                    style={{paddingRight: 10}}
                    className={classNames(
                      'cp-accent',
                      styles.toolSettingsTitle
                    )}
                  >
                    Runtime labels:
                  </Col>
                  <Col xs={24} sm={12}>
                    <KubeLabels
                      labels={this.state.kubeLabels}
                      onChange={this.onKubeLabelsChange}
                    />
                  </Col>
                </Row>
              }
              {
                !this.isWindowsPlatform && (
                  <Row type="flex" align="middle" style={{marginBottom: 10}}>
                    <Col xs={24} sm={{span: 12, offset: 6}}>
                      <Row type="flex" justify="end">
                        <a
                          onClick={this.openConfigureClusterDialog}
                          className={classNames('cp-text', 'underline')}
                          style={{textDecoration: 'underline'}}>
                          <Icon type="setting" /> {ConfigureClusterDialog.getConfigureClusterButtonDescription(this)}
                        </a>
                      </Row>
                    </Col>
                  </Row>
                )
              }
              <Form.Item
                {...this.formItemLayout}
                label="Cloud Region"
                style={{marginTop: 10, marginBottom: 10}}
                required
              >
                {getFieldDecorator('cloudRegionId', {
                  rules: [
                    {
                      required: true,
                      message: 'Cloud region is required'
                    }
                  ],
                  initialValue: this.getCloudRegionInitialValue()
                })(
                  <Select
                    disabled={this.state.pending || this.props.readOnly}
                    showSearch
                    allowClear={false}
                    placeholder="Cloud Region"
                    optionFilterProp="children"
                    onChange={this.handleCloudRegionChange}
                    filterOption={
                      (input, option) =>
                        option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                  >
                    <Select.Option
                      key={regionNotConfiguredValue}
                      name="Not configured"
                      title="Not configured"
                      value={regionNotConfiguredValue}
                    >
                      Not configured
                    </Select.Option>
                    {
                      this.getInitialCloudRegionNotAvailable() && (
                        <Select.Option
                          key={this.getCloudRegionInitialValue()}
                          name="Not available"
                          title="Not available"
                          value={this.getCloudRegionInitialValue()}
                        >
                          Not available
                        </Select.Option>
                      )
                    }
                    <Select.Option
                      disabled
                      key="divider"
                      className={
                        classNames(
                          styles.selectOptionDivider,
                          'cp-tool-select-option-divider'
                        )
                      }
                    />
                    {
                      this.awsRegions
                        .map(region => {
                          return (
                            <Select.Option
                              key={`${region.id}`}
                              name={region.name}
                              title={region.name}
                              value={`${region.id}`}>
                              <AWSRegionTag
                                provider={region.provider}
                                regionUID={region.regionId}
                                style={{fontSize: 'larger'}}
                              /> {region.name}
                            </Select.Option>
                          );
                        })
                    }
                  </Select>
                )}
              </Form.Item>
              <Row
                type="flex"
                align="middle"
                style={{marginBottom: 10}}
              >
                <Col xs={24} sm={{span: 18}}>
                  <Row type="flex" justify="center">
                    Allow reschedule to different region in case of insufficient capacity:
                  </Row>
                </Col>
              </Row>
              <Row type="flex" align="middle" style={{marginBottom: 30}}>
                <Col xs={24} sm={{span: 12, offset: 6}}>
                  <Row type="flex" justify="start">
                    <RescheduleRunControl
                      disabled={this.state.pending || this.props.readOnly}
                      value={this.state.rescheduleRun}
                      onChange={this.onChangeRescheduleRun}
                    />
                  </Row>
                </Col>
              </Row>
              {
                hasPlatformSpecificCapabilities(this.props.platform, this.props.preferences) && (
                  <Form.Item
                    {...this.formItemLayout}
                    label="Run capabilities"
                    style={{marginTop: 10, marginBottom: 10}}
                  >
                    <RunCapabilities
                      disabled={this.state.pending || this.props.readOnly}
                      values={this.state.runCapabilities}
                      onChange={this.onRunCapabilitiesSelect}
                      platform={this.props.platform}
                      dockerImageOS={this.props.dockerOSVersion}
                      provider={this.getCloudProvider()}
                      region={this.getCloudRegion()}
                      mode={RUN_CAPABILITIES_MODE.edit}
                    />
                  </Form.Item>
                )
              }
              <ConfigureClusterDialog
                instanceName={this.props.form.getFieldValue('instanceType')}
                instanceTypes={this.allowedInstanceTypes}
                launchCluster={this.state.launchCluster}
                cloudRegionProvider={this.getCloudProvider()}
                autoScaledPriceType={this.state.autoScaledPriceType}
                autoScaledCluster={this.state.autoScaledCluster}
                hybridAutoScaledClusterEnabled={this.state.hybridAutoScaledClusterEnabled}
                gpuScalingConfiguration={this.state.gpuScalingConfiguration}
                childNodeInstanceConfiguration={this.state.childNodeInstanceConfiguration}
                gridEngineEnabled={this.state.gridEngineEnabled}
                sparkEnabled={this.state.sparkEnabled}
                slurmEnabled={this.state.slurmEnabled}
                kubeEnabled={this.state.kubeEnabled}
                nodesCount={this.state.nodesCount}
                maxNodesCount={+this.state.maxNodesCount || 1}
                onClose={this.closeConfigureClusterDialog}
                onChange={this.onChangeClusterConfiguration}
                visible={this.state.configureClusterDialogVisible}
                disabled={!this.props.configuration || this.props.readOnly || this.state.pending} />
              <Row style={{marginBottom: 10}}>
                <Col xs={24} sm={6} className={styles.toolSettingsTitle}>Cmd template:</Col>
                <Col xs={24} sm={12}>
                  <CodeEditor
                    readOnly={this.state.pending || this.props.readOnly}
                    ref={this.initializeEditor}
                    className={styles.codeEditor}
                    language="shell"
                    onChange={this.defaultCommandEditorValueChanged}
                    lineWrapping={true}
                    defaultCode={this.defaultCommand}
                  />
                </Col>
              </Row>
            </Col>
            <Col sm={24} lg={12}>
              <Row type="flex" align="middle" style={{marginBottom: 10}}>
                <Col xs={24} sm={{span: 12, offset: 6}} lg={{span: 16, offset: 6}}>
                  {
                    this.renderExecutionEnvironmentSummary()
                  }
                </Col>
              </Row>
            </Col>
          </Row>
          {this.renderSeparator('System parameters')}
          <EditToolFormParameters
            readOnly={!this.props.configuration || this.props.readOnly || this.state.pending}
            isSystemParameters
            getSystemParameterDisabledState={
              (parameterName) => getSystemParameterDisabledState(this, parameterName)
            }
            skippedSystemParameters={getSkippedSystemParametersList(this)}
            value={this.defaultSystemProperties}
            onInitialized={this.onEditToolFormSystemParametersInitialized}
            testSkipParameter={name => isCustomCapability(name, this.props.preferences)}
          />
          {this.renderSeparator('Custom parameters')}
          <EditToolFormParameters
            readOnly={!this.props.configuration || this.props.readOnly || this.state.pending}
            value={this.defaultProperties}
            onInitialized={this.onEditToolFormParametersInitialized}
            testSkipParameter={name => isCustomCapability(name, this.props.preferences)}
          />
        </div>
      );
    };
    if (!this.props.tool) {
      return renderExecutionEnvironmentSection();
    } else {
      const onOpenPanel = panels => this.setState({openedPanels: panels});
      return (
        <Collapse
          bordered={false}
          activeKey={this.state.openedPanels}
          onChange={onOpenPanel}>
          <Collapse.Panel
            key={Panels.executionDefaults}
            header={<span>EXECUTION ENVIRONMENT</span>}>
            {renderExecutionEnvironmentSection()}
          </Collapse.Panel>
        </Collapse>
      );
    }
  };

  endpointsAreValid () {
    try {
      const endpoints = this.props.form.getFieldValue('endpoints') || [];
      return !endpoints.some((endpoint) => getValidationError(endpoint));
    } catch (error) {
      console.warn(error);
      return false;
    }
  }

  render () {
    const {getFieldDecorator} = this.props.form;
    const isTool = this.props.mode === 'tool';
    const endpointsAreValid = this.endpointsAreValid();
    return (
      <Form>
        {
          isTool && this.renderSeparator('Tool endpoints')
        }
        {
          isTool &&
          <Row type="flex">
            <Col xs={24} sm={6} />
            <Col xs={24} sm={12}>
              <Form.Item>
                {getFieldDecorator('endpoints',
                  {
                    initialValue: this.props.tool ? (this.props.tool.endpoints || []).map(e => e) : []
                  })(
                  <ToolEndpointsFormItem
                    disabled={this.state.pending || this.props.readOnly}
                    ref={this.initializeEndpointsControl} />
                )}
              </Form.Item>
            </Col>
          </Row>
        }
        {isTool && this.renderSeparator('Tool attributes')}
        {
          isTool &&
          <Row style={{marginBottom: 10, marginTop: 10}}>
            <Col xs={24} sm={6} className={styles.toolSettingsTitle}>Labels:</Col>
            <Col xs={24} sm={12}>
              {
                this.state.labels.map(label => {
                  return (
                    <Tag
                      style={{marginBottom: 5}}
                      key={label}
                      closable={!this.state.pending && !this.props.readOnly}
                      onClose={() => this.removeLabel(label)}>
                      {label}
                    </Tag>
                  );
                })
              }
              {this.state.labelInputVisible && (
                <Input
                  disabled={this.state.pending || this.props.readOnly}
                  ref={this.saveLabelInputRef}
                  type="text"
                  size="small"
                  style={{width: 78}}
                  value={this.state.labelInputValue}
                  onChange={this.handleLabelInputChange}
                  onBlur={this.handleLabelInputConfirm}
                  onPressEnter={this.handleLabelInputConfirm}
                />
              )}
              {
                !this.state.labelInputVisible &&
                <Button
                  disabled={this.state.pending || this.props.readOnly}
                  size="small"
                  type="dashed"
                  onClick={this.showLabelInput}>
                  + New Label
                </Button>
              }
            </Col>
          </Row>
        }
        {!this.props.executionEnvironmentDisabled && this.renderExecutionEnvironment()}
        <Row style={{marginTop: 20}}>
          <Col span={18}>
            <Row type="flex" justify="end">
              <Button
                onClick={this.handleSubmit}
                type="primary"
                htmlType="submit"
                disabled={
                  this.state.pending ||
                  this.props.readOnly ||
                  !this.modified() ||
                  (this.toolFormSystemParameters && !this.toolFormSystemParameters.isValid) ||
                  (this.toolFormParameters && !this.toolFormParameters.isValid) ||
                  this.state.kubeLabelsHasErrors ||
                  !endpointsAreValid
                }>
                SAVE
              </Button>
            </Row>
          </Col>
        </Row>
      </Form>
    );
  }

  reset = (props) => {
    props = props || this.props;
    this.rebuildComponent(props);
    if (this.editor) {
      this.editor.clear();
      this.editor.setValue(this.defaultCommand);
    }
  }
}
