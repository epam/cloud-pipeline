/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import quotaGroups from './quota-groups';

const actions = {
  notify: 'NOTIFY',
  readMode: 'READ_MODE',
  readModeAndDisableNewJobs: 'DISABLE_NEW_JOBS',
  readModeAndStopAllJobs: 'STOP_JOBS',
  block: 'BLOCK'
};

const actionNames = {
  [actions.notify]: 'Notify',
  [actions.readMode]: 'Read-only mode',
  [actions.readModeAndDisableNewJobs]: 'Disable new jobs',
  [actions.readModeAndStopAllJobs]: 'Stop all jobs',
  [actions.block]: 'Block'
};

function QuotaAction (
  {
    action,
    className,
    style
  }
) {
  if (!action) {
    return null;
  }
  const {
    threshold = 0,
    actions = []
  } = action;
  if (actions.length === 0) {
    return null;
  }
  const actionsDescription = actions
    .map(action => (actionNames[action] || action)
      .toLowerCase()).join(', ');
  return (
    <span
      className={className}
      style={style}
    >
      {Math.round(100.0 * Number(threshold)) / 100.0}%: {actionsDescription}
    </span>
  );
}

QuotaAction.propTypes = {
  action: PropTypes.shape({
    threshold: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    actions: PropTypes.object
  })
};

const actionsByGroup = {
  [quotaGroups.global]: [
    actions.notify,
    actions.readMode,
    actions.readModeAndStopAllJobs,
    actions.readModeAndDisableNewJobs,
    actions.block
  ],
  [quotaGroups.computeInstances]: [
    actions.notify,
    actions.readModeAndStopAllJobs,
    actions.readModeAndDisableNewJobs,
    actions.block
  ],
  [quotaGroups.storages]: [
    actions.notify,
    actions.readMode,
    actions.block
  ]
};

export {
  actionNames,
  actionsByGroup,
  QuotaAction
};
export default actions;
