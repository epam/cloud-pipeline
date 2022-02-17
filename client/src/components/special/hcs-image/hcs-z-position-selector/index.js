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
import {Slider} from 'antd';

import {Z_POSITIONS_LIMIT} from '../utilities/constants';
import styles from './hcs-z-position-selector.css';

function HcsZPositionSelector (props) {
  const {
    selectedPosition,
    positions = [],
    onZPositionChange,
    type
  } = props;
  if (positions.filter(p => p).length > 1) {
    const sliderIsVertical = !type || type === 'vertical';
    const maxZValue = positions.reduce((max, {z, title}) => {
      if (z > max.z) {
        max = {z, title};
      }
      return max;
    }, positions[0]);
    const minZValue = positions.reduce((min, {z, title}) => {
      if (z < min.z) {
        min = {z, title};
      }
      return min;
    }, positions[0]);

    let marks;
    if (positions.filter(p => p).length <= Z_POSITIONS_LIMIT) {
      marks = positions.reduce((points, {z, title}) => {
        points[z] = title;
        return points;
      }, {});
    } else {
      marks = {
        [minZValue.z]: minZValue.title,
        [maxZValue.z]: minZValue.title
      };
    }

    return (
      <div className={classNames(
        'cp-bordered',
        {
          [styles.vertContainer]: sliderIsVertical,
          [styles.horContainer]: !sliderIsVertical
        }
      )}>
        <h4 className={styles.title}>Stack</h4>
        <Slider
          vertical={sliderIsVertical}
          defaultValue={selectedPosition}
          marks={marks}
          max={maxZValue.z}
          min={minZValue.z}
          onChange={onZPositionChange}
          className={classNames(
            'cp-hcs-z-position-slider',
            {[styles.slider]: sliderIsVertical}
          )}
        />
      </div>
    );
  }
  return null;
}

export default HcsZPositionSelector;
