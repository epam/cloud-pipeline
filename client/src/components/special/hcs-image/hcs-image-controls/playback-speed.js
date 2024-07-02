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
import {Slider} from 'antd';
import styles from './hcs-image-controls.css';

function HcsVideoSpeedControl (
  {hcsVideoSource}
) {
  const {
    videoMode,
    delay,
    setPlaybackDelay
  } = hcsVideoSource || {};
  if (videoMode) {
    return (
      <div className={styles.videoSettings}>
        <div className={styles.header}>
          Frames delay: <b>{delay} second{delay !== 1 ? 's' : ''}</b>
        </div>
        <Slider
          min={1}
          max={10}
          step={1}
          value={delay}
          onChange={setPlaybackDelay}
        />
      </div>
    );
  }
  return null;
}

export default inject('hcsVideoSource')(observer(HcsVideoSpeedControl));
