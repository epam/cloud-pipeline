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
import {getParameterAllowedStructs} from '../../utilities/workflow-utilities';

function parameterValueEditable (parameter, wdlDocument) {
  return !parameter ||
    !parameter.isOutput ||
    parameter.document === wdlDocument;
}

class WdlParameter extends React.Component {
  get structs () {
    const {parameter} = this.props;
    return getParameterAllowedStructs(parameter);
  }
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
   * @property {boolean} [editable]
   * @property {*} [component]
   * @property {*} [componentOptions]
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
      componentOptions = {},
      className,
      changeParameterIsEvent = true,
      style,
      title,
      placeholder,
      editable = true
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
        {...componentOptions}
        key={property}
        value={parameter[property]}
        onChange={this.onPropertyChanged(property, changeParameterIsEvent)}
        className={className}
        style={style}
        disabled={disabled || !editable}
        placeholder={defaultValue || placeholder}
      />
    ];
  };

  renderName = () => this.renderProperty({
    property: 'name',
    title: 'Name',
    placeholder: 'Parameter name',
    editable: this.props.editable,
    className: styles.wdlParameterName
  });

  renderType = () => this.renderProperty({
    property: 'type',
    title: 'Type',
    placeholder: 'Parameter type',
    component: WdlTypeSelector,
    componentOptions: {
      structs: this.structs
    },
    changeParameterIsEvent: false,
    className: styles.wdlParameterType,
    editable: this.props.editable
  });

  renderValue = () => this.renderProperty({
    property: 'value',
    title: 'Value',
    placeholder: 'Parameter value',
    className: styles.wdlParameterValue,
    editable: parameterValueEditable(this.props.parameter, this.props.wdlDocument)
  });

  renderRemoveButton = () => {
    const {
      disabled,
      editable,
      parameter
    } = this.props;
    if (
      disabled ||
      !editable ||
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
      issues = []
    } = parameter;
    return (
      <WdlIssues
        issues={issues}
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
  editable: PropTypes.bool,
  wdlDocument: PropTypes.object
};

export default WdlParameter;
