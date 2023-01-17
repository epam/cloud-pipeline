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
import checkToolLayers from './check';
import RunOperationWarning from '../common/warning';

const WarningMessage = (opts) => {
  const {
    allowed,
    current
  } = opts;
  let extra;
  if (allowed !== undefined && current !== undefined) {
    extra = `: ${current} layer${current === 1 ? '' : 's'}, ${allowed} maximum.`;
  }
  return (
    <span>
      {/* eslint-disable-next-line max-len */}
      <b>Maximum number of tool layers exceeded{extra ? '' : '.'}</b>{extra}
      {' '}
      You will not be able to <b>pause</b> or <b>commit</b> this job
    </span>
  );
};

function ToolLayersCheckWarning (props) {
  return (
    <RunOperationWarning
      {...props}
      objectId={props.toolId}
      check={checkToolLayers}
      warning={WarningMessage}
    />
  );
}

ToolLayersCheckWarning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  toolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  skip: PropTypes.bool,
  type: PropTypes.string,
  showIcon: PropTypes.bool
};

ToolLayersCheckWarning.defaultProps = {
  type: 'warning',
  skip: false
};

export {WarningMessage};
export default ToolLayersCheckWarning;
