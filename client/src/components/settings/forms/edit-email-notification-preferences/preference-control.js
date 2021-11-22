/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
  Icon,
  Input,
  InputNumber,
  Select,
  Popover
} from 'antd';
import PreferenceLoad from '../../../../models/preferences/PreferenceLoad';
import {Preferences} from './configuration';
import ExcludeParametersControl from './exclude-parameters-control';
import compareArrays from '../../../../utils/compareArrays';
import styles from './preference-control.css';

function wrapValue (value) {
  if (value === undefined || value === null) {
    return null;
  }
  return `${value}`;
}

function compareExcludedParameters (parameters, initialParameters) {
  const compareObjects = (objA, objB) => {
    const arrA = Object.values(objA);
    const arrB = Object.values(objB);
    return !arrA
      .filter(x => !arrB.includes(x))
      .concat(arrB.filter(x => !arrA.includes(x)))
      .length;
  };
  return !compareArrays(parameters, initialParameters, compareObjects);
}

function getExcludedParametersPayload (parameters) {
  const payload = (parameters || [])
    .filter(parameter => parameter.value !== undefined && parameter.name !== undefined)
    .reduce((acc, cur) => {
      acc[cur.name] = {
        value: cur.value,
        operator: cur.operator
      };
      return acc;
    }, {});
  return JSON.stringify(payload);
}

function processExcludedParameters (excludedParametersObj) {
  let obj = {};
  try {
    obj = JSON.parse(excludedParametersObj);
  } catch (_) {
  }
  return Object.entries((obj || {}))
    .map(([parameterName, parameterObj]) => ({
      name: parameterName,
      value: parameterObj.value,
      operator: parameterObj.operator
    }));
}

class PreferenceControl extends React.Component {
  state = {
    value: undefined,
    initialValue: undefined,
    error: undefined,
    pending: true,
    meta: {}
  };

  componentDidMount () {
    this.updateValues();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.session !== this.props.session ||
      prevProps.preference !== this.props.preference
    ) {
      this.updateValues();
    }
  }

  get preference () {
    const {preference} = this.props;
    return Preferences.find(p => p.preference === preference);
  }

  updateValues = () => {
    const {preference: preferenceName} = this.props;
    const request = new PreferenceLoad(preferenceName);
    this.setState({
      pending: true
    }, () => {
      request
        .fetch()
        .then(() => {
          if (request.loaded) {
            const preference = request.value;
            const isExcludedParameters = preferenceName &&
              preferenceName === 'system.notifications.exclude.params';
            if (preference) {
              this.setState({
                error: undefined,
                value: isExcludedParameters
                  ? processExcludedParameters(preference.value)
                  : wrapValue(preference.value),
                initialValue: isExcludedParameters
                  ? processExcludedParameters(preference.value)
                  : wrapValue(preference.value),
                pending: false,
                meta: {...preference}
              }, this.onChange);
            } else {
              this.setState({
                error: `Preference ${preferenceName} not found`,
                pending: false,
                meta: {}
              }, this.onChange);
            }
          } else if (request.error) {
            this.setState({
              error: request.error,
              pending: false,
              meta: {}
            }, this.onChange);
          }
        })
        .catch((e) => {
          this.setState({
            error: e.toString(),
            pending: false,
            meta: {}
          }, this.onChange);
        });
    });
  };

  onChange = () => {
    const {onChange} = this.props;
    if (onChange) {
      const {value, initialValue, meta} = this.state;
      let modified = initialValue !== value;
      let payload = value;
      if (this.preference.type === 'excludeParamsControl') {
        modified = compareExcludedParameters(value, initialValue);
        payload = getExcludedParametersPayload(value);
      }
      onChange(payload, modified, meta);
    }
  };

  renderHint = (preference) => {
    if (!preference || !preference.hint) {
      return null;
    }
    return (
      <Popover
        content={preference.hint}
        placement="left"
      >
        <Icon
          style={{
            marginLeft: 5,
            marginRight: 10,
            fontSize: 'larger',
            cursor: 'pointer'
          }}
          type="question-circle"
        />
      </Popover>
    );
  };

  renderStringControl = (preference) => {
    if (!preference) {
      return null;
    }
    const {value, pending} = this.state;
    const onValueChange = (e) => {
      this.setState({
        value: wrapValue(e.target.value)
      }, this.onChange);
    };
    return (
      <div className={styles.controlRow}>
        <span className={styles.label}>
          {preference.name}
        </span>
        <Input
          disabled={pending}
          className={styles.control}
          value={value}
          onChange={onValueChange}
        />
        {this.renderHint(preference)}
      </div>
    );
  };

  renderEnumControl = (preference) => {
    if (!preference) {
      return null;
    }
    const {value, pending} = this.state;
    const onValueChange = (e) => {
      this.setState({
        value: e
      }, this.onChange);
    };
    return (
      <div className={styles.controlRow}>
        <span className={styles.label}>
          {preference.name}
        </span>
        <Select
          disabled={pending}
          className={styles.control}
          value={value}
          onChange={onValueChange}
        >
          {
            (preference.enum || []).map(o => (
              <Select.Option key={o} value={o}>
                {o}
              </Select.Option>
            ))
          }
        </Select>
        {this.renderHint(preference)}
      </div>
    );
  };

  renderNumberControl = (preference) => {
    if (!preference) {
      return null;
    }
    const {value, pending} = this.state;
    const onValueChange = (e) => {
      this.setState({
        value: wrapValue(e)
      }, this.onChange);
    };
    return (
      <div className={styles.controlRow}>
        <span className={styles.label}>
          {preference.name}
        </span>
        <InputNumber
          disabled={pending}
          className={styles.control}
          value={Number.isNaN(Number(value)) ? 0 : Number(value)}
          min={preference.min}
          max={preference.max}
          onChange={onValueChange}
        />
        {this.renderHint(preference)}
      </div>
    );
  };

  onExcludedParametersChange = (parameter, index) => {
    const {value} = this.state;
    const newState = [...(value || [])];
    let currentParameter = newState[index];
    if (currentParameter) {
      currentParameter = parameter;
      this.setState({
        value: newState
      }, this.onChange);
    }
  };

  onAddExcludedParameter = (parameter) => {
    const {value} = this.state;
    this.setState({
      value: [...(value || []), parameter]
    }, this.onChange);
  };

  onRemoveExcludedParameter = index => {
    const {value} = this.state;
    this.setState({
      value: value.filter((parameter, idx) => idx !== index)
    }, this.onChange);
  };

  renderExcludeParamsControl = (preference) => {
    if (!preference) {
      return null;
    }
    const {value, pending} = this.state;
    return (
      <div
        className={styles.controlRow}
        style={{alignItems: 'baseline'}}
      >
        <span className={styles.label}>
          {preference.name}
        </span>
        <ExcludeParametersControl
          parameters={value}
          pending={pending}
          onChange={this.onExcludedParametersChange}
          onAdd={this.onAddExcludedParameter}
          onRemove={this.onRemoveExcludedParameter}
        />
        {this.renderHint(preference)}
      </div>
    );
  };

  render () {
    const {error} = this.state;
    const preference = this.preference;
    let control;
    if (preference) {
      switch (preference.type) {
        case 'number': control = this.renderNumberControl(preference); break;
        case 'enum': control = this.renderEnumControl(preference); break;
        case 'string': control = this.renderStringControl(preference); break;
        case 'excludeParamsControl':
          control = this.renderExcludeParamsControl(preference);
          break;
        default:
          control = this.renderStringControl(preference); break;
      }
    }
    return (
      <div>
        <div>
          {control}
        </div>
        {
          error && (
            <div className={styles.error}>
              {error}
            </div>
          )
        }
      </div>
    );
  }
}

PreferenceControl.propTypes = {
  preference: PropTypes.string,
  onChange: PropTypes.func,
  session: PropTypes.number
};

export default PreferenceControl;
