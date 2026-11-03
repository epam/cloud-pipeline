/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Form, Input} from 'antd';

const FormItem = Form.Item;
const ID = 'node-up-retry-count';

function NodeUpRetryCountFormItem (
  {
    className,
    style,
    formItemLayout,
    value,
    error,
    placeholder,
    disabled,
    onChange
  }
) {
  const onInputChange = (event) => {
    if (typeof onChange === 'function') {
      onChange(event.target.value);
    }
  };
  return (
    <FormItem
      className={className}
      style={style}
      {...formItemLayout}
      id={ID}
      label="Capacity retries"
      validateStatus={error ? 'error' : undefined}
      help={error}
    >
      <Input
        id={ID}
        value={value === undefined || value === null ? '' : `${value}`}
        placeholder={placeholder === undefined ? undefined : `${placeholder}`}
        disabled={disabled}
        onChange={onInputChange}
      />
    </FormItem>
  );
}

NodeUpRetryCountFormItem.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  formItemLayout: PropTypes.object,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  error: PropTypes.string,
  placeholder: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  disabled: PropTypes.bool,
  onChange: PropTypes.func
};

export default NodeUpRetryCountFormItem;
