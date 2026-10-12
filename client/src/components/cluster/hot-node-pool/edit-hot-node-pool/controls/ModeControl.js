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
import {Select} from 'antd';
import SelectInput from './SelectInput';
import {MODE_DEFAULT, MODE_GPU} from '../constants';

const MODE_NAMES = {
  [MODE_DEFAULT]: 'Default',
  [MODE_GPU]: 'GPU'
};

// `mode` is client-only: there is no backend field for it, so an existing pool
// always loads as DEFAULT and this control is locked (via `disabled`) while editing.
function ModeControl ({value, disabled, hint, onChange}) {
  return (
    <SelectInput
      label="Mode:"
      value={value}
      disabled={disabled}
      hint={hint}
      onChange={onChange}
    >
      {
        [MODE_DEFAULT, MODE_GPU].map(mode => (
          <Select.Option key={mode} value={mode}>
            {MODE_NAMES[mode]}
          </Select.Option>
        ))
      }
    </SelectInput>
  );
}

ModeControl.propTypes = {
  value: PropTypes.string,
  disabled: PropTypes.bool,
  hint: PropTypes.node,
  onChange: PropTypes.func
};

export {MODE_NAMES};
export default ModeControl;
