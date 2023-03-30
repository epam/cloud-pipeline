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
import {observer} from 'mobx-react';
import {Alert} from 'antd';
import classNames from 'classnames';
import styles from './hcs-video.css';
import LoadingView from '../../LoadingView';

function HcsVideoPlayer ({className, style, videoSource}) {
  if (videoSource) {
    const disablePictureInPicture = (videoTag) => {
      if (videoTag) {
        videoTag.disablePictureInPicture = true;
      }
    };
    if (videoSource.videoError) {
      return (
        <div
          className={
            classNames(
              styles.hcsVideoContainer,
              className
            )
          }
          style={style}
        >
          <Alert
            message={videoSource.videoError}
            type="error"
          />
        </div>
      );
    }
    if (videoSource.videoPending || !videoSource.videoUrl) {
      return (
        <div
          className={
            classNames(
              styles.hcsVideoContainer,
              className
            )
          }
          style={style}
        >
          <LoadingView />
        </div>
      );
    }
    const onLoadStart = () => typeof videoSource.videoAccessCallback === 'function'
      ? videoSource.videoAccessCallback()
      : undefined;
    return (
      <div
        className={
          classNames(
            styles.hcsVideoContainer,
            className
          )
        }
        style={style}
      >
        <video
          controls
          controlsList="nodownload noplaybackrate"
          ref={disablePictureInPicture}
          autoPlay
          loop={videoSource.loop}
          crossOrigin={videoSource.crossOrigin}
          onLoadStart={onLoadStart}
        >
          <source src={videoSource.videoUrl} type={videoSource.videoSourceType} />
          <p>Your browser cannot play the provided video file.</p>
        </video>
      </div>
    );
  }
  return null;
}

export default observer(HcsVideoPlayer);
