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
import {Checkbox, Button} from 'antd';
import {booleanParameterValue} from './parameter-utilities';
import {CP_CAP_RESCHEDULE_RUN} from './parameters';

function rescheduleRunParameterValue (parameters) {
  return booleanParameterValue(parameters, CP_CAP_RESCHEDULE_RUN);
}

class RescheduleRunControl extends React.Component {
  render () {
    const {
      value,
      className,
      style,
      checkbox,
      disabled,
      onChange,
      children
    } = this.props;
    if (checkbox) {
      const onChangeCallback = (event) => {
        if (typeof onChange === 'function') {
          onChange(event.target.checked);
        }
      };
      return (
        <div
          className={className}
          style={style}
        >
          <Checkbox
            disabled={disabled}
            checked={!!value}
            onChange={onChangeCallback}
          >
            {
              children || 'Reschedule run'
            }
          </Checkbox>
        </div>
      );
    }
    const valueFormatted = (() => {
      switch (value) {
        case true: return 'enabled';
        case false: return 'disabled';
        default:
          return 'not configured';
      }
    })();
    const onChangeFormatted = (newValue, event) => {
      if (typeof onChange === 'function') {
        if (
          event &&
          event.target &&
          typeof event.target.blur === 'function'
        ) {
          event.target.blur();
        }
        switch (newValue) {
          case 'enabled': onChange(true); break;
          case 'disabled': onChange(false); break;
          default:
            onChange(undefined);
            break;
        }
      }
    };
    const getButtonStyle = (value) => value === valueFormatted ? 'primary' : 'default';
    const values = [
      {
        key: 'not configured',
        name: 'Not Configured'
      },
      {
        key: 'enabled',
        name: 'Enabled'
      },
      {
        key: 'disabled',
        name: 'Disabled'
      }
    ];
    return (
      <div
        className={className}
        style={style}
      >
        <Button.Group>
          {
            values.map((aValue) => (
              <Button
                key={aValue.key}
                type={getButtonStyle(aValue.key)}
                onClick={(event) => onChangeFormatted(aValue.key, event)}
                disabled={disabled}
              >
                {aValue.name}
              </Button>
            ))
          }
        </Button.Group>
      </div>
    );
  }
}

RescheduleRunControl.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.bool
  ]),
  onChange: PropTypes.func,
  checkbox: PropTypes.bool,
  children: PropTypes.node
};

export {rescheduleRunParameterValue};
export default RescheduleRunControl;
