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
import {ChromePicker} from 'react-color';
import {
  Popover
} from 'antd';
import {
  buildColor, buildHexColor,
  parseColor
} from '../color-utilities';
import styles from './color-picker.css';

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
  ...(new Set(maskPoints.map(point => `M${point[0]},${point[1]} L${point[2]},${point[3]}`)))
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

function ColorPresenter (
  {
    className,
    color,
    onClick,
    borderless,
    style,
    ignoreAlpha = false
  }
) {
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
      className={
        classNames(
          className,
          styles.colorPresenter,
          'color-presenter',
          {
            [styles.borderless]: borderless,
            borderless
          }
        )
      }
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
  state = {
    visible: false
  };

  onVisibilityChange = (visible) => {
    this.setState({
      visible
    });
  };

  onChange = (e) => {
    const {
      rgb
    } = e;
    const {
      onChange,
      hex,
      channels,
      ignoreAlpha
    } = this.props;
    if (!onChange) {
      return;
    }
    if (hex) {
      onChange(buildHexColor(rgb, ignoreAlpha));
    } else if (channels) {
      const {r, g, b, a} = rgb;
      onChange({
        r,
        g,
        b,
        a: ignoreAlpha ? 1.0 : (a || 1.0)
      });
    } else {
      onChange(buildColor(rgb));
    }
  }

  render () {
    const {
      color,
      disabled,
      ignoreAlpha
    } = this.props;
    const {visible} = this.state;
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
    return (
      <Popover
        onVisibleChange={this.onVisibilityChange}
        trigger={['click']}
        content={
          visible &&
          (
            <ChromePicker
              color={parseColor(color)}
              onChangeComplete={this.onChange}
            />
          )
        }
      >
        <ColorPresenter
          className={styles.colorPicker}
          onClick={() => this.onVisibilityChange(true)}
          color={color}
          ignoreAlpha={ignoreAlpha}
        />
      </Popover>
    );
  }
}

ColorPicker.propTypes = {
  color: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  hex: PropTypes.bool,
  channels: PropTypes.bool,
  ignoreAlpha: PropTypes.bool
};

export {ColorPresenter};
export default ColorPicker;
