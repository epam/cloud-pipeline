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
import {Button, Icon, Row} from 'antd';
import {
  LockOptions,
  WDLItemPortFormItem,
  validateSinglePort,
  valuesAreEqual as portsAreEqual
} from './WDLItemPortFormItem';
import {PortTypes, Primitives, testPrimitiveTypeFn, quotesFn} from '../utilities';
import styles from '../WDLItemProperties.css';

export function validatePorts (ports, portsType) {
  const count = (ports || []).length;
  const validCount = (ports || []).filter(p => validateSinglePort(p, portsType).valid).length;
  if (count !== validCount) {
    return `Not all ${portsType.toLowerCase()}s are valid`;
  }
  const filterUnique = (item, index, array) => array.indexOf(item) === index;
  const uniqueNamesCount = (ports || []).map(p => p.name).filter(filterUnique).length;
  if (count !== uniqueNamesCount) {
    return `${portsType}s names should be unique`;
  }
  return undefined;
}

export function valuesAreEqual (value1, value2) {
  if (!!value1 !== !!value2) {
    return false;
  }
  if (!value1 && !value2) {
    return true;
  }
  const keys1 = Object.keys(value1);
  const keys2 = Object.keys(value2);
  if (keys1.length !== keys2.length) {
    return false;
  }
  for (let i = 0; i < keys1.length; i++) {
    const key1 = keys1[i];
    const [key2] = keys2.filter(key => key === key1);
    if (!key2) {
      return false;
    }
    const port1 = value1[key1];
    const port2 = value2[key2];
    if (!portsAreEqual(port1, port2, false)) {
      return false;
    }
  }
  return true;
}

@observer
export class WDLItemPortsFormItem extends React.Component {

  static propTypes = {
    value: PropTypes.object,
    portType: PropTypes.oneOf([PortTypes.input, PortTypes.output]),
    onChange: PropTypes.func,
    onInitialize: PropTypes.func,
    onUnMount: PropTypes.func,
    disabled: PropTypes.bool,

    // if `isRequired = true` user cannot change names and types as well as remove or add variables
    isRequired: PropTypes.bool,
    addVariableSupported: PropTypes.bool,
    removeVariableSupported: PropTypes.oneOfType([PropTypes.func, PropTypes.bool]),
    lockVariables: PropTypes.oneOfType(
      PropTypes.func,
      PropTypes.number
    )
  };

  static defaultProps = {
    addVariableSupported: true,
    removeVariableSupported: true,
    lockVariables: LockOptions.none
  };

  state = {
    formItemValue: undefined,
    ports: [],
    error: null,
    collapsed: true
  };

  portFormItems = {};

  componentDidMount () {
    this.updateState(this.props.value);
    this.props.onInitialize && this.props.onInitialize(this);
  }

  componentWillReceiveProps (nextProps) {
    if (!valuesAreEqual(nextProps.value, this.state.formItemValue)) {
      this.updateState(nextProps.value);
    }
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount();
  }

  reset = () => {
    this.setState({
      formItemValue: undefined,
      ports: [],
      error: null,
      collapsed: true
    });
  };

  portFormItemInitialized = (key) => (component) => {
    this.portFormItems[`component_${key}`] = component;
  };

  portFormItemUnMounted = (key) => () => {
    delete this.portFormItems[`component_${key}`];
  };

  updateState = (newValue) => {
    const prevPortsState = (this.state.ports || []).map(p => p.name);
    const newPorts = Object.keys(newValue || {}).map(key => {
      const testResult = quotesFn.test(newValue[key].default);
      return {
        name: key,
        previousName: key,
        type: newValue[key].type,
        multi: newValue[key].multi,
        default: testResult.string,
        useQuotes: testResult.useQuotes
      };
    });
    const getPortIndex = name => prevPortsState.indexOf(name);
    newPorts.sort((pA, pB) => {
      const iA = getPortIndex(pA.previousName);
      const iB = getPortIndex(pB.previousName);
      if (iA > iB) {
        return 1;
      } else if (iA < iB) {
        return -1;
      }
      return 0;
    });
    this.setState({
      formItemValue: newValue,
      ports: newPorts
    }, this.validate);
  };

  validate = (reportNewValue = true) => {
    const componentKeys = Object.keys(this.portFormItems);
    let error;
    for (let i = 0; i < componentKeys.length; i++) {
      const key = componentKeys[i];
      const component = this.portFormItems[key];
      if (component && !component.validate(false)) {
        error = `Some ${this.props.portType.toLowerCase()}s contains errors`;
      }
    }
    error = validatePorts(this.state.ports, this.props.portType) || error;
    this.setState(
      {error},
      reportNewValue && !error ? this.reportOnChange : undefined);
  };

  reportOnChange = () => {
    const value = this.state.ports.reduce((result, current) => {
      result[current.name] = {
        type: current.type,
        default: current.useQuotes || testPrimitiveTypeFn(Primitives.string, current.type)
          ? quotesFn.wrap(current.default)
          : current.default,
        multi: current.multi,
        previousName: current.previousName
      };
      return result;
    }, {});
    this.props.onChange && this.props.onChange(value);
  };

  onPortChanged = (index) => (port) => {
    const ports = this.state.ports;
    if (index >= 0 && ports.length > index) {
      ports.splice(index, 1, port);
    }
    this.setState({
      ports
    }, this.validate);
  };

  onAddClicked = () => {
    const ports = this.state.ports;
    ports.push({
      multi: false
    });
    this.setState({
      collapsed: false,
      ports
    }, this.validate);
  };

  onRemoveClicked = (index) => () => {
    const ports = this.state.ports;
    if (index >= 0 && ports.length > index) {
      ports.splice(index, 1);
    }
    this.setState({
      ports
    }, this.validate);
  };

  toggleCollapse = () => {
    this.setState({
      collapsed: !this.state.collapsed
    });
  };

  render () {
    return (
      <div className={styles.portsContainer} style={{width: '100%'}}>
        <Row
          className={styles.header}
          type="flex"
          align="middle">
          <a
            id={this.state.collapsed ? 'expand-panel-button' : 'collapse-panel-button'}
            className={styles.portCollapsibleHeader}
            style={{flex: 1}}
            onClick={this.toggleCollapse}>
            <Row type="flex" align="middle">
              {
                this.state.collapsed
                  ? <Icon type="down-circle-o" />
                  : <Icon type="up-circle-o" />
              }
              <span>
                {this.props.portType === PortTypes.input ? 'Inputs' : 'Outputs'}
                {
                  this.state.collapsed && (this.state.ports || []).length
                    ? ` (${(this.state.ports || []).length}):`
                    : ':'
                }
              </span>
            </Row>
          </a>
          {
            this.props.addVariableSupported &&
            <Button
              id="add-variable-button"
              disabled={this.props.disabled || this.props.isRequired}
              size="small"
              style={{lineHeight: 'initial'}}
              onClick={this.onAddClicked}>
              ADD
            </Button>
          }
        </Row>
        {
          !this.state.collapsed &&
          <Row className={styles.header} type="flex">
            <span className={styles.portsColumn}>
              Name
            </span>
            <span className={styles.portsColumn}>
              Type
            </span>
            <span className={styles.portsColumn}>
              Value{this.props.portType === PortTypes.input ? ' (optional)' : ''}
            </span>
            <span className={styles.portsActionColumn}>
              {'\u00A0'}
            </span>
          </Row>
        }
        {
          !this.state.collapsed && this.state.ports.map((port, index) => (
            <WDLItemPortFormItem
              key={index}
              value={port}
              removable={this.props.removeVariableSupported}
              lock={this.props.lockVariables}
              disabled={this.props.disabled}
              isRequired={this.props.isRequired}
              onChange={this.onPortChanged(index)}
              onInitialize={this.portFormItemInitialized(index)}
              onUnMount={this.portFormItemUnMounted(index)}
              onRemove={this.onRemoveClicked(index)}
              portType={this.props.portType} />
          ))
        }
        {
          this.state.error &&
          <Row type="flex" justify="space-around" style={{color: 'red'}}>
            {this.state.error}
          </Row>
        }
        {
          !this.state.collapsed && this.state.ports.length === 0 &&
          <Row type="flex" justify="space-around" style={{color: '#aaa'}}>
            No data
          </Row>
        }
      </div>
    );
  }
}
