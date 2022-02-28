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
import classNames from 'classnames';
import {Slider} from 'antd';
import {inject, observer} from 'mobx-react';

import {MAX_Z_POSITIONS_TO_DISPLAY} from '../utilities/constants';
import styles from './hcs-z-position-selector.css';

const SelectorType = {
  vertical: 'vertical',
  horizontal: 'horizontal'
};

const MAX_HEIGHT_PER_TICK = 45;

function zPositionSorter (a, b) {
  return a.z - b.z;
}

function HcsZPositionSelector (props) {
  const {
    className,
    type = SelectorType.horizontal,
    hcsViewerState
  } = props;
  if (!hcsViewerState) {
    return null;
  }
  const {
    availableZPositions: positions = [],
    imageZPosition: value
  } = hcsViewerState;
  const onChange = z => hcsViewerState.changeGlobalZPosition(z);
  if (positions.length > 1) {
    const sorted = positions
      .slice()
      .sort(zPositionSorter);
    const tipFormatter = z => {
      const item = sorted.find(o => o.z === z);
      if (item) {
        return item.title;
      }
      return null;
    };
    const [first] = sorted;
    const last = sorted[sorted.length - 1];
    const firstTitleLength = (first.title || '').length;
    const lastTitleLength = (last.title || '').length;
    const getPadding = titleLength => `${Math.ceil(titleLength / 2)}em`;
    const max = last.z;
    const min = first.z;
    let marks = {
      [first.z]: first.title,
      [last.z]: last.title
    };
    if (positions.length <= MAX_Z_POSITIONS_TO_DISPLAY) {
      marks = positions.reduce((points, {z, title}) => ({
        ...points,
        [z]: title
      }), {});
    }
    const verticalStyle = {
      height: positions.length * MAX_HEIGHT_PER_TICK
    };
    const horizontalStyle = {
      paddingLeft: getPadding(firstTitleLength),
      paddingRight: getPadding(lastTitleLength)
    };
    return (
      <div
        className={
          classNames(
            className,
            styles.container,
            {
              [styles.horizontal]: type === SelectorType.horizontal
            }
          )}
      >
        <div
          className={styles.title}
        >
          Z-plane
        </div>
        <div
          className={styles.sliderContainer}
          style={
            type === SelectorType.vertical
              ? verticalStyle
              : horizontalStyle
          }
        >
          <Slider
            className={'cp-hcs-z-position-slider'}
            vertical={type === SelectorType.vertical}
            included={false}
            dots
            value={value}
            marks={marks}
            max={max}
            min={min}
            step={1}
            onChange={onChange}
            tipFormatter={tipFormatter}
          />
        </div>
      </div>
    );
  }
  return null;
}

HcsZPositionSelector.propTypes = {
  className: PropTypes.string,
  type: PropTypes.oneOf([
    SelectorType.vertical,
    SelectorType.horizontal
  ])
};

HcsZPositionSelector.defaultProps = {
  type: SelectorType.horizontal
};

const selector = inject('hcsViewerState')(observer(HcsZPositionSelector));

selector.Type = SelectorType;

export default selector;
