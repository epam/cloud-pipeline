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
import {Modal, Input, Button, Form, message, Spin} from 'antd';
import classNames from 'classnames';

import {ResolveIp} from '../../../../../models/nat';
import {
  validateIP,
  validatePort,
  validateServerName
} from '../helpers';
import styles from './add-route-modal.css';

const FormItem = Form.Item;
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
    ports: {'port': undefined},
    ip: undefined,
    serverName: undefined,
    description: undefined,
    errors: {},
    pending: false,
    ipManualInput: false,
    ipResolveForServer: undefined
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
      errors = {}
    } = this.state;
    const flattenForm = {
      ip: ip,
      serverName: serverName,
      ...ports
    };
    return !!Object.values(errors)
      .map((status) => (status && status.error) || false)
      .filter(error => error)
      .length || Object.values(flattenForm).includes(undefined);
  }

  get portsDuplicates () {
    const portsObj = Object.values(this.state.ports || {})
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
      ip
    } = this.state;
    const {
      routes = []
    } = this.props;
    const currentIpRoutes = routes.filter(route => route.externalIp === ip);
    const portValues = Object
      .values(ports)
      .filter(o => !Number.isNaN(Number(o)))
      .map(o => Number(o))
      .concat(currentIpRoutes.map(o => Number(o.externalPort)));
    const errors = {
      serverName: validateServerName(serverName),
      ip: validateIP(ip),
      ...(
        Object
          .entries(ports)
          .map(([identifier, value]) => ({[identifier]: validatePort(value, portValues)}))
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
        [name]: value
      };
    } else if (name) {
      state[name] = value;
    }
    this.setState(state, () => this.validate());
  }

  addPortInput = () => {
    const {ports = {}} = this.state;
    const identifier = `port${AddRouteForm.getPortUID()}`;
    this.setState({
      ports: {
        ...ports,
        [identifier]: undefined
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
      ports: {[identifier]: undefined},
      ip: undefined,
      serverName: undefined,
      description: undefined,
      errors: {},
      ipManualInput: false,
      ipResolveForServer: undefined
    });
  }

  cancelForm = () => {
    this.props.onCancel();
    this.resetForm();
  }

  getValidationStatus = (key) => {
    const {errors} = this.state;
    return errors[key] && errors[key].error ? 'error' : 'success';
  }

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
        ip
      } = this.state;
      this.props.onAdd({
        ip,
        serverName,
        description,
        ports: Object.values(ports)
      });
      this.resetForm();
    }
    this.cancelForm();
  }

  onAutoResolveIp = () => {
    const {
      ipManualInput,
      ipResolveForServer,
      serverName
    } = this.state;
    if (!ipManualInput && ipResolveForServer !== serverName) {
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
    const {serverName, ip, description, pending} = this.state;
    const FormItemError = ({identifier}) => (
      <div
        className={
          classNames(
            styles.formItemValidation,
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
            <Form>
              <div className={styles.formItemContainer}>
                <span className={styles.title}>Server name:</span>
                <FormItem
                  className={styles.formItem}
                  validateStatus={this.getValidationStatus('serverName')}
                >
                  <Input
                    placeholder="Server name"
                    value={serverName}
                    onChange={this.handleChange('serverName')}
                    onBlur={this.onAutoResolveIp}
                  />
                </FormItem>
              </div>
              <FormItemError identifier="serverName" />
              <div className={styles.formItemContainer}>
                <span className={styles.title}>IP:</span>
                <FormItem
                  className={styles.formItem}
                  validateStatus={this.getValidationStatus('ip')}
                >
                  <Input
                    placeholder="127.0.0.1"
                    value={ip}
                    onChange={this.handleChange('ip')}
                  />
                </FormItem>
                <Button
                  disabled={
                    !serverName ||
                    (this.getValidationStatus('serverName') === 'error') ||
                    pending
                  }
                  style={{marginLeft: 5}}
                  onClick={this.onResolveIP}
                >
                  Resolve
                </Button>
              </div>
              <FormItemError identifier="ip" />
              {
                this.ports.map(([portIdentifier, port], index, ports) => ([
                  <div
                    key={`port-${portIdentifier}`}
                    className={styles.formItemContainer}
                  >
                    <span
                      className={styles.title}
                      style={{
                        visibility: index > 0 ? 'hidden' : undefined
                      }}
                    >
                      Port{this.ports.length > 1 ? 's' : ''}:
                    </span>
                    <FormItem
                      className={styles.formItem}
                      validateStatus={this.getValidationStatus(portIdentifier)}
                    >
                      <Input
                        value={port}
                        onChange={this.handleChange(portIdentifier)}
                      />
                    </FormItem>
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
                  </div>,
                  <FormItemError
                    key={`port-${portIdentifier}-validation`}
                    identifier={portIdentifier}
                  />
                ]))
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
                <FormItem
                  className={styles.formItem}
                  validateStatus={this.getValidationStatus('description')}
                >
                  <Input
                    placeholder="Comment"
                    value={description}
                    onChange={this.handleChange('description')}
                  />
                </FormItem>
              </div>
            </Form>
          </Spin>
        </div>
      </Modal>
    );
  }
}
