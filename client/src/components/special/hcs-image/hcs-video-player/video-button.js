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
import {observer} from 'mobx-react';
import {Button, Icon} from 'antd';

class VideoButton extends React.Component {
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
      videoSource
    } = this.props;
    if (!videoSource || !available) {
      return null;
    }
    const iconType = videoSource.videoMode
      ? 'picture'
      : 'video-camera';
    return (
      <Button
        size="small"
        className={className}
        onClick={this.onChangeModeClicked}
        disabled={!videoSource.initialized}
      >
        <Icon
          type={iconType}
          className="cp-larger"
        />
      </Button>
    );
  }
}

VideoButton.propTypes = {
  className: PropTypes.string,
  available: PropTypes.bool,
  videoSource: PropTypes.object
};

export default observer(VideoButton);
