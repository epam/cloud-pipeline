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
import {Select} from 'antd';
import {RepositoryTypeNames, RepositoryTypes} from '../../../special/git-repository-control';

class RepositoryTypeSelector extends React.Component {
  componentDidMount () {
    this.reportFromPropsChanged();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value) {
      this.reportFromPropsChanged();
    }
  }

  reportFromPropsChanged = () => {
    const {value} = this.props;
    this.reportOnChange(value);
  };

  onChange = (newValue) => {
    const {
      onChange
    } = this.props;
    if (typeof onChange === 'function') {
      onChange(newValue);
    }
  };

  reportOnChange = (newValue) => {
    const {
      onRepositoryTypeChanged
    } = this.props;
    if (typeof onRepositoryTypeChanged === 'function') {
      onRepositoryTypeChanged(newValue);
    }
  };

  render () {
    const {
      className,
      disabled,
      style,
      value
    } = this.props;
    return (
      <div
        className={className}
        style={{
          width: '100%',
          ...(style || {})
        }}
      >
        <Select
          style={{width: '100%'}}
          disabled={disabled}
          onChange={this.onChange}
          value={value}
        >
          {
            Object.values(RepositoryTypes || {})
              .map((type) => (
                <Select.Option key={type} value={type}>
                  {RepositoryTypeNames[type] || type}
                </Select.Option>
              ))
          }
        </Select>
      </div>
    );
  }
}

RepositoryTypeSelector.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  style: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  onRepositoryTypeChanged: PropTypes.func
};

export default RepositoryTypeSelector;
