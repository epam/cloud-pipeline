/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Icon, Input, Select} from 'antd';
import {getFilesForModule} from '../file-parameter';
import {observer} from 'mobx-react';
import ColorPicker from '../../../../color-picker';
import colorList from '../../common/color-list';
import styles from './image-selectors.css';

const VALUE_SUPPORTED_SCHEMES = [
  'Composite'
];

class GrayToColorRenderer extends React.Component {
  get parameter () {
    const {parameterValue} = this.props;
    if (!parameterValue) {
      return undefined;
    }
    return parameterValue.parameter;
  }
  @computed
  get value () {
    const {parameterValue} = this.props;
    if (!parameterValue) {
      return undefined;
    }
    return parameterValue.value;
  }
  get cpModule () {
    if (!this.parameter) {
      return undefined;
    }
    return this.parameter.cpModule;
  }
  @computed
  get scheme () {
    if (!this.cpModule) {
      return 'Composite';
    }
    return this.cpModule.getParameterValue('scheme') || 'Composite';
  }
  @computed
  get filesToProcess () {
    if (!this.value) {
      return [];
    }
    return this.value;
  }
  @computed
  get files () {
    return getFilesForModule(this.cpModule);
  }
  setValue = (newValue) => {
    const {parameterValue} = this.props;
    if (parameterValue) {
      parameterValue.value = newValue;
      parameterValue.reportChanged();
    }
  };
  addConfiguration = () => {
    const current = this.filesToProcess;
    this.setValue([...current, {
      name: undefined,
      value: 1.0,
      color: colorList[current.length % colorList.length]
    }]);
  };
  changeConfigurationFile = (index) => (object) => {
    const objectToOutline = this.filesToProcess[index] || {};
    objectToOutline.name = object;
    const newValue = this.filesToProcess.slice();
    newValue.splice(index, 1, objectToOutline);
    this.setValue(newValue);
  }
  changeConfigurationValue = (index) => (e) => {
    const objectToOutline = this.filesToProcess[index] || {};
    objectToOutline.value = e.target.value;
    const newValue = this.filesToProcess.slice();
    newValue.splice(index, 1, objectToOutline);
    this.setValue(newValue);
  }
  changeConfigurationColor = (index) => (color) => {
    const objectToOutline = this.filesToProcess[index] || {};
    objectToOutline.color = color;
    const newValue = this.filesToProcess.slice();
    newValue.splice(index, 1, objectToOutline);
    this.setValue(newValue);
  }
  removeConfiguration = (index) => (event) => {
    if (event) {
      event.stopPropagation();
    }
    const newValue = this.filesToProcess.slice();
    newValue.splice(index, 1);
    this.setValue(newValue);
  };
  render () {
    const {
      className,
      style,
      valueLabel = 'weight'
    } = this.props;
    if (!this.parameter) {
      return null;
    }
    return (
      <div
        className={className}
        style={style}
      >
        {
          this.filesToProcess.map((objectToOutline, index) => (
            <div
              className={styles.imageRow}
              key={`${objectToOutline.name || ''}_${index}`}
            >
              <Select
                value={objectToOutline.name}
                style={{flex: 1}}
                onChange={this.changeConfigurationFile(index)}
              >
                {
                  this.files.map((object) => (
                    <Select.Option key={object} value={object}>
                      {object}
                    </Select.Option>
                  ))
                }
              </Select>
              {
                ['Composite'].includes(this.scheme) && (
                  <span style={{whiteSpace: 'pre'}}>
                    {` ${valueLabel} `}
                  </span>
                )
              }
              {
                ['Composite'].includes(this.scheme) && (
                  <Input
                    value={objectToOutline.value}
                    onChange={this.changeConfigurationValue(index)}
                    style={{width: 75}}
                  />
                )
              }
              {
                ['Composite'].includes(this.scheme) && (
                  <ColorPicker
                    color={objectToOutline.color || '#FFFFFF'}
                    onChange={this.changeConfigurationColor(index)}
                    hex
                    ignoreAlpha
                  />
                )
              }
              <Button
                style={{marginLeft: 5}}
                type="danger"
                size="small"
                onClick={this.removeConfiguration(index)}
              >
                <Icon type="delete" />
              </Button>
            </div>
          ))
        }
        <div>
          <Button
            size="small"
            onClick={this.addConfiguration}
          >
            Add image
          </Button>
        </div>
      </div>
    );
  }
}

GrayToColorRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameterValue: PropTypes.object,
  valueLabel: PropTypes.string
};

const GrayToColor = observer(GrayToColorRenderer);

export {VALUE_SUPPORTED_SCHEMES};
export default GrayToColor;
