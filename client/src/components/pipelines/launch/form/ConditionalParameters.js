/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import {Icon, Input} from 'antd';
import React from 'react';

export default class ConditionalParameters extends React.Component {
  get visibleParameters () {
    const {conditionalParameters, dependentName} = this.props;
    const linkedConditionalParameters = (conditionalParameters || [])
      .filter(param => {
        const [parameterName] = param.visibilityCondition.split(/===|==/);
        return dependentName && parameterName
          ? parameterName.trim() === dependentName
          : false;
      });
    return linkedConditionalParameters.filter(p => !p.markAsDeleted);
  }

  onChange = parameter => (event) => {
    const {onChange, conditionalParameters, readOnly} = this.props;
    if (readOnly) {
      return;
    }
    const {value} = event.target;
    const idx = conditionalParameters.findIndex((p) => p.name === parameter.name);
    if (idx >= 0) {
      const newState = [...conditionalParameters];
      newState.splice(idx, 1, {
        ...conditionalParameters[idx],
        value
      });
      onChange(newState);
    }
  };

  removeParameter = parameter => () => {
    const {onChange, conditionalParameters, readOnly} = this.props;
    if (readOnly) {
      return;
    }
    const idx = conditionalParameters.findIndex((p) => p.name === parameter.name);
    if (idx >= 0) {
      const newState = [...conditionalParameters];
      newState.splice(idx, 1, {
        ...conditionalParameters[idx],
        markAsDeleted: true
      });
      onChange(newState);
    }
  };

  render () {
    const {conditionalParameters, readOnly} = this.props;
    if (!conditionalParameters?.length) {
      return null;
    }
    return (
      <div style={{
        gap: '10px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
      }}>
        {this.visibleParameters.map((parameter, index) => (
          <div
            key={index}
            className="launch-form-pipeline-name-form-item"
            style={{
              display: 'flex',
              flexDirection: 'column',
              width: '50%',
              gap: '1px'
            }}
          >
            <span className="ant-form-item-title" style={{textWrap: 'nowrap'}}>
              {parameter.name}
            </span>
            <div style={{display: 'flex', flexWrap: 'nowrap', fontSize: 'larger'}}>
              <Input
                style={{flex: 1, minHeight: 32}}
                value={parameter.value}
                onChange={this.onChange(parameter)}
                disabled={readOnly}
              />
              {!readOnly ? (
                <Icon
                  id="remove-conditional-parameter-button"
                  className="dynamic-delete-button"
                  type="minus-circle-o"
                  onClick={this.removeParameter(parameter)}
                  style={{
                    verticalAlign: 'middle',
                    marginTop: '2px',
                    fontSize: 'larger',
                    cursor: 'pointer',
                    alignSelf: 'flex-start',
                    margin: 'auto -2px auto 15px',
                    height: '100%'
                  }}
                />
              ) : (
                <div
                  style={{
                    marginLeft: 15,
                    width: 15,
                    display: 'inline-block'
                  }}>{'\u00A0'}</div>
              )}
            </div>
          </div>
        ))}
      </div>
    );
  }
}
