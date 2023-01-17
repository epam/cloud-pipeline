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
import {Radio} from 'antd';
import styles from './hcs-image-controls.css';

function HcsVideoGenerationModeToggle (
  {hcsVideoSource}
) {
  const {
    videoMode
  } = hcsVideoSource || {};
  if (
    videoMode &&
    hcsVideoSource.hasTimePointsAndZPlanes
  ) {
    const onChange = (event) => {
      hcsVideoSource.videoByZPlanes = event.target.value === 1;
      (hcsVideoSource.generateUrl)();
    };
    return (
      <div
        className={styles.videoSettings}
        style={{display: 'flex', alignItems: 'center', paddingTop: 0}}
      >
        <div
          className={styles.header}
          style={{marginRight: 5}}
        >
          Video:
        </div>
        <Radio.Group
          onChange={onChange}
          value={Number(hcsVideoSource.videoByZPlanes)}
          className={styles.videoGenerationModeToggle}
        >
          <Radio.Button value={0}>By time points</Radio.Button>
          <Radio.Button value={1}>By z planes</Radio.Button>
        </Radio.Group>
      </div>
    );
  }
  return null;
}

export default inject('hcsVideoSource')(observer(HcsVideoGenerationModeToggle));
