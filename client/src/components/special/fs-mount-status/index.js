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
import classNames from 'classnames';
import styles from './fs-mount-status.css';

export const MountStatus = {
  active: 'ACTIVE',
  disableMount: 'MOUNT_DISABLED',
  readOnly: 'READ_ONLY'
};

const MountStatusNames = {
  [MountStatus.active]: 'Active',
  [MountStatus.disableMount]: 'Mount is disabled',
  [MountStatus.readOnly]: 'Read-only'
};

function FSMountStatus ({className, storage, status, style}) {
  let statusType = status;
  if (!statusType && storage) {
    const {mountStatus, type} = storage;
    statusType = type === 'NFS' ? mountStatus : undefined;
  }
  if (statusType) {
    return (
      <span
        className={
          classNames(
            styles.mountStatus,
            styles[statusType.toLowerCase()],
            className
          )
        }
        style={style}
      >
        {MountStatusNames[statusType] || statusType}
      </span>
    );
  }
  return null;
}

FSMountStatus.propTypes = {
  className: PropTypes.string,
  storage: PropTypes.object,
  status: PropTypes.string,
  style: PropTypes.object
};

export default FSMountStatus;
