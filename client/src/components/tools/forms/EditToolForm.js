/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import ToolEndpointsFormItem from '../elements/ToolEndpointsFormItem';
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
  getAutoScaledPriceTypeValue
} from '../../pipelines/launch/form/utilities/launch-cluster';
import {
  CP_CAP_LIMIT_MOUNTS,
  CP_CAP_SGE,
  CP_CAP_SPARK,
  CP_CAP_SLURM,
  CP_CAP_KUBE,
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_MODULES,
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS,
  CP_CAP_AUTOSCALE_HYBRID,
  CP_CAP_AUTOSCALE_PRICE_TYPE,
  CP_CAP_SINGULARITY,
  CP_CAP_DESKTOP_NM
} from '../../pipelines/launch/form/utilities/parameters';
import AWSRegionTag from '../../special/AWSRegionTag';
import RunCapabilities, {
  dinDEnabled,
  noMachineEnabled,
  singularityEnabled,
  systemDEnabled,
  moduleEnabled,
  getRunCapabilitiesSkippedParameters,
  RUN_CAPABILITIES
} from '../../pipelines/launch/form/utilities/run-capabilities';

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
    onSubmit: PropTypes.func,
    readOnly: PropTypes.bool,
    onInitialized: PropTypes.func,
    executionEnvironmentDisabled: PropTypes.bool
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
    gridEngineEnabled: false,
    sparkEnabled: false,
    slurmEnabled: false,
    kubeEnabled: false,
    launchCluster: false,
    dinD: false,
    singularity: false,
    systemD: false,
    noMachine: false,
    module: false
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
          if (values.limitMounts) {
            params.push({
              name: CP_CAP_LIMIT_MOUNTS,
              value: values.limitMounts
            });
          }
          if (this.state.launchCluster && this.state.autoScaledCluster) {
            params.push({
              name: CP_CAP_SGE,
              type: 'boolean',
              value: true
            });
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
          }
          if (this.state.launchCluster && this.state.gridEngineEnabled) {
            params.push({
              name: CP_CAP_SGE,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.launchCluster && this.state.sparkEnabled) {
            params.push({
              name: CP_CAP_SPARK,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.launchCluster && this.state.slurmEnabled) {
            params.push({
              name: CP_CAP_SLURM,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.launchCluster && this.state.kubeEnabled) {
            params.push({
              name: CP_CAP_KUBE,
              type: 'boolean',
              value: true
            });
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
          if (this.state.dinD) {
            params.push({
              name: CP_CAP_DIND_CONTAINER,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.systemD) {
            params.push({
              name: CP_CAP_SYSTEMD_CONTAINER,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.singularity) {
            params.push({
              name: CP_CAP_SINGULARITY,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.noMachine) {
            params.push({
              name: CP_CAP_DESKTOP_NM,
              type: 'boolean',
              value: true
            });
          }
          if (this.state.module) {
            params.push({
              name: CP_CAP_MODULES,
              type: 'boolean',
              value: true
            });
          }

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
          is_spot: `${values.is_spot}` === 'true'
        };
        this.setState({pending: true}, async () => {
          if (this.props.onSubmit) {
            await this.props.onSubmit({
              endpoints: this.endpointControl ? values.endpoints : [],
              labels: this.state.labels,
              cpu: '1000mi',
              ram: '1Gi',
              allowSensitive: values.allowSensitive
            }, configuration);
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
      case 'instance_disk': return this.getDiskInitialValue();
      case 'allowSensitive': return this.getAllowSensitiveInitialValue();
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

  getCloudRegionInitialValue = () => {
    return this.props.configuration && this.props.configuration.cloudRegionId
      ? `${this.props.configuration.cloudRegionId}`
      : regionNotConfiguredValue;
  };

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
        state.maxNodesCount = props.configuration && props.configuration.parameters &&
          props.configuration.parameters[CP_CAP_AUTOSCALE_WORKERS]
            ? +props.configuration.parameters[CP_CAP_AUTOSCALE_WORKERS].value
            : 0;
        state.nodesCount = props.configuration.node_count;
        state.autoScaledCluster = props.configuration && autoScaledClusterEnabled(props.configuration.parameters);
        state.hybridAutoScaledClusterEnabled = props.configuration &&
          hybridAutoScaledClusterEnabled(props.configuration.parameters);
        state.gridEngineEnabled = props.configuration && gridEngineEnabled(props.configuration.parameters);
        state.sparkEnabled = props.configuration && sparkEnabled(props.configuration.parameters);
        state.slurmEnabled = props.configuration && slurmEnabled(props.configuration.parameters);
        state.kubeEnabled = props.configuration && kubeEnabled(props.configuration.parameters);
        state.autoScaledPriceType = props.configuration &&
          getAutoScaledPriceTypeValue(props.configuration.parameters);
        state.launchCluster = state.nodesCount > 0 || state.autoScaledCluster;
        state.dinD = dinDEnabled(props.configuration.parameters);
        state.singularity = singularityEnabled(props.configuration.parameters);
        state.systemD = systemDEnabled(props.configuration.parameters);
        state.noMachine = noMachineEnabled(props.configuration.parameters);
        state.module = moduleEnabled(props.configuration.parameters);
        this.defaultCommand = props.configuration && props.configuration.cmd_template
          ? props.configuration.cmd_template
          : this.defaultCommand;
        if (props.configuration && props.configuration.parameters) {
          for (let key in props.configuration.parameters) {
            if (!props.configuration.parameters.hasOwnProperty(key) ||
              getSystemParameterDisabledState(this, key)) {
              continue;
            }
            if (key === CP_CAP_LIMIT_MOUNTS) {
              this.defaultLimitMounts = props.configuration.parameters[CP_CAP_LIMIT_MOUNTS].value;
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
        this.setState(state);
      })();
    } else {
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

  @computed
  get selectedRunCapabilities () {
    const {dinD, singularity, systemD, noMachine, module} = this.state;

    return [
      dinD ? RUN_CAPABILITIES.dinD : false,
      singularity ? RUN_CAPABILITIES.singularity : false,
      systemD ? RUN_CAPABILITIES.systemD : false,
      noMachine ? RUN_CAPABILITIES.noMachine : false,
      module ? RUN_CAPABILITIES.module : false
    ].filter(Boolean);
  };

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
      return this.defaultLimitMounts !== this.props.form.getFieldValue('limitMounts');
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
    const gridEngineEnabledValue = this.props.configuration &&
      gridEngineEnabled(this.props.configuration.parameters);
    const sparkEnabledValue = this.props.configuration &&
      sparkEnabled(this.props.configuration.parameters);
    const slurmEnabledValue = this.props.configuration &&
      slurmEnabled(this.props.configuration.parameters);
    const kubeEnabledValue = this.props.configuration &&
      kubeEnabled(this.props.configuration.parameters);
    const autoScaledPriceTypeValue = this.props.configuration &&
      getAutoScaledPriceTypeValue(this.props.configuration.parameters);
    const launchCluster = nodesCount > 0 || autoScaledCluster;
    const additionalCapabilitiesChanged = () => {
      const dinD = dinDEnabled(this.props.configuration.parameters);
      const singularity = singularityEnabled(this.props.configuration.parameters);
      const systemD = systemDEnabled(this.props.configuration.parameters);
      const noMachine = noMachineEnabled(this.props.configuration.parameters)
      const module = moduleEnabled(this.props.configuration.parameters);
      return dinD !== this.state.dinD ||
        singularity !== this.state.singularity ||
        systemD !== this.state.systemD ||
        noMachine !== this.state.noMachine ||
        module !== this.state.module;
    };

    return configurationFormFieldChanged('is_spot') ||
      configurationFormFieldChanged('instance_size', 'instanceType') ||
      configurationFormFieldChanged('instance_disk', 'disk') ||
      configurationFormFieldChanged('allowSensitive') ||
      commandChanged() ||
      !compareArrays(toolEndpointArray, toolEndpointArrayFormValue) ||
      !compareArrays(toolLabelsArray, this.state.labels) ||
      (this.toolFormParameters && this.toolFormParameters.modified) ||
      (this.toolFormSystemParameters && this.toolFormSystemParameters.modified) ||
      !!launchCluster !== !!this.state.launchCluster ||
      !!autoScaledCluster !== !!this.state.autoScaledCluster ||
      !!hybridAutoScaledCluster !== !!this.state.hybridAutoScaledClusterEnabled ||
      !!gridEngineEnabledValue !== !!this.state.gridEngineEnabled ||
      !!sparkEnabledValue !== !!this.state.sparkEnabled ||
      !!slurmEnabledValue !== !!this.state.slurmEnabled ||
      !!kubeEnabledValue !== !!this.state.kubeEnabled ||
      autoScaledPriceTypeValue !== this.state.autoScaledPriceType ||
      (this.state.launchCluster && nodesCount !== this.state.nodesCount) ||
      (this.state.launchCluster && this.state.autoScaledCluster && maxNodesCount !== this.state.maxNodesCount) ||
      limitMountsFieldChanged() || cloudRegionFieldChanged() || additionalCapabilitiesChanged();
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
      nodesCount,
      maxNodesCount,
      gridEngineEnabled,
      sparkEnabled,
      slurmEnabled,
      kubeEnabled,
      autoScaledPriceType
    } = configuration;
    let {dinD, systemD} = this.state;
    if (kubeEnabled) {
      dinD = true;
      systemD = true;
    }
    this.setState({
      launchCluster,
      nodesCount,
      autoScaledCluster,
      hybridAutoScaledClusterEnabled,
      maxNodesCount,
      gridEngineEnabled,
      sparkEnabled,
      slurmEnabled,
      kubeEnabled,
      autoScaledPriceType,
      dinD,
      systemD
    }, () => {
      this.closeConfigureClusterDialog();
      const priceType = this.props.form.getFieldValue('is_spot') || this.getPriceTypeInitialValue();
      this.props.form.setFieldsValue({
        is_spot: this.correctPriceTypeValue(priceType)
      });
    });
  };

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
        ? lines.push(<span>{cpu} - {maxCPU} <b>CPU</b></span>)
        : lines.push(<span>{cpu} <b>CPU</b></span>);
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
          <div className={styles.summary}>
            {
              lines.map((l, index) => (
                <div key={index} className={styles.summaryItem}>
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
                  style={{
                    margin: '0 5px',
                    verticalAlign: 'middle',
                    height: 1,
                    backgroundColor: '#ccc'
                  }}>{'\u00A0'}</div>
              </td>
              <td style={{width: 1, whiteSpace: 'nowrap'}}><b>{text}</b></td>
              <td style={{width: '50%'}}>
                <div
                  style={{
                    margin: '0 5px',
                    verticalAlign: 'middle',
                    height: 1,
                    backgroundColor: '#ccc'
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
      dinD: capabilities.includes(RUN_CAPABILITIES.dinD),
      singularity: capabilities.includes(RUN_CAPABILITIES.singularity),
      systemD: capabilities.includes(RUN_CAPABILITIES.systemD),
      noMachine: capabilities.includes(RUN_CAPABILITIES.noMachine),
      module: capabilities.includes(RUN_CAPABILITIES.module)
    });
  };

  renderExecutionEnvironment = () => {
    const renderExecutionEnvironmentSection = () => {
      const {getFieldDecorator, getFieldValue} = this.props.form;
      let allowSensitive = getFieldValue('allowSensitive');
      if (allowSensitive === undefined) {
        allowSensitive = this.getAllowSensitiveInitialValue();
      }
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
                    {
                      this.allowedInstanceTypes
                        .map(t => t.instanceFamily)
                        .filter((familyName, index, array) => array.indexOf(familyName) === index)
                        .map(instanceFamily => {
                          return (
                            <Select.OptGroup key={instanceFamily || 'Other'} label={instanceFamily || 'Other'}>
                              {
                                this.allowedInstanceTypes
                                  .filter(t => t.instanceFamily === instanceFamily)
                                  .map(t =>
                                    <Select.Option
                                      title={`${t.name} (CPU: ${t.vcpu}, RAM: ${t.memory}${t.gpu ? `, GPU: ${t.gpu}` : ''})`}
                                      key={t.sku}
                                      value={t.name}>
                                      {t.name} (CPU: {t.vcpu}, RAM: {t.memory}{t.gpu ? `, GPU: ${t.gpu}` : ''})
                                    </Select.Option>
                                  )
                              }
                            </Select.OptGroup>
                          );
                        })
                    }
                  </Select>
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
                  <Input disabled={this.state.pending || this.props.readOnly}/>
                )}
              </Form.Item>
              <Form.Item {...this.formItemLayout} label="Limit mounts" style={{marginTop: 10, marginBottom: 10}}>
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
              <Form.Item {...this.formItemLayout} label="Allow sensitive storages" style={{marginTop: 10, marginBottom: 10}}>
                {getFieldDecorator('allowSensitive',
                  {
                    initialValue: this.getAllowSensitiveInitialValue(),
                    valuePropName: 'checked'
                  })(
                  <Checkbox
                    disabled={this.state.pending || this.props.readOnly || this.props.mode === 'version'}
                    onChange={this.correctSensitiveMounts}
                  />
                )}
              </Form.Item>
              <Row type="flex" align="middle" style={{marginBottom: 10}}>
                <Col xs={24} sm={{span: 12, offset: 6}}>
                  <Row type="flex" justify="end">
                    <a
                      onClick={this.openConfigureClusterDialog}
                      style={{color: '#777', textDecoration: 'underline'}}>
                      <Icon type="setting" /> {ConfigureClusterDialog.getConfigureClusterButtonDescription(this)}
                    </a>
                  </Row>
                </Col>
              </Row>
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
                    <Select.Option disabled key="divider" className={styles.selectOptionDivider} />
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
              <Form.Item
                {...this.formItemLayout}
                label="Run capabilities"
                style={{marginTop: 10, marginBottom: 10}}
              >
                <RunCapabilities
                  values={this.selectedRunCapabilities}
                  onChange={this.onRunCapabilitiesSelect}
                />
              </Form.Item>
              <ConfigureClusterDialog
                instanceName={this.props.form.getFieldValue('instanceType')}
                launchCluster={this.state.launchCluster}
                cloudRegionProvider={this.getCloudProvider()}
                autoScaledPriceType={this.state.autoScaledPriceType}
                autoScaledCluster={this.state.autoScaledCluster}
                hybridAutoScaledClusterEnabled={this.state.hybridAutoScaledClusterEnabled}
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
            skippedSystemParameters={[
              ...getSkippedSystemParametersList(this),
              ...getRunCapabilitiesSkippedParameters()
            ]}
            hiddenParameters={getRunCapabilitiesSkippedParameters()}
            value={this.defaultSystemProperties}
            onInitialized={this.onEditToolFormSystemParametersInitialized} />
          {this.renderSeparator('Custom parameters')}
          <EditToolFormParameters
            readOnly={!this.props.configuration || this.props.readOnly || this.state.pending}
            value={this.defaultProperties}
            onInitialized={this.onEditToolFormParametersInitialized} />
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

  render () {
    const {getFieldDecorator} = this.props.form;
    const isTool = this.props.mode === 'tool';

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
                  (this.toolFormParameters && !this.toolFormParameters.isValid)
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
    props.form.resetFields();
    if (this.editor) {
      this.editor.clear();
      this.editor.setValue(this.defaultCommand);
    }
  }

}
