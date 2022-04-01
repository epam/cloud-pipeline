/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

const FRIENDLY_NAME_MAX_LENGTH = 25;

function RunFriendlyNameInput ({
  onChange,
  value = '',
  label = '',
  containerClassName,
  containerStyle,
  inputStyle
}) {
  const onInputChange = (event = {}) => {
    const {value = ''} = event.target || {};
    const validityPattern = /^$|^[\da-zA-Z_-]+$/;
    if (
      value.length <= FRIENDLY_NAME_MAX_LENGTH &&
      validityPattern.test(value)
    ) {
      onChange && onChange(value);
    }
  };
  return (
    <div
      style={Object.assign({
        display: 'flex',
        flexWrap: 'nowrap',
        alignItems: 'center',
        borderRadius: '4px'
      }, containerStyle)}
      className={containerClassName}
    >
      {label && (
        <span
          style={{
            verticalAlign: 'middle',
            whiteSpace: 'nowrap'
          }}
        >
          {label}
        </span>
      )}
      <Input
        onChange={onInputChange}
        value={value}
        style={Object.assign({
          width: '200px',
          marginLeft: '5px'
        }, inputStyle)}
      />
    </div>
  );
}

RunFriendlyNameInput.propTypes = {
  onChange: PropTypes.func,
  value: PropTypes.string,
  label: PropTypes.string,
  containerClassName: PropTypes.string,
  containerStyle: PropTypes.object,
  inputStyle: PropTypes.object
};

export default RunFriendlyNameInput;
