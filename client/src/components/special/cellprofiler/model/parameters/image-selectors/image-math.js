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
import styles from './image-selectors.css';

const VALUE_SUPPORTED_OPERATIONS = [
  'Add',
  'Subtract',
  'Absolute Difference',
  'Multiply',
  'Divide',
  'Average',
  'Minimum',
  'Maximum',
  'Standard Deviation',
  'Invert',
  'Log transform (base 2)',
  'Log transform (legacy)'
];

const SINGLE_FILE_OPERATIONS = [
  'Invert',
  'Log transform (base 2)',
  'Log transform (legacy)',
  'Not'
];

class ImageMathImagesRenderer extends React.Component {
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
  get operation () {
    if (!this.cpModule) {
      return 'Add';
    }
    return this.cpModule.getParameterValue('operation') || 'Add';
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
      value: 1.0
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
      valueLabel = 'multiply by'
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
                VALUE_SUPPORTED_OPERATIONS.includes(this.operation) && (
                  <span style={{whiteSpace: 'pre'}}>
                    {` ${valueLabel} `}
                  </span>
                )
              }
              {
                VALUE_SUPPORTED_OPERATIONS.includes(this.operation) && (
                  <Input
                    value={objectToOutline.value}
                    onChange={this.changeConfigurationValue(index)}
                    style={{width: 75}}
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
          {
            (
              !SINGLE_FILE_OPERATIONS.includes(this.operation) ||
              this.filesToProcess.length === 0
            ) && (
              <Button
                size="small"
                onClick={this.addConfiguration}
              >
                Add image
              </Button>
            )
          }
        </div>
      </div>
    );
  }
}

ImageMathImagesRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameterValue: PropTypes.object,
  valueLabel: PropTypes.string
};

const ImageMathImages = observer(ImageMathImagesRenderer);

export {VALUE_SUPPORTED_OPERATIONS, SINGLE_FILE_OPERATIONS};
export default ImageMathImages;
