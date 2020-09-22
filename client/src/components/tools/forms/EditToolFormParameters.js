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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import {Button, Checkbox, Col, Dropdown, Icon, Input, Menu, Row, Select} from 'antd';
import BucketBrowser from '../../pipelines/launch/dialogs/BucketBrowser';
import SystemParametersBrowser from '../../pipelines/launch/dialogs/SystemParametersBrowser';
import {CP_CAP_LIMIT_MOUNTS} from '../../pipelines/launch/form/utilities/parameters';
import styles from './EditToolFormParameters.css';

@observer
export default class EditToolFormParameters extends React.Component {

  static propTypes = {
    value: PropTypes.object,
    onInitialized: PropTypes.func,
    readOnly: PropTypes.bool,
    isSystemParameters: PropTypes.bool,
    getSystemParameterDisabledState: PropTypes.func,
    skippedSystemParameters: PropTypes.array,
    hiddenParameters: PropTypes.array
  };

  state = {
    parameters: [],
    validation: [],
    bucketBrowserParameter: null,
    systemParameterBrowserVisible: false
  };

  @computed
  get skippedSystemParameters () {
    if (this.props.skippedSystemParameters && this.props.skippedSystemParameters.length) {
      return this.props.skippedSystemParameters;
    }
    return [];
  }

  openBucketBrowser = (index) => {
    this.setState({
      bucketBrowserParameter: index
    });
  };

  closeBucketBrowser = () => {
    this.setState({
      bucketBrowserParameter: null
    });
  };

  selectButcketPath = (path) => {
    if (this.state.bucketBrowserParameter !== null) {
      const parameters = this.state.parameters;
      parameters[this.state.bucketBrowserParameter].value = path;
      this.setState({
        parameters,
        bucketBrowserParameter: null
      });
    }
  };

  addParameter = (parameter) => {
    const parameters = this.state.parameters;
    parameters.push(parameter);
    const validation = this.validate(parameters);
    this.setState({
      parameters,
      validation
    });
  };

  renderAddParameterButton = () => {
    if (this.props.isSystemParameters) {
      return (
        <Button
          onClick={this.openSystemParameterBrowser}
          disabled={this.props.readOnly} >
          Add system parameters
        </Button>
      );
    }

    const onSelect = ({key}) => {
      this.addParameter(
        {type: key, defaultValue: key === 'boolean' ? 'true' : undefined}
      );
    };

    const parameterTypeMenu = (
      <Menu selectedKeys={[]} onClick={onSelect}>
        <Menu.Item id="add-string-parameter" key="string">String parameter</Menu.Item>
        <Menu.Item id="add-boolean-parameter" key="boolean">Boolean parameter</Menu.Item>
        <Menu.Item id="add-path-parameter" key="path">Path parameter</Menu.Item>
        <Menu.Item id="add-input-parameter" key="input">Input path parameter</Menu.Item>
        <Menu.Item id="add-output-parameter" key="output">Output path parameter</Menu.Item>
        <Menu.Item id="add-common-parameter" key="common">Common path parameter</Menu.Item>
      </Menu>
    );

    return (
      <Button.Group>
        <Button
          disabled={this.props.readOnly}
          onClick={() => this.addParameter({type: 'string'})}
          id="add-parameter-button">
          Add parameter
        </Button>
        {
          !this.props.readOnly
            ? (
            <Dropdown overlay={parameterTypeMenu} placement="bottomRight">
              <Button
                id="add-parameter-dropdown-button"
                disabled={this.props.readOnly}>
                <Icon type="down" />
              </Button>
            </Dropdown>
          ) : undefined
        }
      </Button.Group>
    );
  };

  renderStringParameterInput = (parameter, index, onChange, isError) => {
    return (
      <Input
        disabled={this.props.readOnly}
        value={parameter.value}
        onChange={onChange}
        style={Object.assign(
          {width: '100%', marginLeft: 5},
          isError ? {borderColor: 'red'} : {}
        )} />
    );
  };

  renderSelectParameterInput = (parameter, index, onChange, isError) => {
    return (
      <Select
        disabled={this.props.readOnly}
        value={parameter.value}
        onChange={v => onChange({target: {value: v}})}
        style={Object.assign(
          {width: '100%', marginLeft: 5},
          isError ? {borderColor: 'red'} : {}
        )}>
        {
          (parameter.enum || []).map(e => {
            return (
              <Select.Option key={e} value={e}>{e}</Select.Option>
            );
          })
        }
      </Select>
    );
  };

  renderBooleanParameterInput = (parameter, index, onChange, isError) => {
    return (
      <Checkbox
        disabled={this.props.readOnly}
        checked={`${parameter.value}` === 'true'}
        style={Object.assign({marginLeft: 5, marginTop: 4}, isError ? {color: 'red'} : {})}
        onChange={onChange}>
        {parameter.value ? 'Enabled' : 'Disabled'}
      </Checkbox>
    );
  };

  renderPathParameterInput = (parameter, index, onChange, isError) => {
    let icon;
    switch (parameter.type) {
      case 'input': icon = 'download'; break;
      case 'output': icon = 'upload'; break;
      case 'common': icon = 'select'; break;
      default: icon = 'folder'; break;
    }
    return (
      <Input
        disabled={this.props.readOnly}
        style={Object.assign(
          {width: '100%', marginLeft: 5, top: 0},
          isError ? {borderColor: 'red'} : {}
        )}
        value={parameter.value}
        onChange={onChange}
        addonBefore={
          <div style={{cursor: 'pointer'}} onClick={() => this.openBucketBrowser(index)}>
            <Icon type={icon} />
          </div>}
        placeholder="Path"
      />
    );
  };

  renderParameter = (parameter, index) => {
    if (this.props.isSystemParameters && this.props.getSystemParameterDisabledState &&
      this.props.getSystemParameterDisabledState(parameter.name || '')) {
      return null;
    }
    if (this.props.isSystemParameters && this.props.hiddenParameters &&
      this.props.hiddenParameters.includes(parameter.name || '')) {
      return null;
    }

    const onChange = (property) => (e) => {
      const parameters = this.state.parameters;
      if ((parameters[index].type || '').toLowerCase() === 'boolean' && property === 'value') {
        parameters[index][property] = e.target.checked;
      } else {
        parameters[index][property] = e.target.value;
      }
      const validation = this.validate(parameters);
      this.setState({parameters, validation});
    };
    const onRemoveParameter = () => {
      const parameters = this.state.parameters;
      parameters.splice(index, 1);
      const validation = this.validate(parameters);
      this.setState({
        parameters,
        validation
      });
    };
    let renderParameterInputFn;
    switch ((parameter.type || '').toLowerCase()) {
      case 'path':
      case 'output':
      case 'input':
      case 'common':
        renderParameterInputFn = this.renderPathParameterInput;
        break;
      case 'boolean':
        renderParameterInputFn = this.renderBooleanParameterInput;
        break;
      default:
        if (parameter.enum && parameter.enum.length) {
          renderParameterInputFn = this.renderSelectParameterInput;
        } else {
          renderParameterInputFn = this.renderStringParameterInput;
        }
        break;
    }
    let nameError;
    if (this.state.validation[index] && this.state.validation[index].error) {
      nameError = this.state.validation[index].error;
    }
    let valueError;
    if (this.state.validation[index] && this.state.validation[index].errorValue) {
      valueError = this.state.validation[index].errorValue;
    }
    return (
      <Row key={index} type="flex" style={{marginTop: 5, marginBottom: 5}} align="top">
        <Col offset={3} span={3} style={{textAlign: 'right', display: 'flex', flexDirection: 'column'}}>
          <Input
            disabled={this.props.readOnly || this.props.isSystemParameters}
            className={`${styles.parameter} ${styles.parameterName} ${nameError ? styles.wrong : ''}`}
            value={parameter.name}
            onChange={onChange('name')}
            style={Object.assign(
              {width: '100%', marginRight: 5},
              this.props.isSystemParameters ? {color: '#666'} : {}
            )} />
          {
            nameError && <span className={styles.error}>{nameError}</span>
          }
        </Col>
        <Col span={12} style={{display: 'flex', flexDirection: 'column'}}>
          {renderParameterInputFn && renderParameterInputFn(parameter, index, onChange('value'), !!valueError)}
          {
            valueError && <span className={styles.error} style={{marginLeft: 5}}>{valueError}</span>
          }
        </Col>
        <Col>
          {
            !this.props.readOnly &&
            <Icon
              id="remove-parameter-button"
              className="dynamic-delete-button"
              type="minus-circle-o"
              style={{cursor: 'pointer', marginLeft: 20, marginTop: 7}}
              onClick={onRemoveParameter}
            />
          }
        </Col>
      </Row>
    );
  };

  openSystemParameterBrowser = () => {
    this.setState({systemParameterBrowserVisible: true});
  };

  closeSystemParameterBrowser = () => {
    this.setState({systemParameterBrowserVisible: false});
  };

  render () {
    return (
      <div>
        {this.state.parameters.map(this.renderParameter)}
        <Row type="flex" justify="space-around" align="middle">
          {this.renderAddParameterButton()}
        </Row>
        <BucketBrowser
          multiple
          onSelect={this.selectButcketPath}
          onCancel={this.closeBucketBrowser}
          visible={this.state.bucketBrowserParameter !== null}
          path={
            this.state.bucketBrowserParameter !== null
              ? this.state.parameters[this.state.bucketBrowserParameter].value
              : null
          }
          showOnlyFolder={
            this.state.bucketBrowserParameter !== null
              ? (this.state.parameters[this.state.bucketBrowserParameter].type || '').toLowerCase() === 'output'
              : false
          }
          checkWritePermissions={
            this.state.bucketBrowserParameter !== null
              ? (this.state.parameters[this.state.bucketBrowserParameter].type || '').toLowerCase() === 'output'
              : false
          }
          bucketTypes={['AZ', 'S3', 'GS', 'DTS', 'NFS']} />
        <SystemParametersBrowser
          visible={this.state.systemParameterBrowserVisible}
          onCancel={this.closeSystemParameterBrowser}
          onSave={(parameters) => {
            const p = this.state.parameters;
            p.push(...parameters.map(param => ({
              name: param.name,
              type: param.type,
              value: param.defaultValue
            })));
            const validation = this.validate(p);
            this.setState({
              parameters: p,
              systemParameterBrowserVisible: false,
              validation
            });
          }}
          notToShow={[
            ...this.state.parameters.map(p => p.name),
            CP_CAP_LIMIT_MOUNTS,
            ...this.skippedSystemParameters
          ]}
        />
      </div>
    );
  }

  reset = () => {
    const mapParameter = p => ({name: p.name, value: p.value, type: p.type});
    this.setState({
      parameters: (this.props.value || []).map(mapParameter)
    });
  };

  validate = (parameters, updateState = false) => {
    parameters = (parameters || this.state.parameters);
    const validation = parameters.map(() => {
      return {};
    });
    for (let i = 0; i < parameters.length; i++) {
      if (!parameters[i].name) {
        validation[i].error = 'Parameter name is required';
      } else if ((parameters[i].name || '').toUpperCase() === CP_CAP_LIMIT_MOUNTS) {
        validation[i].error = 'Parameter name is reserved';
      } else if (parameters
          .map(p => (p.name || '').toLowerCase())
          .filter(n => n === (parameters[i].name || '').toLowerCase()).length > 1) {
        validation[i].error = 'Parameter name should be unique';
      } else if (this.props.isSystemParameters &&
        (((parameters[i].type || '').toLowerCase() === 'boolean' && parameters[i].value === undefined) ||
          ((parameters[i].type || '').toLowerCase() !== 'boolean' && !parameters[i].value))
      ) {
        validation[i].errorValue = 'Parameter value is required';
      }
    }
    if (updateState) {
      this.setState({validation});
    }
    return validation;
  };

  componentDidMount () {
    this.props.onInitialized && this.props.onInitialized(this);
  }

  getValues = () => {
    return this.state.parameters.filter(p => (
      !this.props.isSystemParameters && !this.props.hiddenParameters.includes(p.name)
    ));
  };

  @computed
  get isValid () {
    return this.state.validation.filter(v => !!v.error || !!v.errorValue).length === 0;
  }

  @computed
  get modified () {
    const propsValue = this.props.value || [];
    const currentValue = this.state.parameters || [];
    if (propsValue.length !== currentValue.length) {
      return true;
    }
    for (let i = 0; i < propsValue.length; i++) {
      const propsValueItem = propsValue[i];
      const currentValueItem = currentValue[i];
      if (propsValueItem.name !== currentValueItem.name || propsValueItem.value !== currentValueItem.value) {
        return true;
      }
    }
    return false;
  }

}
