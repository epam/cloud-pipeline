/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Icon,
  InputNumber,
  Select
} from 'antd';
import classNames from 'classnames';
import {actionNames, getActionsByTypeAndGroup} from './utilities/quota-actions';
import styles from './quotas.css';

class Threshold extends React.Component {
  static propTypes = {
    action: PropTypes.object,
    error: PropTypes.bool,
    quotaGroup: PropTypes.string,
    quotaType: PropTypes.string,
    disabled: PropTypes.bool,
    onChange: PropTypes.func,
    onRemove: PropTypes.func
  };

  onChangeValue = (value) => {
    const {action, onChange} = this.props;
    onChange && onChange({
      ...action,
      threshold: value
    });
  };

  onChangeAction = (actions = []) => {
    const {action, onChange} = this.props;
    onChange && onChange({
      ...action,
      actions
    });
  };

  render () {
    const {
      action,
      disabled,
      error,
      onRemove,
      quotaGroup,
      quotaType
    } = this.props;
    if (!action) {
      return null;
    }
    const {
      actions = [],
      threshold = 0
    } = action;
    return (
      <div
        className={styles.thresholdContainer}
      >
        <InputNumber
          disabled={disabled}
          key="threshold input"
          placeholder="Threshold"
          className={classNames({'cp-error': !!error})}
          value={threshold}
          onChange={this.onChangeValue}
          min={0}
          style={{width: 100}}
        />
        <span style={{marginLeft: 5}}>%:</span>
        <Select
          className={classNames({'cp-error': !!error})}
          disabled={disabled}
          mode="multiple"
          key="rule"
          placeholder="Select action"
          value={actions.slice()}
          onChange={this.onChangeAction}
          style={{flex: 1, margin: '0px 5px'}}
        >
          {
            getActionsByTypeAndGroup(quotaType, quotaGroup).map(action => (
              <Select.Option
                key={action}
                value={action}
              >
                {actionNames[action] || action}
              </Select.Option>
            ))
          }
        </Select>
        {
          onRemove &&
          (
            <Button
              size="small"
              disabled={disabled}
              onClick={onRemove}
              type="danger"
              style={{marginLeft: 5, lineHeight: 1}}
            >
              <Icon type="close" />
            </Button>
          )
        }
      </div>
    );
  }
}

export default Threshold;
