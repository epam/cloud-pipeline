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
import {NODE_UP_RETRY_COUNT_PATTERN} from '../../utilities/launch-cluster';

const FormItem = Form.Item;

function NodeUpRetryCountFormItem (
  {
    className,
    formItemLayout,
    getFieldDecorator,
    disabled,
    initialValue,
    hasFeedback
  }
) {
  return (
    <FormItem
      className={className}
      {...formItemLayout}
      label="Node up retry count"
      required
      hasFeedback={hasFeedback}
    >
      {getFieldDecorator('nodeUpRetryCount',
        {
          rules: [
            {
              pattern: NODE_UP_RETRY_COUNT_PATTERN,
              message: 'Please enter a valid positive integer number'
            },
            {
              required: true,
              message: 'Node up retry count is required'
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
  initialValue: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  hasFeedback: PropTypes.bool
};

NodeUpRetryCountFormItem.defaultProps = {
  hasFeedback: true
};

export default NodeUpRetryCountFormItem;
