/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Checkbox,
  Modal,
  Icon,
  Input,
  Button,
  message,
  Popover,
  Spin,
  Select
} from 'antd';
import classNames from 'classnames';

import protocols from '../protocols';
import * as portUtilities from '../ports-utilities';
import {ResolveIp} from '../../../../../models/nat';
import {
  validateIP,
  validatePort,
  validateServerName,
  validateDescription
} from '../helpers';
import styles from './add-route-modal.css';

function Hint ({className, style, children}) {
  if (!children) {
    return null;
  }
  return (
    <Popover
      placement="right"
      content={(
        <div
          style={{
            maxWidth: 200
          }}
        >
          {children}
        </div>
      )}
    >
      <Icon
        type="question-circle"
        className={className}
        style={{
          ...(style || {}),
          cursor: 'pointer'
        }}
      />
    </Popover>
  );
}

export default class AddRouteForm extends React.Component {
  static portUID = 0;
  static getPortUID = () => {
    AddRouteForm.portUID += 1;
    return AddRouteForm.portUID;
  };

  static propTypes = {
    visible: PropTypes.bool,
    onAdd: PropTypes.func,
    onCancel: PropTypes.func,
    routes: PropTypes.array
  };

  state = {
    ports: {'port': {value: undefined, protocol: protocols.TCP}},
    ip: undefined,
    serverName: undefined,
    description: undefined,
    errors: {},
    pending: false,
    ipManualInput: false,
    ipResolveForServer: undefined,
    useIP: false
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.visible !== prevProps.visible && this.props.visible) {
      this.resetForm();
    }
  }

  get ports () {
    return Object.entries(this.state.ports);
  }

  get formIsInvalid () {
    const {
      ports = {},
      ip,
      serverName,
      errors = {},
      useIP
    } = this.state;
    const flattenForm = {
      ip: ip,
      serverName: serverName,
      ...ports
    };
    if (!useIP) {
      delete flattenForm.ip;
    }
    return !!Object.values(errors)
      .map((status) => (status && status.error) || false)
      .filter(error => error)
      .length || Object.values(flattenForm).includes(undefined);
  }

  get portsDuplicates () {
    const portsObj = Object.values(this.state.ports || {})
      .map(({value}) => value)
      .reduce((r, c) => {
        r[c] = (r[c] || 0) + 1;
        return r;
      }, {});
    const duplicates = Object.entries(portsObj).filter(([key, value]) => value > 1);
    return Object.fromEntries(duplicates);
  }

  validate = () => {
    const {
      ports = {},
      serverName,
      ip,
      useIP,
      description
    } = this.state;
    const {
      routes = []
    } = this.props;
    const currentIpRoutes = routes
      .filter(route => useIP
        ? route.externalIp === ip
        : serverName === route.externalIp);
    const getOtherPorts = identifier => Object
      .keys(ports)
      .filter(o => o !== identifier)
      .map(o => (ports[o] || {}).value)
      .concat(
        currentIpRoutes
          .map(o => o.externalPorts || [])
          .reduce((r, c) => ([...r, ...c]), [])
      );
    const errors = {
      serverName: validateServerName(serverName),
      ip: validateIP(ip, !useIP),
      description: validateDescription(description),
      ...(
        Object
          .entries(ports)
          .map(([identifier, {value}]) => ({
            [identifier]: validatePort(value, getOtherPorts(identifier))
          }))
          .reduce((r, c) => ({...r, ...c}), {})
      )
    };
    this.setState({errors});
  };

  handleChange = (name) => (event) => {
    const {value} = event.target;
    const {ports = {}} = this.state;
    const state = {};
    if (name === 'ip') {
      state.ipManualInput = true;
    }
    if (name && name.startsWith('port')) {
      state.ports = {
        ...ports,
        [name]: {...ports[name], value}
      };
    } else if (name) {
      state[name] = value;
    }
    this.setState(state, () => this.validate());
  }

  handleProtocolChange = (portIdentificator) => (value) => {
    const ports = {...this.state.ports};
    const state = {};
    if (ports[portIdentificator]) {
      ports[portIdentificator].protocol = value;
    }
    state.ports = {...ports};
    this.setState(state);
  }

  handleUseIP = (event) => {
    const {checked} = event.target;
    this.setState({
      useIP: checked
    }, () => {
      this.onAutoResolveIp()
        .then(() => this.validate());
    });
  }

  addPortInput = () => {
    const {ports = {}} = this.state;
    const identifier = `port${AddRouteForm.getPortUID()}`;
    this.setState({
      ports: {
        ...ports,
        [identifier]: {value: undefined, protocol: protocols.TCP}
      }
    }, () => this.validate());
  }

  removePortInput = (name) => {
    const {ports = {}} = this.state;
    delete ports[name];
    this.setState({
      ports: {...ports}
    }, () => this.validate());
  }

  resetForm = () => {
    const identifier = `port${AddRouteForm.getPortUID()}`;
    this.setState({
      ports: {[identifier]: {value: undefined, protocol: protocols.TCP}},
      ip: undefined,
      serverName: undefined,
      description: undefined,
      errors: {},
      ipManualInput: false,
      ipResolveForServer: undefined,
      useIP: false
    });
  }

  cancelForm = () => {
    this.props.onCancel();
    this.resetForm();
  }

  isError = (key) => {
    const {errors} = this.state;
    return errors[key] && errors[key].error;
  }

  getValidationClassName = (key) => classNames({
    'cp-error': this.isError(key)
  });

  getValidationMessage = (key) => {
    const {errors} = this.state;
    return (errors[key] && errors[key].message) || '';
  }

  onSubmit = async () => {
    if (!this.formIsInvalid) {
      const {
        ports = {},
        serverName,
        description,
        ip,
        useIP
      } = this.state;
      const portValues = [];
      for (const entryValue of Object.values(ports)) {
        const {value, protocol} = entryValue;
        for (const port of portUtilities.parsePorts(value)) {
          portValues.push({port, protocol});
        }
      }
      this.props.onAdd({
        ip: useIP ? ip : serverName,
        serverName,
        description,
        ports: portValues
      });
      this.resetForm();
    }
    this.cancelForm();
  }

  onAutoResolveIp = () => {
    const {
      ipManualInput,
      ipResolveForServer,
      serverName,
      useIP
    } = this.state;
    if (useIP && !ipManualInput && ipResolveForServer !== serverName) {
      return this.onResolveIP();
    }
    return Promise.resolve();
  };

  onResolveIP = async () => {
    const {serverName} = this.state;
    try {
      const request = new ResolveIp(serverName);
      this.setState({
        pending: true
      });
      await request.fetch();
      if (request.error) {
        message.error(request.error, 5);
        this.setState({
          pending: false
        });
      }
      if (request.loaded && request.value && request.value.length) {
        const {errors = {}} = this.state;
        this.setState({
          ip: request.value[0],
          pending: false,
          errors: {...errors, ip: false},
          ipResolveForServer: serverName
        });
      }
    } catch (e) {
      message.error(e.message, 5);
      this.setState({
        pending: false
      });
    }
  }

  renderFooter = () => (
    <div className={styles.footerButtonsContainer}>
      <Button
        onClick={this.cancelForm}
      >
        CANCEL
      </Button>
      <Button
        type="primary"
        disabled={this.formIsInvalid}
        onClick={this.onSubmit}
      >
        ADD
      </Button>
    </div>
  );

  render () {
    const {onCancel, visible} = this.props;
    const {
      serverName,
      ip,
      description,
      pending,
      useIP
    } = this.state;
    const FormItemError = ({identifier}) => (
      <div
        className={
          classNames(
            styles.formItemValidation,
            'cp-error',
            {
              [styles.invalid]: !!this.getValidationMessage(identifier)
            }
          )
        }
      >
        {this.getValidationMessage(identifier)}
      </div>
    );
    return (
      <Modal
        title="Add new route"
        visible={visible}
        onCancel={onCancel}
        footer={this.renderFooter()}
      >
        <div>
          <Spin spinning={pending}>
            <div>
              <div className={styles.formItemContainer}>
                <span className={styles.title}>Server name:</span>
                <Input
                  className={
                    classNames(
                      styles.formItem,
                      this.getValidationClassName('serverName')
                    )
                  }
                  placeholder="Server name"
                  value={serverName}
                  onChange={this.handleChange('serverName')}
                  onBlur={this.onAutoResolveIp}
                />
                <Hint
                  style={{marginLeft: 5}}
                >
                  {/* eslint-disable-next-line */}
                  Domain name is composed of a sequence of <b>lowercased</b> labels concatenated with dots.<br />
                  Each label must be from 1 to 63 characters long.<br />
                  {/* eslint-disable-next-line */}
                  The entire domain name, including the delimiting dots, has a maximum of 253 characters.<br />
                  {/* eslint-disable-next-line */}
                  Supported characters are <b>"a"</b>-<b>"z"</b> (<b>lowercase</b>), digits <b>"0"</b>-<b>"9"</b>,
                  hyphen (<b>"-"</b>) and underscore (<b>"_"</b>).
                  <br />
                  {/* eslint-disable-next-line */}
                  Each label must starts with a character (<b>"a"-"z"</b>) and must not ends with a hyphen (<b>"-"</b>)
                </Hint>
              </div>
              <FormItemError identifier="serverName" />
              <div className={styles.formItemContainer}>
                <span
                  className={styles.title}
                >
                  {'\u00A0'}
                </span>
                <Checkbox
                  className={styles.formItem}
                  checked={useIP}
                  onChange={this.handleUseIP}
                >
                  Specify IP address
                </Checkbox>
              </div>
              {
                useIP && (
                  <div className={styles.formItemContainer}>
                    <span className={styles.title}>IP:</span>
                    <Input
                      placeholder="127.0.0.1"
                      className={
                        classNames(
                          styles.formItem,
                          this.getValidationClassName('ip')
                        )
                      }
                      value={ip}
                      onChange={this.handleChange('ip')}
                    />
                    <Button
                      disabled={
                        !serverName ||
                        this.isError('serverName') ||
                        pending
                      }
                      style={{marginLeft: 5}}
                      onClick={this.onResolveIP}
                    >
                      Resolve
                    </Button>
                  </div>
                )
              }
              <FormItemError identifier="ip" />
              {
                this.ports.map(([portIdentifier, port], index, ports) => (
                  <div
                    key={`port-${portIdentifier}`}
                    className={
                      classNames(
                        styles.portFormItemContainer,
                        'cp-nat-route-port-control'
                      )
                    }
                  >
                    <div className={styles.formItemContainer}>
                      <span className={styles.title}>Port:</span>
                      <Input
                        className={
                          classNames(
                            styles.formItem,
                            this.getValidationClassName(portIdentifier)
                          )
                        }
                        value={port.value}
                        onChange={this.handleChange(portIdentifier)}
                      />
                      {
                        ports.length > 1 && (
                          <Button
                            type="danger"
                            icon="delete"
                            onClick={() => this.removePortInput(portIdentifier)}
                            style={{marginLeft: 5}}
                          />
                        )
                      }
                    </div>
                    <FormItemError
                      key={`port-${portIdentifier}-validation`}
                      identifier={portIdentifier}
                    />
                    <div className={styles.formItemContainer}>
                      <span className={styles.title}>Protocol:</span>
                      <Select
                        className={styles.formItem}
                        value={port.protocol}
                        onChange={this.handleProtocolChange(portIdentifier)}
                      >
                        <Select.Option value={protocols.TCP}>TCP</Select.Option>
                        <Select.Option value={protocols.UDP}>UDP</Select.Option>
                      </Select>
                    </div>
                  </div>
                ))
              }
              <div className={styles.addButtonContainer}>
                <Button
                  icon="plus"
                  onClick={this.addPortInput}
                >
                  Add port
                </Button>
              </div>
              <div className={styles.formItemContainer}>
                <span className={styles.title}>Comment:</span>
                <Input
                  className={
                    classNames(
                      styles.formItem,
                      this.getValidationClassName('description')
                    )
                  }
                  placeholder="Comment"
                  value={description}
                  onChange={this.handleChange('description')}
                />
              </div>
              <FormItemError identifier="description" />
            </div>
          </Spin>
        </div>
      </Modal>
    );
  }
}
