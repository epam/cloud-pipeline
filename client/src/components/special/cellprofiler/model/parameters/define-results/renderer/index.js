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
/* eslint-disable max-len */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed, isObservableArray} from 'mobx';
import {Button, Icon} from 'antd';
import classNames from 'classnames';
import generateId from '../../../common/generate-id';
import OutputProperty from './output-property';
import OutputFormula from './output-formula';
import {ObjectProperty} from '../object-properties';
import styles from './define-results.css';

const generateConfigurationId = () => `${generateId()}_configuration`;

class DefineResultsRenderer extends React.Component {
  /**
   * @returns {ModuleParameter}
   */
  @computed
  get parameter () {
    if (this.props.parameterValue) {
      return this.props.parameterValue.parameter;
    }
    return undefined;
  }
  /**
   * @returns {AnalysisModule}
   */
  @computed
  get cpModule () {
    if (this.parameter) {
      return this.parameter.cpModule;
    }
    return undefined;
  }
  /**
   * @returns {AnalysisPipeline}
   */
  @computed
  get pipeline () {
    if (this.cpModule) {
      return this.cpModule.pipeline;
    }
    return undefined;
  }
  @computed
  get configurations () {
    if (this.props.parameterValue) {
      const value = this.props.parameterValue.value;
      if (!value || !(isObservableArray(value) || Array.isArray(value))) {
        return [];
      }
      return value;
    }
    return [];
  }
  @computed
  get objects () {
    if (!this.pipeline) {
      return [];
    }
    return this.pipeline.objects;
  }

  setValue = (newValue) => {
    if (this.props.parameterValue) {
      this.props.parameterValue.value = newValue;
      this.props.parameterValue.reportChanged();
    }
  };

  addOutputProperty = (e) => {
    if (e && e.target && typeof e.target.blur === 'function') {
      e.target.blur();
    }
    this.setValue([
      ...this.configurations,
      {id: generateConfigurationId(), properties: [{name: ObjectProperty.all}]}
    ]);
  };

  addOutputFormula = (e) => {
    if (e && e.target && typeof e.target.blur === 'function') {
      e.target.blur();
    }
    this.setValue([...this.configurations, {id: generateConfigurationId(), isFormula: true}]);
  };

  changePropertyOrFormula = (propertyOrFormula) => {
    const newValue = this.configurations.slice();
    const anIndex = newValue.findIndex(o => o.id === propertyOrFormula.id);
    newValue.splice(anIndex, 1, propertyOrFormula);
    this.setValue(newValue);
  }

  removePropertyOrFormula = (propertyOrFormula) => {
    const newValue = this.configurations.slice();
    const anIndex = newValue.findIndex(o => o.id === propertyOrFormula.id);
    newValue.splice(anIndex, 1);
    this.setValue(newValue);
  }

  render () {
    const {
      className,
      style
    } = this.props;
    if (!this.pipeline || !this.parameter) {
      return null;
    }
    return (
      <div
        className={className}
        style={style}
      >
        {
          this.configurations.map((aConfiguration) => (
            <div
              key={aConfiguration.id}
              className={
                classNames(
                  styles.outputRow,
                  'cp-even-odd-element'
                )
              }
            >
              {
                !aConfiguration.isFormula && (
                  <OutputProperty
                    pipeline={this.pipeline}
                    outputProperty={aConfiguration}
                    objects={this.objects.slice()}
                    onChange={this.changePropertyOrFormula}
                    onRemove={this.removePropertyOrFormula}
                  />
                )
              }
              {
                aConfiguration.isFormula && (
                  <OutputFormula
                    pipeline={this.pipeline}
                    formula={aConfiguration}
                    onChange={this.changePropertyOrFormula}
                    onRemove={this.removePropertyOrFormula}
                  />
                )
              }
            </div>
          ))
        }
        <div
          className={styles.addConfigurationActionsRow}
        >
          <Button
            size="small"
            className={styles.action}
            onClick={this.addOutputProperty}
          >
            <Icon type="plus" />
            <span>Output property</span>
          </Button>
          <Button
            size="small"
            className={styles.action}
            onClick={this.addOutputFormula}
          >
            <Icon type="plus" />
            <span>Output formula</span>
          </Button>
        </div>
      </div>
    );
  }
}

DefineResultsRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameterValue: PropTypes.object
};

export default observer(DefineResultsRenderer);
