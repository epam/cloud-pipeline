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
import styles from './input-field.css';

export default class InputDocker extends React.Component {
  render () {
    const {value, onChange, label, disabled} = this.props;
    return (
      <div className={styles.inputField}>
        <label className={styles.label}>{label}</label>
        <Input
          style={{width: '100%'}}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          placeholder="Enter input path"
        />
      </div>
    );
  }
}

InputDocker.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string,
  disabled: PropTypes.bool
};

InputDocker.defaultProps = {
  value: '',
  label: '',
  disabled: false
};
