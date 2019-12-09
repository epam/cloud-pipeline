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
import * as billing from '../../../models/billing';
import styles from './quotas.css';

function getRuleName (rule) {
  switch (rule) {
    case billing.quotas.rules.none: return 'Do nothing';
    case billing.quotas.rules.email: return 'Notify user';
    case billing.quotas.rules.stop: return 'Stop all runs';
    case billing.quotas.rules.readOnly: return 'Set read-only access';
    case billing.quotas.rules.block: return 'Block user';
  }
  return null;
}

class Threshold extends React.Component {
  static propTypes = {
    action: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    error: PropTypes.bool,
    disabled: PropTypes.bool,
    onChange: PropTypes.func,
    onRemove: PropTypes.func,
    value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    quota: PropTypes.number
  };

  onChangeValue = (value) => {
    const {action, onChange} = this.props;
    onChange && onChange({
      action,
      threshold: value
    });
  };

  onChangeAction = (action) => {
    const {value, onChange} = this.props;
    onChange && onChange({
      action,
      threshold: value
    });
  };

  render () {
    const {action, disabled, value, error, onRemove, quota} = this.props;
    return (
      <div
        className={`${styles.thresholdContainer} ${error ? styles.error : ''}`}
      >
        <InputNumber
          disabled={disabled}
          key="threshold input"
          placeholder="Threshold"
          className={styles.input}
          value={value}
          onChange={this.onChangeValue}
          min={0}
          max={100}
        />
        <div className={styles.spans}>
          <span>%</span>
          {
            !isNaN(quota) && !isNaN(value) &&
            (
              <span>
                ({Math.round(+quota * (+value)) / 100.0} $)
              </span>
            )
          }
          <span>:</span>
        </div>
        <div className={styles.selectContainer}>
          <Select
            disabled={disabled}
            key="rule"
            placeholder="Select action"
            value={action}
            onChange={this.onChangeAction}
            style={{width: '100%'}}
          >
            {Object.keys(billing.quotas.rules).map(key => (
              <Select.Option key={key} value={billing.quotas.rules[key]}>
                {getRuleName(billing.quotas.rules[key])}
              </Select.Option>
            ))}
          </Select>
        </div>
        {
          onRemove &&
          (
            <Button
              size="small"
              disabled={disabled}
              onClick={onRemove}
              type="danger"
              style={{marginLeft: 5}}
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
