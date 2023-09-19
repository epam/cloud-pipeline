/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
  Input
} from 'antd';
import styles from './wdl-parameter.css';
import WdlTypeSelector from '../wdl-type-selector';
import WdlIssues from '../wdl-issues';
import {isCall, isScatterIterator} from '../../../../../../../../utils/pipeline-builder';

class WdlParameter extends React.Component {
  onPropertyChanged = (property, isEvent = true) => (event) => {
    const {
      parameter
    } = this.props;
    if (parameter) {
      parameter[property] = isEvent ? event.target.value : event;
    }
  };

  /**
   * @typedef {Object} PropertyOptions
   * @property {string} property
   * @property {string} [placeholder]
   * @property {string} [title]
   * @property {string} [className]
   * @property {*} [component]
   * @property {boolean} [changeParameterIsEvent=true]
   * @property {Object} [style]
   */
  /**
   * @param {PropertyOptions} options
   * @returns {JSX.Element[]}
   */
  renderProperty = (options) => {
    const {
      parameter,
      disabled
    } = this.props;
    if (!parameter) {
      return null;
    }
    const {
      property,
      component,
      className,
      changeParameterIsEvent = true,
      style,
      title,
      placeholder
    } = options || {};
    if (!property) {
      return null;
    }
    const defaultValue = parameter.executableParameter?.value;
    const Component = component || Input;
    return [
      title ? (
        <span
          key={`${property}-title`}
          className={styles.wdlParameterPartTitle}
        >
          {title}:
        </span>
      ) : null,
      <Component
        key={property}
        value={parameter[property]}
        onChange={this.onPropertyChanged(property, changeParameterIsEvent)}
        className={className}
        style={style}
        disabled={disabled}
        placeholder={defaultValue || placeholder}
      />
    ];
  };

  renderName = () => this.renderProperty({
    property: 'name',
    title: 'Name',
    placeholder: 'Parameter name',
    className: styles.wdlParameterName
  });

  renderType = () => this.renderProperty({
    property: 'type',
    title: 'Type',
    placeholder: 'Parameter type',
    component: WdlTypeSelector,
    changeParameterIsEvent: false,
    className: styles.wdlParameterType
  });

  renderValue = () => this.renderProperty({
    property: 'value',
    title: 'Value',
    placeholder: 'Parameter value',
    className: styles.wdlParameterValue
  });

  renderRemoveButton = () => {
    const {
      disabled,
      removable,
      parameter
    } = this.props;
    if (
      disabled ||
      !removable ||
      !parameter
    ) {
      return null;
    }
    const removeParameter = () => {
      let owner = parameter.parent;
      if (owner && isCall(owner)) {
        owner = owner.executable;
      }
      if (owner && typeof owner.removeParameters === 'function') {
        const parameterToRemove = parameter.executableParameter || parameter;
        owner.removeParameters([
          parameterToRemove
        ]);
      }
    };
    return (
      <div
        className={styles.wdlParameterDeleteButton}
        onClick={removeParameter}
      >
        <Icon type="delete" className="cp-danger" />
      </div>
    );
  };

  renderIssues = () => {
    const {
      parameter
    } = this.props;
    if (!parameter) {
      return null;
    }
    const {
      entityIssues = []
    } = parameter;
    return (
      <WdlIssues
        issues={entityIssues}
      />
    );
  };

  render () {
    const {
      className,
      style,
      parameter
    } = this.props;
    if (!parameter) {
      return null;
    }
    const name = this.renderName();
    const type = this.renderType();
    const removeButton = this.renderRemoveButton();
    const issues = this.renderIssues();
    const value = this.renderValue();
    if (isScatterIterator(parameter)) {
      return (
        <div
          className={className}
          style={style}
        >
          <div className={styles.wdlParameterRow}>
            {name}
            {value}
          </div>
          {issues}
        </div>
      );
    }
    return (
      <div
        className={className}
        style={style}
      >
        <div className={styles.wdlParameterRow}>
          {name}
          {type}
          {removeButton}
        </div>
        <div className={styles.wdlParameterRow}>
          {value}
        </div>
        {issues}
      </div>
    );
  }
}

WdlParameter.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  parameter: PropTypes.object,
  removable: PropTypes.bool
};

export default WdlParameter;
