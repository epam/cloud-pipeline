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
import {injectParametersStore} from './store';
import RunCapabilities, {RUN_CAPABILITIES_MODE} from '../../../form/utilities/run-capabilities';

function ParametersRunCapabilities (props) {
  const {
    className,
    style,
    dockerImage,
    parametersStore,
    disabled,
    tool,
    provider,
    region
  } = props;
  if (!parametersStore) {
    return null;
  }
  return (
    <RunCapabilities
      className={className}
      disabled={disabled}
      onChange={parametersStore.onChangeCapabilities}
      style={style}
      dockerImage={dockerImage}
      platform={tool ? tool.platform : undefined}
      provider={provider}
      region={region}
      values={parametersStore.capabilities}
      mode={RUN_CAPABILITIES_MODE.launch}
    />
  );
}

ParametersRunCapabilities.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  style: PropTypes.object,
  dockerImage: PropTypes.string,
  tool: PropTypes.object,
  provider: PropTypes.string,
  region: PropTypes.object
};

export default injectParametersStore(observer(ParametersRunCapabilities));
