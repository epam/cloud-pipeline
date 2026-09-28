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
import {Form, Icon, Input} from 'antd';
import {NODE_UP_RETRY_COUNT_PATTERN} from '../../utilities/launch-cluster';
import styles from './node-up-retry-count.css';

const FormItem = Form.Item;

// A class, not a function: `getFieldDecorator` passes a ref to its child
export class NodeUpRetryCountInput extends React.Component {
  onClear = (event) => {
    if (event) {
      event.stopPropagation();
    }
    const {onChange, disabled} = this.props;
    if (disabled || !onChange) {
      return;
    }
    onChange(undefined);
  };

  onClearKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onClear(event);
    }
  };

  render () {
    const {
      id,
      value,
      onChange,
      disabled,
      placeholder
    } = this.props;
    const clearIcon = value && !disabled
      ? (
        <Icon
          type="close-circle"
          className={styles.clear}
          role="button"
          aria-label="Clear"
          tabIndex={0}
          onMouseDown={(event) => event.preventDefault()}
          onClick={this.onClear}
          onKeyDown={this.onClearKeyDown}
        />
      )
      : null;
    // `suffix` is always passed, even as null: antd re-mounts the input,
    // and drops its focus, when the prop appears or disappears
    return (
      <Input
        id={id}
        value={value}
        onChange={onChange}
        disabled={disabled}
        placeholder={placeholder}
        suffix={clearIcon}
      />
    );
  }
}

NodeUpRetryCountInput.propTypes = {
  id: PropTypes.string,
  value: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  placeholder: PropTypes.string
};

function NodeUpRetryCountFormItem (
  {
    className,
    formItemLayout,
    getFieldDecorator,
    disabled,
    initialValue,
    placeholder
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
              pattern: NODE_UP_RETRY_COUNT_PATTERN,
              message: 'Please enter a valid positive integer number'
            }
          ],
          initialValue
        }
      )(
        <NodeUpRetryCountInput
          disabled={disabled}
          placeholder={placeholder}
        />
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
  placeholder: PropTypes.string
};

export default NodeUpRetryCountFormItem;
