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
import classNames from 'classnames';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Button, Dropdown, Icon, Slider} from 'antd';
import styles from './hcs-video.css';

function VideoSettingsWrapper ({selectable, children}) {
  return (
    <div
      className={styles.videoSettings}
    >
      {children}
    </div>
  );
}

class VideoButton extends React.Component {
  state = {
    showVideoSettings: false
  };

  changeVideoSettingsOverlayVisibility = (visible) => {
    this.setState({
      showVideoSettings: visible
    });
  };

  onChangeModeClicked = () => {
    const {
      videoSource
    } = this.props;
    if (videoSource) {
      videoSource.setVideoMode(!videoSource.videoMode);
    }
  }

  render () {
    const {
      className,
      available,
      videoSource,
      style
    } = this.props;
    const {
      showVideoSettings
    } = this.state;
    if (!videoSource || !available) {
      return null;
    }
    const iconType = videoSource.videoMode
      ? 'picture'
      : 'play-circle';
    return (
      <Button.Group
        className={
          classNames(
            className,
            styles.videoBtnGroup
          )
        }
        style={style}
      >
        <Button
          size="small"
          className={styles.btn}
          onClick={this.onChangeModeClicked}
          disabled={!videoSource.initialized}
        >
          <Icon
            type={iconType}
            className="cp-larger"
          />
        </Button>
        {
          videoSource.videoMode && (
            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              overlay={(
                <VideoSettingsWrapper>
                  <div>
                    <div style={{margin: '5px 0'}}>
                      Frames per second: {videoSource.playbackSpeed}
                    </div>
                    <Slider
                      min={1}
                      max={10}
                      step={1}
                      value={videoSource.playbackSpeed}
                      onChange={videoSource.setPlaybackSpeed}
                    />
                  </div>
                </VideoSettingsWrapper>
              )}
              visible={showVideoSettings}
              onVisibleChange={this.changeVideoSettingsOverlayVisibility}
            >
              <Button
                size="small"
                className={styles.btn}
              >
                <Icon
                  type="down"
                />
              </Button>
            </Dropdown>
          )
        }
      </Button.Group>
    );
  }
}

VideoButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  available: PropTypes.bool,
  videoSource: PropTypes.object
};

export default observer(VideoButton);
