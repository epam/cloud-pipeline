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
import {WarningMessage} from './warning';
import generateProvider from '../common';

const {
  CheckProvider,
  Warning,
  store,
  inject,
  getCheckInfo,
  getCheckResult
} = generateProvider({
  check: commitCheck,
  warning: WarningMessage
});

function CommitCheckProvider ({active, children, runId}) {
  return (
    <CheckProvider active={active} objectId={runId}>
      {children}
    </CheckProvider>
  );
}

CommitCheckProvider.propTypes = {
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  children: PropTypes.node,
  active: PropTypes.bool
};
CommitCheckProvider.defaultProps = {
  active: true
};
CommitCheckProvider.inject = inject;
CommitCheckProvider.store = store;
CommitCheckProvider.getCheckInfo = getCheckInfo;
CommitCheckProvider.getCheckResult = getCheckResult;
CommitCheckProvider.Warning = Warning;
CommitCheckProvider.Warning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  type: PropTypes.string
};
CommitCheckProvider.Warning.defaultProps = {
  type: 'warning'
};

export default CommitCheckProvider;
