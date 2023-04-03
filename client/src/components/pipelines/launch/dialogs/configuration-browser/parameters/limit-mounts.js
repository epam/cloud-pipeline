/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {Checkbox} from 'antd';
import {injectParametersStore} from './store';
import {LimitMountsInput} from '../../../form/LimitMountsInput';

function ParametersLimitMounts (props) {
  const {
    className,
    style,
    parametersStore,
    disabled,
    tool
  } = props;
  if (!parametersStore) {
    return null;
  }
  const onChangeDoNotMount = e => {
    if (e.target.checked) {
      parametersStore.onChangeLimitMounts('none');
    } else {
      parametersStore.onChangeLimitMounts(undefined);
    }
  };
  const doNotMount = /^none$/i.test(parametersStore.limitMounts);
  return (
    <div
      className={className}
      style={style}
    >
      <div>
        <Checkbox
          disabled={disabled}
          checked={doNotMount}
          onChange={onChangeDoNotMount}
        >
          Do not mount storages
        </Checkbox>
      </div>
      {
        !doNotMount && (
          <div>
            <LimitMountsInput
              className={className}
              disabled={disabled}
              style={style}
              onChange={parametersStore.onChangeLimitMounts}
              value={parametersStore.limitMounts}
              allowSensitive={tool ? tool.allowSensitive : false}
            />
          </div>
        )
      }
    </div>
  );
}

ParametersLimitMounts.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  style: PropTypes.object,
  tool: PropTypes.object
};

export default injectParametersStore(observer(ParametersLimitMounts));
