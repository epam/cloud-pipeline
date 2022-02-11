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
import {Select} from 'antd';
import {constants} from '../hcs-image-viewer';
import styles from './hcs-image-controls.css';

const {ColorMapProfiles = []} = constants;

function ColorMap (
  {
    hcsViewerState
  }
) {
  if (!hcsViewerState || ColorMapProfiles.length === 0) {
    return null;
  }
  const {
    useColorMap,
    colorMap,
    pending
  } = hcsViewerState;
  if (!useColorMap) {
    return null;
  }
  return (
    <div
      className={styles.colorMap}
    >
      <span className={styles.header}>
        Blending mode:
      </span>
      <Select
        allowClear={colorMap !== ''}
        value={colorMap}
        disabled={pending}
        className={styles.colorMapSelector}
        onChange={(o) => hcsViewerState.changeColorMap(o || '')}
      >
        <Select.Option key="empty" value="">
          <i className="cp-text-not-important">Unset</i>
        </Select.Option>
        {
          ColorMapProfiles.map(profile => (
            <Select.Option key={profile} value={profile}>
              {profile}
            </Select.Option>
          ))
        }
      </Select>
    </div>
  );
}

export default inject('hcsViewerState')(observer(ColorMap));
