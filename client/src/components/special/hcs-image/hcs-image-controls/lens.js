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
