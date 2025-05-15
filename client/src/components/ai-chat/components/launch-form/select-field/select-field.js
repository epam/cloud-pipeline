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

import React from 'react';
import PropTypes from 'prop-types';
import {Select} from 'antd';
import styles from './select-field.css';

const {Option} = Select;

export default class SelectField extends React.Component {
  render () {
    const {value, options, onChange, placeholder, disabled, label} = this.props;

    return (
      <div className={styles.selectField}>
        {label && <label className={styles.label}>{label}</label>}
        <Select
          style={{width: '100%'}}
          value={value}
          onChange={onChange}
          disabled={disabled}
          placeholder={placeholder}
        >
          {options.map((option) => (
            <Option key={option.value} value={option.value}>
              {option.label}
            </Option>
          ))}
        </Select>
      </div>
    );
  }
}

SelectField.propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(
    PropTypes.shape({
      value: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired
    })
  ).isRequired,
  onChange: PropTypes.func.isRequired,
  placeholder: PropTypes.string,
  disabled: PropTypes.bool,
  label: PropTypes.string
};

SelectField.defaultProps = {
  value: undefined,
  placeholder: 'Select an option',
  disabled: false,
  label: ''
};
