/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import moment from 'moment-timezone';
import {
  Tooltip,
  Icon
} from 'antd';
import displayDate from '../../../../../../utils/displayDate';
import {STATUS}
  from '../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import styles from './restore-status-icon.css';

const ICONS = {
  [STATUS.INITIATED]: 'clock-circle',
  [STATUS.RUNNING]: 'clock-circle',
  [STATUS.SUCCEEDED]: 'check-circle'
};
const COLORS = {
  [STATUS.INITIATED]: 'cp-primary',
  [STATUS.RUNNING]: 'cp-primary',
  [STATUS.SUCCEEDED]: 'cp-success'
};

function RestoreStatusIcon ({restoreInfo, children}) {
  if (!restoreInfo || !STATUS[restoreInfo.status]) {
    return children;
  }
  const end = displayDate(moment.utc(restoreInfo.restoredTill), 'YYYY-MM-DD');
  const start = displayDate(moment.utc(restoreInfo.started));
  const title = restoreInfo.status === STATUS.SUCCEEDED
    ? `Restored till ${end}`
    : `Restoring started at ${start}`;
  return (
    <span
      className={styles.iconsContainer}
    >
      <Tooltip
        title={title}
      >
        {children}
        <Icon
          type={ICONS[restoreInfo.status]}
          style={{
            fontSize: '11px',
            position: 'absolute',
            right: '-5px',
            top: 'calc(50% - 3px)',
            transform: 'translateY(-50%)'
          }}
          className={classNames(
            COLORS[restoreInfo.status],
            styles.subIcon
          )}
        />
      </Tooltip>
    </span>
  );
};

RestoreStatusIcon.propTypes = {
  restoreInfo: PropTypes.shape({
    folder: PropTypes.object,
    files: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
  })
};

export {STATUS};
export default RestoreStatusIcon;
