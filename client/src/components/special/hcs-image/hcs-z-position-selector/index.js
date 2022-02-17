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

import styles from './hcs-z-position-selector.css';

function HcsZPositionSelector (props) {
  const {
    selectedPosition,
    positions = [],
    onZPositionChange
  } = props;

  if (positions.filter(p => p).length > 1) {
    const marks = positions.reduce((points, {z, title}) => {
      points[z] = title;
      return points;
    }, {});
    const zValues = Object.keys(marks);
    const maxZValue = zValues.reduce((max, mark) => {
      if (mark > max) {
        max = mark;
      }
      return Number(max);
    }, zValues[0]);
    const minZValue = zValues.reduce((min, mark) => {
      if (mark < min) {
        min = mark;
      }
      return Number(min);
    }, zValues[0]);

    return (
      <div className={classNames(styles.container, 'cp-bordered')}>
        <h4 className={styles.title}>Stack</h4>
        <Slider
          vertical
          defaultValue={selectedPosition}
          marks={marks}
          max={maxZValue}
          min={minZValue}
          onChange={onZPositionChange}
          className={classNames('cp-hcs-z-position-slider', styles.slider)}
        />
      </div>
    );
  }
  return null;
}

export default HcsZPositionSelector;
