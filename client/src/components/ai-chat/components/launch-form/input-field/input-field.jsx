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
import {Input} from 'antd';
import styles from './input-field.module.css';
import {observer} from 'mobx-react';

@observer
export default class InputField extends React.Component {
  state = {
    error: null,
  };

  handleChange = (value) => {
    const {onChange, validator} = this.props;
    let error = null;

    if (validator) {
      error = validator(value);
    }

    this.setState({error});
    onChange(value);
  };

  validate = () => {
    const {value, validator} = this.props;

    if (validator) {
      const error = validator(value);
      this.setState({error});
      return !error;
    }

    return true;
  };

  render() {
    const {value, label, disabled, placeholder} = this.props;
    const {error} = this.state;

    return (
      <div className={styles.inputField}>
        <label className={styles.label}>{label}</label>
        <div className={styles.inputContainer}>
          <Input
            className={`${error ? styles.inputError : ''}`}
            style={{width: '100%'}}
            value={value}
            onChange={(e) => this.handleChange(e.target.value)}
            disabled={disabled}
            placeholder={placeholder || 'Enter input'}
          />
          {error && <div className={styles.error}>{error}</div>}
        </div>
      </div>
    );
  }
}

InputField.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string,
  disabled: PropTypes.bool,
  placeholder: PropTypes.string,
  validator: PropTypes.func,
};

InputField.defaultProps = {
  value: '',
  label: '',
  disabled: false,
  placeholder: '',
  validator: null,
};
