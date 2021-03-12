/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Icon,
  Input,
  InputNumber,
  Modal
} from 'antd';
import classNames from 'classnames';
import styles from './HostedAppConfiguration.css';

class HostedAppConfigurationDialog extends React.Component {
  state = {
    service: undefined,
    ports: [],
    serviceError: undefined,
    portsErrors: [],
    portsGlobalError: undefined
  };

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.visible && this.props.visible !== prevProps.visible) {
      this.updateState();
    }
  }

  updateState = () => {
    const {configuration} = this.props;
    if (!configuration) {
      this.setState({
        service: undefined,
        ports: [{port: undefined, targetPort: undefined}],
        serviceError: undefined,
        portsErrors: [undefined],
        portsGlobalError: undefined
      });
    } else {
      const {
        service,
        ports = []
      } = configuration;
      this.setState({
        service,
        ports: ports.slice().map(p => ({...p, targetPortTouched: true})),
        serviceError: undefined,
        portsErrors: ports.map(p => undefined),
        portsGlobalError: undefined
      });
    }
  };

  validate = () => {
    return new Promise((resolve) => {
      const {service, ports} = this.state;
      let serviceError;
      const portsGlobalError = ports.length === 0
        ? 'You must provide at least one ports configuration'
        : undefined;
      if (!service) {
        serviceError = `Service name must be specified`;
      } else if (!/^[a-z]([-a-z0-9]*[a-z0-9])?$/.test(service)) {
        // eslint-disable-next-line
        serviceError = 'Service name must start with letter and contain letters, digits or "-" symbols';
      }
      const checkPort = port => port !== undefined &&
        port !== null &&
        port !== '' &&
        !Number.isNaN(Number(port));
      const validatePorts = (portsConfiguration, index, array) => {
        const {port, targetPort} = portsConfiguration || {};
        let portError = checkPort(port)
          ? undefined
          : 'Port is invalid';
        let targetPortError = checkPort(targetPort)
          ? undefined
          : 'Target port is invalid';
        const portNonUnique = array.filter(p => p.port === port).length > 1;
        if (portNonUnique && !portError) {
          portError = `Port ${port} is already configured`;
        }
        if (portError || targetPortError) {
          return {
            port: portError,
            targetPort: targetPortError
          };
        }
        return undefined;
      };
      const portsErrors = (ports || []).map(validatePorts);
      this.setState({
        serviceError,
        portsErrors,
        portsGlobalError
      }, () => {
        resolve(!serviceError && portsErrors.filter(Boolean).length === 0 && !portsGlobalError);
      });
    });
  };

  onChangeServiceName = (e) => {
    this.setState({
      service: e.target.value
    }, this.validate);
  };

  onChangePort = (index) => (e) => {
    const {ports} = this.state;
    if (ports.length > index) {
      const port = ports[index];
      port.port = e;
      if (!port.targetPortTouched) {
        port.targetPort = e;
      }
      this.setState({ports}, this.validate);
    }
  };

  onChangeTargetPort = (index) => (e) => {
    const {ports} = this.state;
    if (ports.length > index) {
      const port = ports[index];
      port.targetPort = e;
      port.targetPortTouched = true;
      this.setState({ports}, this.validate);
    }
  };

  onAddPorts = () => {
    const {ports} = this.state;
    ports.push({port: undefined, targetPort: undefined});
    this.setState({ports}, this.validate);
  };

  onRemovePorts = (index) => () => {
    const {ports} = this.state;
    if (ports.length > index) {
      ports.splice(index, 1);
      this.setState({ports}, this.validate);
    }
  };

  handleSave = () => {
    this.validate()
      .then(valid => {
        if (valid) {
          const {onChange} = this.props;
          const {service, ports} = this.state;
          if (onChange) {
            onChange({
              service,
              ports: ports.map(p => ({port: p.port, targetPort: p.targetPort}))
            });
          }
        }
      });
  };

  render () {
    const {
      configuration,
      visible,
      onClose,
      onRemove
    } = this.props;
    const {
      service,
      ports,
      serviceError,
      portsErrors,
      portsGlobalError
    } = this.state;
    return (
      <Modal
        title="Configure internal DNS"
        visible={visible}
        onCancel={onClose}
        footer={(
          <div
            className={styles.modalActions}
          >
            <div>
              {
                configuration
                  ? (
                    <Button
                      type="danger"
                      onClick={onRemove}
                    >
                      Remove configuration
                    </Button>
                  )
                  : '\u00A0'
              }
            </div>
            <div>
              <Button
                className={styles.button}
                onClick={onClose}
              >
                Cancel
              </Button>
              <Button
                disabled={
                  serviceError ||
                  portsErrors.filter(Boolean).length > 0 ||
                  portsGlobalError
                }
                className={styles.button}
                type="primary"
                onClick={this.handleSave}
              >
                Save
              </Button>
            </div>
          </div>
        )}
      >
        <div
          className={
            classNames(
              styles.row,
              {
                [styles.hasError]: !!serviceError
              }
            )
          }
        >
          <span
            className={styles.label}
          >
            Service name:
          </span>
          <Input
            className={styles.input}
            value={service}
            onChange={this.onChangeServiceName}
          />
        </div>
        {
          serviceError && (
            <div
              className={classNames(styles.row, styles.error)}
            >
              {serviceError}
            </div>
          )
        }
        {
          ports.map((port, index) => [(
            <div
              className={styles.row}
              key={`ports-${index}`}
            >
              <span
                className={
                  classNames(
                    styles.label,
                    styles.port
                  )
                }
              >
                Port:
              </span>
              <InputNumber
                className={
                  classNames(
                    styles.input,
                    styles.port,
                    {
                      [styles.error]: portsErrors && portsErrors[index] && portsErrors[index].port
                    }
                  )
                }
                value={port.port}
                onChange={this.onChangePort(index)}
                min={30000}
                max={65535}
              />
              <span
                className={
                  classNames(
                    styles.label,
                    styles.port
                  )
                }
              >
                Target Port:
              </span>
              <InputNumber
                className={
                  classNames(
                    styles.input,
                    styles.port,
                    {
                      [styles.error]: portsErrors &&
                      portsErrors[index] &&
                      portsErrors[index].targetPort
                    }
                  )
                }
                value={port.targetPort}
                onChange={this.onChangeTargetPort(index)}
                min={30000}
                max={65535}
              />
              <Button
                size="small"
                type="danger"
                onClick={this.onRemovePorts(index)}
              >
                <Icon type="delete" />
              </Button>
            </div>
          ), portsErrors && portsErrors[index] && (
            <div
              key={`ports-${index}-error`}
              className={classNames(styles.row, styles.error)}
            >
              {
                portsErrors[index]
                  ? (
                    <span>
                      {
                        [
                          portsErrors[index].general,
                          portsErrors[index].port,
                          portsErrors[index].targetPort
                        ]
                          .filter(Boolean)
                          .join('. ')
                      }
                    </span>
                  )
                  : undefined
              }
            </div>
          )])
            .reduce((r, c) => ([...r, ...c]), [])
        }
        <div
          className={
            classNames(
              styles.row,
              styles.addPort
            )
          }
        >
          <Button
            onClick={this.onAddPorts}
          >
            <Icon type="plus" /> Add ports configuration
          </Button>
        </div>
        {
          portsGlobalError && (
            <div
              className={classNames(styles.row, styles.error)}
            >
              {portsGlobalError}
            </div>
          )
        }
      </Modal>
    );
  }
}

HostedAppConfigurationDialog.propTypes = {
  configuration: PropTypes.object,
  onChange: PropTypes.func,
  onClose: PropTypes.func,
  onRemove: PropTypes.func,
  visible: PropTypes.bool
};

class HostedAppConfiguration extends React.Component {
  state = {
    configurationDialogVisible: false
  };

  openConfigurationDialog = () => {
    this.setState({
      configurationDialogVisible: true
    });
  };

  closeConfigurationDialog = () => {
    this.setState({
      configurationDialogVisible: false
    });
  };

  onChange = (configuration) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(configuration);
    }
    this.closeConfigurationDialog();
  };

  onRemove = () => {
    const {onChange} = this.props;
    if (onChange) {
      onChange();
    }
    this.closeConfigurationDialog();
  };

  renderTitle = () => {
    const {value} = this.props;
    if (!value) {
      return (
        <span className={styles.configure}>
          <Icon type="setting" />Configure
        </span>
      );
    }
    const {service, ports = []} = value;
    return (
      <div
        style={{
          lineHeight: '18px',
          marginTop: 5,
          display: 'flex',
          flexDirection: 'row',
          flexWrap: 'wrap'
        }}
      >
        <span
          style={{marginRight: 5}}
        >
          {service}
        </span>
        <span>(</span>
        {
          ports.map((port, index, array) => (
            <span
              key={`${port.port}-${port.targetPort}-${index}`}
              style={
                index === array.length - 1
                  ? {}
                  : {marginRight: 5}
              }
            >
              {port.port}:{port.targetPort}
              {
                index < array.length - 1 ? ',' : undefined
              }
            </span>
          ))
        }
        <span style={{marginRight: 5}}>)</span>
        <span className={styles.configure}>
          <Icon type="setting" />Configure
        </span>
      </div>
    );
  };

  render () {
    const {value} = this.props;
    const {configurationDialogVisible} = this.state;
    return (
      <div>
        <a
          className={styles.link}
          onClick={this.openConfigurationDialog}
        >
          {this.renderTitle()}
        </a>
        <HostedAppConfigurationDialog
          visible={configurationDialogVisible}
          configuration={value}
          onClose={this.closeConfigurationDialog}
          onChange={this.onChange}
          onRemove={this.onRemove}
        />
      </div>
    );
  }
}

HostedAppConfiguration.propTypes = {
  onChange: PropTypes.func,
  value: PropTypes.object
};

export default HostedAppConfiguration;
