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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Icon,
  Select
} from 'antd';
import {getObjectsForModule} from './object-parameter';
import styles from './outline-object-configuration.css';
import ColorPicker from '../../../color-picker';
import colorList from '../common/color-list';

const displayModes = {
  color: 'Color',
  grayscale: 'Grayscale'
};

class OutlineConfigRenderer extends React.Component {
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
  get displayMode () {
    if (!this.cpModule) {
      return displayModes.color;
    }
    return this.cpModule.getParameterValue('displayMode') || displayModes.color;
  }
  @computed
  get objectsToOutline () {
    if (!this.value) {
      return [];
    }
    return this.value;
  }
  @computed
  get objects () {
    return getObjectsForModule(this.cpModule);
  }
  setValue = (newValue) => {
    const {parameterValue} = this.props;
    if (parameterValue) {
      parameterValue.value = newValue;
      parameterValue.reportChanged();
    }
  };
  addConfiguration = () => {
    const current = this.objectsToOutline;
    this.setValue([...current, {
      name: undefined,
      color: colorList[current.length % colorList.length]
    }]);
  };
  changeConfigurationObject = (index) => (object) => {
    const objectToOutline = this.objectsToOutline[index] || {};
    objectToOutline.name = object;
    const newValue = this.objectsToOutline.slice();
    newValue.splice(index, 1, objectToOutline);
    this.setValue(newValue);
  }
  changeConfigurationColor = (index) => (color) => {
    const objectToOutline = this.objectsToOutline[index] || {};
    objectToOutline.color = color;
    const newValue = this.objectsToOutline.slice();
    newValue.splice(index, 1, objectToOutline);
    this.setValue(newValue);
  }
  removeConfiguration = (index) => (event) => {
    if (event) {
      event.stopPropagation();
    }
    const newValue = this.objectsToOutline.slice();
    newValue.splice(index, 1);
    this.setValue(newValue);
  };
  render () {
    const {
      className,
      style
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
          this.objectsToOutline.map((objectToOutline, index) => (
            <div
              className={styles.objectToOutlineRow}
              key={`${objectToOutline.name || ''}_${index}`}
            >
              <Select
                value={objectToOutline.name}
                style={{flex: 1}}
                onChange={this.changeConfigurationObject(index)}
              >
                {
                  this.objects.map((object) => (
                    <Select.Option key={object} value={object}>
                      {object}
                    </Select.Option>
                  ))
                }
              </Select>
              {
                this.displayMode === displayModes.color && (
                  <ColorPicker
                    color={objectToOutline.color}
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
            Add object
          </Button>
        </div>
      </div>
    );
  }
}

OutlineConfigRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameterValue: PropTypes.object
};

const OutlineConfig = observer(OutlineConfigRenderer);

export default OutlineConfig;
