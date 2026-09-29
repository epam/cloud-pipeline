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
import {
  MAX_NODE_UP_RETRY_COUNT,
  NODE_UP_RETRY_COUNT_PATTERN,
  nodeUpRetryCountExceedsMax
} from '../../utilities/launch-cluster';

const FormItem = Form.Item;

function NodeUpRetryCountFormItem (
  {
    className,
    formItemLayout,
    getFieldDecorator,
    disabled,
    initialValue,
    // falls back to the constant rather than to no bound at all, so a caller
    // that forgets the prop still gets a bounded field
    max = MAX_NODE_UP_RETRY_COUNT
  }
) {
  return (
    <FormItem
      className={className}
      {...formItemLayout}
      label="Capacity retries"
    >
      {getFieldDecorator('nodeUpRetryCount',
        {
          rules: [
            {
              required: true,
              message: 'Capacity retries is required'
            },
            {
              pattern: NODE_UP_RETRY_COUNT_PATTERN,
              message: 'Please enter a valid positive integer number'
            },
            {
              validator: (rule, value, callback) => callback(
                nodeUpRetryCountExceedsMax(value, max)
                  ? `Maximum value is ${max}`
                  : undefined
              )
            }
          ],
          initialValue
        }
      )(
        <Input disabled={disabled} />
      )}
    </FormItem>
  );
}

NodeUpRetryCountFormItem.propTypes = {
  className: PropTypes.string,
  formItemLayout: PropTypes.object,
  getFieldDecorator: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
  initialValue: PropTypes.string,
  max: PropTypes.number
};

export default NodeUpRetryCountFormItem;
