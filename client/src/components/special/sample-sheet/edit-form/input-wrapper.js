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
import {Input} from 'antd';
import classNames from 'classnames';
import styles from './input-wrapper.css';

function InputWrapper (
  {
    className,
    disabled,
    editable,
    isKey,
    onChange,
    style,
    value
  }
) {
  if (editable) {
    return (
      <Input
        disabled={disabled}
        className={className}
        style={style}
        value={value}
        onChange={onChange}
      />
    );
  }
  return (
    <span
      className={
        classNames(
          className,
          styles.span,
          {
            [styles.key]: isKey
          }
        )
      }
      style={style}
    >
      {value}
    </span>
  );
}

InputWrapper.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  editable: PropTypes.bool,
  onChange: PropTypes.func,
  style: PropTypes.object,
  value: PropTypes.string,
  isKey: PropTypes.bool
};

export default InputWrapper;
