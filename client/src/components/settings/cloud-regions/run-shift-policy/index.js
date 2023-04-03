/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

function runShiftPoliciesEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const tA = typeof a;
  const tB = typeof b;
  if (tA !== tB) {
    return false;
  }
  if (tA === 'object') {
    const {
      shiftEnabled: aShiftEnabled = false
    } = a;
    const {
      shiftEnabled: bShiftEnabled = false
    } = b;
    return aShiftEnabled === bShiftEnabled;
  }
  return a === b;
}

class RunShiftPolicy extends React.PureComponent {
  get shiftEnabled () {
    const {
      value
    } = this.props;
    if (typeof value === 'object') {
      const {
        shiftEnabled = false
      } = value;
      return shiftEnabled;
    }
    return false;
  }

  onShiftEnabledChanged = (event) => {
    const {
      value,
      onChange
    } = this.props;
    if (typeof onChange === 'function') {
      const newValue = typeof value === 'object' ? {...value} : {};
      newValue.shiftEnabled = event.target.checked;
      onChange(newValue);
    }
  };

  render () {
    const {
      className,
      style,
      disabled
    } = this.props;
    return (
      <div
        className={className}
        style={style}
      >
        <Checkbox
          disabled={disabled}
          checked={this.shiftEnabled}
          onChange={this.onShiftEnabledChanged}
        >
          Shift enabled
        </Checkbox>
      </div>
    );
  }
}

RunShiftPolicy.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.object,
  onChange: PropTypes.func,
  disabled: PropTypes.bool
};

export {runShiftPoliciesEqual};
export default RunShiftPolicy;
