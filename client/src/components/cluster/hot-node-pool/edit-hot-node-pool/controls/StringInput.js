/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Input, InputNumber} from 'antd';
import classNames from 'classnames';
import HintControl from './HintControl';
import styles from '../../edit-hot-node-pool.css';

// `disabled` is the caller's already-combined expression (e.g. `disabled || readOnly`):
// this component never OR's a `readOnly` prop in on top of it, so every field keeps
// its own disabled/readOnly behaviour explicit at the call site.
function StringInput ({
  type,
  label,
  value,
  onChange,
  disabled,
  min,
  max,
  hint,
  error,
  touched,
  style
}) {
  return (
    <div style={style}>
      <div className={styles.formRow}>
        <span className={styles.label}>
          {label}
        </span>
        {
          type === 'number' ? (
            <InputNumber
              className={classNames(styles.formItem, styles.small)}
              disabled={disabled}
              value={value}
              min={min}
              max={max}
              onChange={onChange}
            />
          ) : (
            <div className={classNames(styles.formItem, styles.small)}>
              <Input
                disabled={disabled}
                value={value}
                onChange={onChange}
              />
            </div>
          )
        }
        <HintControl hint={hint} />
      </div>
      {
        error && touched && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {error}
          </div>
        )
      }
    </div>
  );
}

StringInput.propTypes = {
  type: PropTypes.oneOf(['string', 'number']),
  label: PropTypes.node,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  min: PropTypes.number,
  max: PropTypes.number,
  hint: PropTypes.node,
  error: PropTypes.string,
  touched: PropTypes.bool,
  style: PropTypes.object
};

StringInput.defaultProps = {
  type: 'string'
};

export default StringInput;
