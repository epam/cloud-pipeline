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
import {ColorPicker as AntdColorPicker} from 'antd';
import {buildColor, buildHexColor, parseColor} from '../../../themes/utilities/color-utilities';
import styles from './color-picker.module.css';

const COLOR_PRESENTER_RADIUS = 14;
const COLOR_PRESENTER_CENTER = COLOR_PRESENTER_RADIUS + 1;
const COLOR_PRESENTER_OPAQUE_MASK_STROKE = 2;

const maskPoints = [];
const width = 2 * COLOR_PRESENTER_RADIUS;
const step = COLOR_PRESENTER_OPAQUE_MASK_STROKE * 3;
for (let o = 0; o < width; o += step) {
  maskPoints.push([0, width - o, width - o, 0]);
  maskPoints.push([width, o, o, width]);
}
const MASK_PATH = [
  ...new Set(maskPoints.map((point) => `M${point[0]},${point[1]} L${point[2]},${point[3]}`)),
].join(' ');

const SVG_DEFS = (
  <defs key="defs">
    <mask id="mask" key="mask">
      <path
        key="mask-path"
        d={MASK_PATH}
        strokeWidth={COLOR_PRESENTER_OPAQUE_MASK_STROKE}
        stroke="white"
      />
    </mask>
  </defs>
);

const COLOR_PRESENTER_VIEW_BOX = `0 0 ${2 * COLOR_PRESENTER_CENTER} ${2 * COLOR_PRESENTER_CENTER}`;

function ColorPresenter({className, color, onClick, borderless, style, ignoreAlpha = false}) {
  if (!color) {
    return null;
  }
  const parsed = parseColor(color) || {r: 0, g: 0, b: 0};
  let {a = 1} = parsed;
  if (ignoreAlpha) {
    a = 1;
  }
  const opaqueColor = a === 1 ? undefined : {...parsed, a: 1};
  let opaqueGraphics;
  if (opaqueColor) {
    opaqueGraphics = (
      <circle
        cx={COLOR_PRESENTER_CENTER}
        cy={COLOR_PRESENTER_CENTER}
        r={COLOR_PRESENTER_RADIUS}
        fill={buildColor(opaqueColor)}
        mask="url(#mask)"
      />
    );
  }
  return (
    <svg
      className={classNames(className, styles.colorPresenter, 'color-presenter', {
        [styles.borderless]: borderless,
        borderless,
      })}
      viewBox={COLOR_PRESENTER_VIEW_BOX}
      onClick={onClick}
      style={style}
    >
      {SVG_DEFS}
      <circle
        cx={COLOR_PRESENTER_CENTER}
        cy={COLOR_PRESENTER_CENTER}
        r={COLOR_PRESENTER_RADIUS}
        fill={color}
      />
      {opaqueGraphics}
    </svg>
  );
}

class ColorPicker extends React.Component {
  onChangeComplete = (color) => {
    const {onChange, hex, channels, ignoreAlpha} = this.props;
    if (!onChange) {
      return;
    }
    const rgb = color.toRgb();
    const normalized = {
      r: rgb.r,
      g: rgb.g,
      b: rgb.b,
      a: ignoreAlpha ? 1.0 : (rgb.a ?? 1.0),
    };
    if (hex) {
      onChange(buildHexColor(normalized, ignoreAlpha));
    } else if (channels) {
      onChange(normalized);
    } else {
      onChange(buildColor(normalized));
    }
  };

  render() {
    const {color, disabled, ignoreAlpha} = this.props;
    if (disabled) {
      return (
        <ColorPresenter
          className={styles.colorPicker}
          color={color}
          style={{cursor: 'default'}}
          ignoreAlpha={ignoreAlpha}
        />
      );
    }
    const parsed = parseColor(color);
    return (
      <AntdColorPicker
        value={parsed ? buildHexColor(parsed, ignoreAlpha) : undefined}
        disabledAlpha={ignoreAlpha}
        showText={false}
        onChangeComplete={this.onChangeComplete}
      >
        <ColorPresenter className={styles.colorPicker} color={color} ignoreAlpha={ignoreAlpha} />
      </AntdColorPicker>
    );
  }
}

ColorPicker.propTypes = {
  color: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  hex: PropTypes.bool,
  channels: PropTypes.bool,
  ignoreAlpha: PropTypes.bool,
};

export {ColorPresenter};
export default ColorPicker;
