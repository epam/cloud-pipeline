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
import styles from './hcs-video.css';

function HcsVideoPlayer ({videoSource}) {
  if (videoSource) {
    return (
      <div className={styles.hcsVideoContainer}>
        <video controls autoPlay >
          <source src={videoSource} type="video/mp4" />
          <source src={videoSource} type="video/ogg" />
          <source src={videoSource} type="video/webm" />
          <p>Your browser cannot play the provided video file.</p>
        </video>
      </div>
    );
  }
  return null;
}

export default HcsVideoPlayer;
