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
import {Checkbox} from 'antd';
import classNames from 'classnames';
import HintControl from './HintControl';
import styles from '../../edit-hot-node-pool.css';

// `marginLeft: 11` on check mirrors the Autoscaled row, hand-tuned to line up
// with the indented rows that appear below it once checked.
function CheckboxControl ({
  label,
  checked,
  onChange,
  disabled,
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
        <Checkbox
          style={checked ? {marginLeft: 11} : {}}
          className={classNames(styles.formItem, styles.small)}
          disabled={disabled}
          checked={checked}
          onChange={onChange}
        />
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

CheckboxControl.propTypes = {
  label: PropTypes.node,
  checked: PropTypes.bool,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  hint: PropTypes.node,
  error: PropTypes.string,
  touched: PropTypes.bool,
  style: PropTypes.object
};

export default CheckboxControl;
