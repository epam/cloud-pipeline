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
import {AutoComplete, Icon, Input, Row, Button} from 'antd';
import {PortTypes, Primitives, reservedRegExp, quotesFn} from '../utilities';
import styles from '../WDLItemProperties.css';

export function valuesAreEqual (value1, value2, checkExtra = false) {
  if (!!value1 !== !!value2) {
    return false;
  }
  if (!value1 && !value2) {
    return true;
  }
  return value1.name === value2.name &&
    value1.type === value2.type &&
    quotesFn.equals(value1.default, value2.default) &&
    value1.multi === value2.multi &&
    (
      !checkExtra ||
      (value1.previousName === value2.previousName && value1.useQuotes === value2.useQuotes)
    );
}

const primitiveGroup = new RegExp(`^(${Object.values(Primitives).join('|')})[?+]?$`);
const arrayGroup = new RegExp(/^Array\[(.+)\][?+]?$/);
const mapGroup = new RegExp(/^Map\[(.+)[\s]*,[\s]*(.+)\][?+]?$/);
const pairGroup = new RegExp(/^Pair\[(.+)[\s]*,[\s]*(.+)\][?+]?$/);

function validateType (type) {
  const pairResult = pairGroup.exec(type);
  if (pairResult && pairResult.length === 3) {
    return validateType(pairResult[1]) && validateType(pairResult[2]);
  }
  const mapResult = mapGroup.exec(type);
  if (mapResult && mapResult.length === 3) {
    return validateType(mapResult[1]) && validateType(mapResult[2]);
  }
  const arrayResult = arrayGroup.exec(type);
  if (arrayResult && arrayResult.length === 2) {
    return validateType(arrayResult[1]);
  }
  return !!primitiveGroup.exec(type);
}

export function validateSinglePort (port, portType) {
  const requiredFields = ['name', 'type'];
  if (portType === PortTypes.output) {
    requiredFields.push('default');
  }
  const newValidation = {};
  let validationPassed = true;
  requiredFields.forEach(field => {
    if (!port[field]) {
      newValidation[field] = 'Required';
      validationPassed = false;
    }
  });
  const typeAreCorrect = validateType(port.type);
  if (!typeAreCorrect) {
    newValidation.type = newValidation.type || 'Wrong type';
    validationPassed = false;
  }
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(port.name)) {
    newValidation.name = 'Wrong name';
    validationPassed = false;
  }
  if (reservedRegExp.exec(port.name)) {
    newValidation.name = 'Wrong name';
    validationPassed = false;
  }
  return {valid: validationPassed, errors: newValidation};
}

function autoCompleteTypes (type) {
  if (type && type.trim().length) {
    return [
      type,
      ...Object.values(Primitives)
    ].filter((t, i, a) => a.indexOf(t) === i);
  }
  return Object.values(Primitives);
}

export const LockOptions = {
  none: 0,
  type: 1,
  name: 2,
  value: 4
};

@observer
export class WDLItemPortFormItem extends React.Component {

  static propTypes = {
    value: PropTypes.object,
    portType: PropTypes.oneOf([PortTypes.input, PortTypes.output]),
    lock: PropTypes.oneOfType(
      PropTypes.func,
      PropTypes.number
    ),
    onChange: PropTypes.func,
    onRemove: PropTypes.func,
    onInitialize: PropTypes.func,
    onUnMount: PropTypes.func,
    disabled: PropTypes.bool,
    removable: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),

    // if `isRequired = true` user cannot change Name & Type fields as well as remove variable
    isRequired: PropTypes.bool
  };

  static defaultProps = {
    removable: true,
    lock: LockOptions.none
  };

  state = {
    formItemValue: undefined,
    name: undefined,
    type: undefined,
    default: undefined,
    multi: false,
    previousName: undefined,
    useQuotes: false,
    validation: {},
    typesDataSource: []
  };

  componentDidMount () {
    this.updateState(this.props.value);
    this.props.onInitialize && this.props.onInitialize(this);
  }

  componentWillReceiveProps (nextProps) {
    if (!valuesAreEqual(nextProps.value, this.state.formItemValue, true)) {
      this.updateState(nextProps.value);
    }
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount(this);
  }

  updateState = (newValue) => {
    this.setState({
      formItemValue: newValue,
      name: newValue.name,
      type: newValue.type,
      default: newValue.default,
      multi: newValue.multi,
      previousName: newValue.previousName,
      useQuotes: newValue.useQuotes,
      validation: {},
      typesDataSource: autoCompleteTypes(newValue.type)
    }, this.validate);
  };

  validate = (reportNewValue = true) => {
    const {valid, errors} = validateSinglePort(this.state, this.props.portType);
    this.setState(
      {validation: errors},
      reportNewValue && valid ? this.reportOnChange : undefined);
    return valid;
  };

  reportOnChange = () => {
    this.props.onChange && this.props.onChange({
      name: this.state.name,
      type: this.state.type,
      default: this.state.default,
      multi: this.state.multi,
      previousName: this.state.previousName,
      useQuotes: this.state.useQuotes
    });
  };

  onChangeName = (e) => {
    this.setState({
      name: e.target.value
    }, this.validate);
  };

  onChangeType = (newType) => {
    this.setState({
      type: newType
    }, this.validate);
  };

  handleTypesSearch = (type) => {
    this.setState({
      type: type,
      typesDataSource: autoCompleteTypes(type)
    }, this.validate);
  };

  onChangeValue = (e) => {
    this.setState({
      default: e.target.value
    }, this.validate);
  };

  onRemoveClicked = () => {
    this.props.onRemove && this.props.onRemove();
  };

  getClasses = (field, defaultClass) => {
    const classes = [];
    if (defaultClass && typeof defaultClass === 'string') {
      classes.push(defaultClass);
    }
    if (defaultClass && typeof defaultClass === 'object' && Array.isArray(defaultClass)) {
      classes.push(...defaultClass);
    }
    if (this.state.validation[field]) {
      classes.push(styles.notValid);
    } else {
      classes.push(styles.valid);
    }
    return classes.join(' ');
  };

  isRemovable = () => {
    if (typeof this.props.removable === 'boolean') {
      return this.props.removable;
    }
    if (typeof this.props.removable === 'function') {
      return this.props.removable({name: this.state.name, type: this.state.type});
    }
    return true;
  };

  getLockOptions = () => {
    if (typeof this.props.lock === 'function') {
      return this.props.lock({name: this.state.name, type: this.state.type});
    }
    return this.props.lock;
  };

  isLocked = (mask) => {
    const lockOptions = this.getLockOptions();
    return (lockOptions & mask) === mask;
  };

  render () {
    const isRemovable = this.isRemovable();
    return (
      <Row type="flex" align="middle">
        <Input
          className={this.getClasses('name', [styles.portsColumn, 'variable-name'])}
          disabled={this.props.disabled || this.props.isRequired || this.isLocked(LockOptions.name)}
          value={this.state.name}
          onChange={this.onChangeName} />
        <AutoComplete
          dataSource={this.state.typesDataSource}
          className={this.getClasses('type', [styles.portsColumn, 'variable-type'])}
          disabled={this.props.disabled || this.props.isRequired || this.isLocked(LockOptions.type)}
          value={this.state.type}
          onSearch={this.handleTypesSearch}
          onSelect={this.onChangeType} />
        <Input
          className={this.getClasses('default', [styles.portsColumn, 'variable-value'])}
          disabled={this.props.disabled || this.isLocked(LockOptions.value)}
          value={this.state.default}
          onChange={this.onChangeValue} />
        <div className={styles.portsActionColumn}>
          {
            isRemovable && !this.props.isRequired &&
            <Button
              id="remove-variable-button"
              size="small"
              disabled={this.props.disabled}
              style={{lineHeight: 'initial'}}
              onClick={this.onRemoveClicked}>
              <Icon type="close" />
            </Button>
          }
          {
            (!isRemovable || this.props.isRequired) && '\u00A0'
          }
        </div>
      </Row>
    );
  }
}
