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
import {inject, observer} from 'mobx-react';
import {Checkbox, Select} from 'antd';

import styles from './hcs-image-controls.css';

@inject('hcsViewerState')
@observer
class HcsLensControl extends React.Component {
  render () {
    const {hcsViewerState = {}} = this.props;
    const {
      channels = [],
      useLens,
      lensEnabled,
      lensChannel,
      pending
    } = hcsViewerState;
    if (useLens && channels.length > 1) {
      return (
        <div className={styles.lensContainer}>
          <Checkbox
            disabled={pending}
            checked={lensEnabled}
            onChange={(e) => hcsViewerState.changeLensMode(e.target.checked)}
          >
            Lens
          </Checkbox>
          <Select
            disabled={pending || !lensEnabled}
            style={{flex: '1 1 auto'}}
            onChange={(channelIndex) => hcsViewerState.changeLensChannel(channelIndex)}
            value={lensChannel >= 0 ? `${lensChannel}` : undefined}
          >
            {channels.map((channel) => (
              <Select.Option
                key={channel.identifier}
                value={`${channel.index}`}
              >
                {channel.name}
              </Select.Option>))
            }
          </Select>
        </div>
      );
    }
    return null;
  }
}

export default HcsLensControl;
