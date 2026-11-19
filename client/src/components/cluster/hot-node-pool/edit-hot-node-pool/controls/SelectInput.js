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
import {Select} from 'antd';
import classNames from 'classnames';
import HintControl from './HintControl';
import styles from '../../edit-hot-node-pool.css';

// Option/OptGroup trees differ too much between callers (region grouping with
// AWSRegionTag, instance type grouping with InstanceDetails, flat price types)
// to generalize here, so they are passed in as `children`; this component only
// owns the row/label/error/Select boilerplate shared by all three.
function SelectInput ({
  label,
  value,
  onChange,
  disabled,
  placeholder,
  showSearch,
  filterOption,
  error,
  touched,
  hint,
  style,
  children
}) {
  return (
    <div style={style}>
      <div className={styles.formRow}>
        <span className={styles.label}>
          {label}
        </span>
        <Select
          disabled={disabled}
          showSearch={showSearch}
          className={classNames(styles.formItem, styles.small)}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          filterOption={filterOption}
        >
          {children}
        </Select>
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

SelectInput.propTypes = {
  label: PropTypes.node,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  placeholder: PropTypes.string,
  showSearch: PropTypes.bool,
  filterOption: PropTypes.oneOfType([PropTypes.func, PropTypes.bool]),
  error: PropTypes.string,
  touched: PropTypes.bool,
  hint: PropTypes.node,
  style: PropTypes.object,
  children: PropTypes.node
};

export default SelectInput;
