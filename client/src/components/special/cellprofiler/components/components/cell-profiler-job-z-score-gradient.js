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
import styles from './cell-profiler-job-z-score-gradient.css';
import ColorPicker from '../../../color-picker';

function getColor (channels) {
  const [
    r = 1,
    g = 1,
    b = 1,
    a = 1
  ] = channels || [];
  return `rgba(${r * 255}, ${g * 255}, ${b * 255}, ${a})`;
}

function CellProfilerJobZScoreGradient (
  {
    className,
    colors,
    onChange,
    style
  }
) {
  const {
    negative,
    positive,
    zero
  } = colors || {};
  const negativeColor = getColor(negative);
  const positiveColor = getColor(positive);
  const zeroColor = getColor(zero);
  const onChangeColor = (color, type) => {
    const {r, g, b} = color;
    const payload = {
      negative,
      positive,
      zero
    };
    switch (type) {
      case -1: payload.negative = [r / 255.0, g / 255.0, b / 255.0, 1]; break;
      case 0: payload.zero = [r / 255.0, g / 255.0, b / 255.0, 1]; break;
      case 1: payload.positive = [r / 255.0, g / 255.0, b / 255.0, 1]; break;
      default: break;
    }
    if (typeof onChange === 'function') {
      onChange(payload);
    }
  };
  return (
    <div
      className={
        classNames(
          className,
          styles.gradientContainer
        )
      }
      style={style}
    >
      <div
        className={
          classNames(
            styles.gradient,
            'cp-bordered'
          )
        }
        style={{
          // eslint-disable-next-line max-len
          background: `linear-gradient(90deg, ${negativeColor} 0%, ${zeroColor} 50%, ${positiveColor} 100%)`
        }}
      />
      <div
        className={styles.gradientPickerContainer}
      >
        <ColorPicker
          color={negativeColor}
          channels
          onChange={(color) => onChangeColor(color, -1)}
        />
        <ColorPicker
          color={zeroColor}
          channels
          onChange={(color) => onChangeColor(color, 0)}
        />
        <ColorPicker
          color={positiveColor}
          channels
          onChange={(color) => onChangeColor(color, 1)}
        />
      </div>
    </div>
  );
}

CellProfilerJobZScoreGradient.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  colors: PropTypes.shape({
    negative: PropTypes.any,
    positive: PropTypes.any,
    zero: PropTypes.any
  }),
  onChange: PropTypes.func
};

export default CellProfilerJobZScoreGradient;
