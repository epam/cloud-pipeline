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
import checkLayers from './check';
import RunOperationWarning from '../common/warning';

const WarningMessage = (opts) => {
  const {
    allowed,
    current
  } = opts;
  if (allowed && current) {
    return (
      <span>
        {/* eslint-disable-next-line max-len */}
        <b>Maximum number of layers exceeded</b>: {current} layer{current === 1 ? '' : 's'}, {allowed} maximum.
        Operation disabled.
      </span>
    );
  }
  return 'Maximum number of layers exceeded. Operation disabled.';
};

function LayersCheckWarning (props) {
  return (
    <RunOperationWarning
      {...props}
      objectId={props.runId}
      check={checkLayers}
      warning={WarningMessage}
    />
  );
}

LayersCheckWarning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  type: PropTypes.string,
  showIcon: PropTypes.bool
};

export {WarningMessage};
export default LayersCheckWarning;
