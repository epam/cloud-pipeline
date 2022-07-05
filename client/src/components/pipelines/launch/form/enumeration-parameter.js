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

class EnumerationParameter extends React.PureComponent {
  render () {
    const {
      value,
      onChange,
      className,
      style,
      rawEdit,
      disabled,
      enumeration = [],
      parameters,
      placeholder = 'Value',
      size = 'large'
    } = this.props;
    if (rawEdit) {
      return (
        <Input
          className={className}
          style={style}
          onChange={e => onChange ? onChange(e.target.value) : undefined}
          placeholder={placeholder}
          size={size}
          value={value}
        />
      );
    }
    return (
      <Select
        disabled={disabled}
        placeholder={placeholder}
        className={className}
        onChange={onChange}
        style={style}
        value={value}
        size={size}
      >
        {
          (enumeration || [])
            .filter(e => e.isVisible(parameters))
            .map(e => (
              <Select.Option
                key={e.name}
                value={e.name}
              >
                {e.name}
              </Select.Option>
            ))
        }
      </Select>
    );
  }
}

EnumerationParameter.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  className: PropTypes.string,
  style: PropTypes.object,
  rawEdit: PropTypes.bool,
  disabled: PropTypes.bool,
  enumeration: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  parameters: PropTypes.object,
  placeholder: PropTypes.string,
  size: PropTypes.string
};

export default EnumerationParameter;
