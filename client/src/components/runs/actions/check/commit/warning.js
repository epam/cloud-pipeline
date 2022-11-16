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
import commitCheck from './check';
import RunOperationWarning from '../common/warning';

const WarningMessage = (
  <span>This operation may fail due to <b>'Out of disk'</b> error</span>
);

function CommitCheckWarning (props) {
  return (
    <RunOperationWarning
      {...props}
      objectId={props.runId}
      check={commitCheck}
      warning={WarningMessage}
    />
  );
}

CommitCheckWarning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  type: PropTypes.string,
  showIcon: PropTypes.bool
};

export {WarningMessage};
export default CommitCheckWarning;
