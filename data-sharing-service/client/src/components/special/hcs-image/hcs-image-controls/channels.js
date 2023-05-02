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
import {inject, observer} from 'mobx-react';
import {
  Icon,
  Checkbox
} from 'antd';
import Channel from './channel';
import styles from './hcs-image-controls.css';

function HcsImageChannelsControl (
  {
    allowLockChannels,
    hcsViewerState
  }
) {
  const {
    channels = [],
    pending,
    allChannelsLocked,
    lockedChannels
  } = hcsViewerState || {};
  if (pending && channels.length === 0) {
    return (
      <div className={styles.channels}>
        <i className="cp-text-not-important">Loading channels...</i>
      </div>
    );
  }
  if (channels.length === 0) {
    return null;
  }
  return (
    <div className={styles.channels}>
      <div className={styles.header}>
        <span>
          Channels:
          {pending && (<Icon type="loading" style={{marginLeft: 5}} />)}
        </span>
        {
          allowLockChannels && (
            <Checkbox
              onChange={(e) => hcsViewerState.setChannelsLocked(e.target.checked)}
              checked={allChannelsLocked}
              indeterminate={
                lockedChannels.length > 0 && lockedChannels.length < channels.length
              }
            >
              Persist channels state
            </Checkbox>
          )
        }
      </div>
      {
        channels.map(channel => (
          <Channel
            key={channel.identifier}
            identifier={channel.identifier}
            name={channel.name}
            allowLockChannel={allowLockChannels}
            visible={channel.visible}
            locked={lockedChannels.includes(channel.name)}
            color={channel.color}
            domain={channel.domain}
            contrastLimits={channel.contrastLimits}
            loading={pending}
            onVisibilityChanged={
              (visible) => hcsViewerState.changeChannelVisibility(channel, visible)
            }
            onLockedChanged={
              (locked) => hcsViewerState.setChannelLocked(channel, locked)
            }
            onContrastLimitsChanged={
              (limits) => hcsViewerState.changeChannelContrastLimits(channel, limits)
            }
            onColorChanged={
              (color) => hcsViewerState.changeChannelColor(channel, color)
            }
          />
        ))
      }
    </div>
  );
}

HcsImageChannelsControl.propTypes = {
  allowLockChannels: PropTypes.bool
};

HcsImageChannelsControl.defaultProps = {
  allowLockChannels: true
};

export default inject('hcsViewerState')(observer(HcsImageChannelsControl));
