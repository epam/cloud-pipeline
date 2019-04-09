/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
  Col,
  Collapse,
  Form,
  Icon,
  Input,
  Row,
  Select,
  Tag
} from 'antd';
import ToolEndpointsFormItem from '../elements/ToolEndpointsFormItem';
import CodeEditor from '../../special/CodeEditor';
import EditToolFormParameters from './EditToolFormParameters';
import styles from '../Tools.css';
import {names} from '../../../models/utils/ContextualPreference';
import {
  LIMIT_MOUNTS_PARAMETER,
  LimitMountsInput
} from '../../pipelines/launch/form/LimitMountsInput';
import {
  autoScaledClusterEnabled,
  CP_CAP_SGE,
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS,
  ConfigureClusterDialog,
  getSkippedSystemParametersList,
  getSystemParameterDisabledState
} from '../../pipelines/launch/form/utilities/launch-cluster';

const Panels = {
  endpoints: 'endpoints',
  attributes: 'attributes',
  executionDefaults: 'exec',
  parameters: 'parameters'
};

@Form.create()
@inject('toolInstanceTypes', 'runDefaultParameters')
@inject(({allowedInstanceTypes}, props) => ({
  allowedInstanceTypes: allowedInstanceTypes.getAllowedTypes(props.toolId)
}))
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
    configuration: PropTypes.object,
    onSubmit: PropTypes.func,
    readOnly: PropTypes.bool,
    onInitialized: PropTypes.func,
    executionEnvironmentDisabled: PropTypes.bool
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
    launchCluster: false
  };

  @observable defaultLimitMounts;
  @observable defaultCommand;
  @observable defaultProperties;
  @observable defaultSystemProperties;

  endpointControl;

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
              name: LIMIT_MOUNTS_PARAMETER,
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
              ram: '1Gi'
            }, configuration);
          }
          this.setState({pending: false});
        });
      } else if (err) {
        this.setState({openedPanels: [Panels.executionDefaults]});
      }
    });
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
        state.launchCluster = state.nodesCount > 0 || state.autoScaledCluster;
        this.defaultCommand = props.configuration && props.configuration.cmd_template
          ? props.configuration.cmd_template
          : this.defaultCommand;
        if (props.configuration && props.configuration.parameters) {
          for (let key in props.configuration.parameters) {
            if (!props.configuration.parameters.hasOwnProperty(key) ||
              getSystemParameterDisabledState(this, key)) {
              continue;
            }
            if (key === LIMIT_MOUNTS_PARAMETER) {
              this.defaultLimitMounts = props.configuration.parameters[LIMIT_MOUNTS_PARAMETER].value;
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
    this.props.onInitialized && this.props.onInitialized(this);
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.tool !== this.props.tool || nextProps.configuration !== this.props.configuration) {
      this.reset(nextProps);
    }
  }

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
    if (!this.props.toolInstanceTypes.loaded) {
      return [];
    }
    const instanceTypes = [];
    for (let i = 0; i < (this.props.toolInstanceTypes.value || []).length; i++) {
      const instanceType = this.props.toolInstanceTypes.value[i];
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
    if (!this.props.allowedInstanceTypes.loaded || !this.props.allowedInstanceTypes.value[names.allowedPriceTypes]) {
      return [];
    }
    const priceTypes = [];
    for (let i = 0; i < (this.props.allowedInstanceTypes.value[names.allowedPriceTypes] || []).length; i++) {
      const isSpot = this.props.allowedInstanceTypes.value[names.allowedPriceTypes][i].toLowerCase() === 'spot';
      priceTypes.push({
        isSpot,
        name: isSpot ? 'Spot' : 'On-demand'
      });
    }
    return priceTypes;
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
      const toolField = this.props.configuration ? this.props.configuration[field] : undefined;
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
    const launchCluster = nodesCount > 0 || autoScaledCluster;
    return configurationFormFieldChanged('is_spot') ||
      configurationFormFieldChanged('instance_size', 'instanceType') ||
      configurationFormFieldChanged('instance_disk', 'disk') ||
      commandChanged() ||
      !compareArrays(toolEndpointArray, toolEndpointArrayFormValue) ||
      !compareArrays(toolLabelsArray, this.state.labels) ||
      (this.toolFormParameters && this.toolFormParameters.modified) ||
      (this.toolFormSystemParameters && this.toolFormSystemParameters.modified) ||
      launchCluster !== this.state.launchCluster ||
      nodesCount !== this.state.nodesCount ||
      maxNodesCount !== this.state.maxNodesCount ||
      autoScaledCluster !== this.state.autoScaledCluster ||
      limitMountsFieldChanged();
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
    const {launchCluster, autoScaledCluster, nodesCount, maxNodesCount} = configuration;
    this.setState({launchCluster, nodesCount, autoScaledCluster, maxNodesCount},
      this.closeConfigureClusterDialog);
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

  renderExecutionEnvironment = () => {
    const renderExecutionEnvironmentSection = () => {
      const {getFieldDecorator} = this.props.form;
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
                    initialValue: this.correctInstanceTypeValue(
                      (this.props.configuration && this.props.configuration.instance_size) ||
                      (this.props.tool && this.props.tool.instanceType)
                    )
                  })(
                  <Select
                    disabled={this.state.pending || this.props.readOnly}
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
                    initialValue: this.correctPriceTypeValue(
                      this.props.configuration && this.props.configuration.is_spot !== undefined
                      ? `${this.props.configuration.is_spot}`
                      : `${this.props.defaultPriceTypeIsSpot}`
                    )
                  })(
                    <Select disabled={this.state.pending || this.props.readOnly}>
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
                    initialValue: (this.props.configuration && this.props.configuration.instance_disk) ||
                    (this.props.tool && this.props.tool.disk) ||
                    undefined
                  })(
                  <Input disabled={this.state.pending || this.props.readOnly}/>
                )}
              </Form.Item>
              <Form.Item {...this.formItemLayout} label="Limit mounts" style={{marginTop: 10, marginBottom: 10}}>
                {getFieldDecorator('limitMounts',
                  {
                    initialValue: this.defaultLimitMounts
                  })(
                  <LimitMountsInput disabled={this.state.pending || this.props.readOnly} />
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
              <ConfigureClusterDialog
                instanceName={this.props.form.getFieldValue('instanceType')}
                launchCluster={this.state.launchCluster}
                autoScaledCluster={this.state.autoScaledCluster}
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
            skippedSystemParameters={getSkippedSystemParametersList(this) || []}
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

    return (
      <Form>
        {
          this.props.tool && this.renderSeparator('Tool endpoints')
        }
        {
          this.props.tool &&
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
        {this.props.tool && this.renderSeparator('Tool attributes')}
        {
          this.props.tool &&
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
