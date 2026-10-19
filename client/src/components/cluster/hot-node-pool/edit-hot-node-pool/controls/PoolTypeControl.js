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
import {POOL_TYPE_STANDARD, POOL_TYPE_SHAREABLE} from '../constants';

const POOL_TYPE_NAMES = {
  [POOL_TYPE_STANDARD]: 'Standard',
  [POOL_TYPE_SHAREABLE]: 'Shareable node'
};

// `poolType` is a real backend field (`NodePoolVO.poolType`, enum `STANDARD`/
// `SHAREABLE_NODE`) that applies to a pool in either mode, not just a future-dated one:
// a `SHAREABLE_NODE` pool's node is shared between several runs, and the backend derives
// its launch config from that. The backend's UPDATE statement does not carry
// `pool_type`, so the value can only be chosen at create time - which is why the form
// disables this control when editing.
function PoolTypeControl ({value, disabled, hint, onChange}) {
  return (
    <SelectInput
      label="Pool type:"
      value={value}
      disabled={disabled}
      hint={hint}
      onChange={onChange}
    >
      {
        [POOL_TYPE_STANDARD, POOL_TYPE_SHAREABLE].map(poolType => (
          <Select.Option key={poolType} value={poolType}>
            {POOL_TYPE_NAMES[poolType]}
          </Select.Option>
        ))
      }
    </SelectInput>
  );
}

PoolTypeControl.propTypes = {
  value: PropTypes.string,
  disabled: PropTypes.bool,
  hint: PropTypes.node,
  onChange: PropTypes.func
};

export {POOL_TYPE_NAMES};
export default PoolTypeControl;
