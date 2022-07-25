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
import {Input, Select} from 'antd';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import {injectParametersStore} from './store';
import styles from './parameters.css';

function Addon ({children}) {
  if (!children) {
    return null;
  }
  const {
    props = {}
  } = children || {};
  const child = React.cloneElement(
    children,
    {
      className: classNames(
        props.className,
        styles.addon
      )
    }
  );
  return (
    <span
      className={
        classNames(
          styles.parameterAddon,
          'cp-input-group-addon'
        )
      }
      style={{
        display: 'inline-flex'
      }}
    >
      {child}
    </span>
  );
}

class AutoCompleteInput extends React.Component {
  get options () {
    const {
      value = '',
      autoCompleteOptions = {}
    } = this.props;
    const sets = Object
      .entries(autoCompleteOptions)
      .map(([group, options]) => ({
        group,
        options
      }));
    const parts = value.split('.');
    if (parts.length <= 1) {
      return [];
    }
    const [groupName, ...propertyParts] = parts;
    const property = propertyParts.join('.').toLowerCase();
    return sets
      .filter(set => set.group.toLowerCase() === groupName.toLowerCase())
      .map(set => set.options
        .filter(option => option.toLowerCase().startsWith(property))
        .map(option => ({group: set.group, option}))
      )
      .reduce((r, c) => ([...r, ...c]), [])
      .filter(rule => rule.option.toLowerCase().startsWith(property))
      .map(rule => ({name: rule.option, value: `${rule.group}.${rule.option}`}));
  }
  onChange = (newValue) => {
    const {
      onChange
    } = this.props;
    if (typeof onChange === 'function') {
      onChange(newValue);
    }
  };
  render () {
    const {
      className,
      style,
      value,
      disabled,
      size,
      error,
      addonBefore,
      addonAfter
    } = this.props;
    return (
      <Input.Group
        compact
        size={size}
        className={className}
        style={Object.assign({display: 'flex'}, style)}
      >
        <Addon>
          {addonBefore}
        </Addon>
        <Select
          className={
            classNames(
              {
                'cp-error': !!error
              }
            )
          }
          disabled={disabled}
          value={value}
          onChange={this.onChange}
          mode="combobox"
          filterOption={false}
          style={{flex: 1}}
        >
          {
            this.options.map(option => (
              <Select.Option
                key={option.value}
                value={option.value}
              >
                {option.name}
              </Select.Option>
            ))
          }
        </Select>
        <Addon>
          {addonAfter}
        </Addon>
      </Input.Group>
    );
  }
}

AutoCompleteInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  addonBefore: PropTypes.node,
  addonAfter: PropTypes.node,
  size: PropTypes.oneOf(['large', 'small', 'default']),
  error: PropTypes.any,
  autoCompleteOptions: PropTypes.object
};

AutoCompleteInput.defaultProps = {
  size: 'default'
};

function ParametersAutoCompleteInputComponent (props) {
  const {
    className,
    style,
    value,
    disabled,
    size,
    error,
    addonBefore,
    addonAfter,
    parametersStore,
    autoCompleteOptions = {},
    useEntityFields = true,
    useProjectFields = true,
    onChange
  } = props;
  const options = {
    ...autoCompleteOptions,
    this: useEntityFields ? parametersStore.entityTypeFields : [],
    project: useProjectFields ? parametersStore.projectFields : []
  };
  return (
    <AutoCompleteInput
      className={className}
      style={style}
      value={value}
      disabled={disabled}
      addonAfter={addonAfter}
      addonBefore={addonBefore}
      size={size}
      error={error}
      autoCompleteOptions={options}
      onChange={onChange}
    />
  );
}

ParametersAutoCompleteInputComponent.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  addonBefore: PropTypes.node,
  addonAfter: PropTypes.node,
  size: PropTypes.oneOf(['large', 'small', 'default']),
  error: PropTypes.any,
  autoCompleteOptions: PropTypes.object,
  useEntityFields: PropTypes.bool,
  useProjectFields: PropTypes.bool
};
ParametersAutoCompleteInputComponent.defaultProps = {
  useEntityFields: true,
  useProjectFields: true
};

const ParametersAutoCompleteInput = injectParametersStore(
  observer(ParametersAutoCompleteInputComponent)
);

export {AutoCompleteInput, ParametersAutoCompleteInput};
