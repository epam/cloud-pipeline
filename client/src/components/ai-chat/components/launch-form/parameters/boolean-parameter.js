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
import {Checkbox} from 'antd';
import styles from './styles-components.css';

export default class BooleanParameter extends React.Component {
  render () {
    const {value, onChange, label, disabled} = this.props;
    return (
      <Checkbox
        className={styles.indent}
        checked={value === 'true'}
        onChange={(e) => onChange(e.target.checked ? 'true' : 'false')}
        disabled={disabled}
      >
        {label}
      </Checkbox>
    );
  }
}

BooleanParameter.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string,
  disabled: PropTypes.bool
};

BooleanParameter.defaultProps = {
  value: 'false',
  label: '',
  disabled: false
};
