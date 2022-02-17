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
import {
  Checkbox,
  Popover,
  Slider
} from 'antd';
import {ChromePicker} from 'react-color';
import styles from './hcs-image-controls.css';

class Channel extends React.PureComponent {
  state = {
    colorPickerVisible: false
  };

  renderColorConfiguration = () => {
    const {
      color = [],
      loading,
      onColorChanged
    } = this.props;
    if (color.length === 3) {
      const presenter = (
        <div
          key="color-presenter"
          className={styles.colorPresenter}
        >
          <div
            className={styles.fill}
            style={{
              color: `rgb(${color.join(', ')})`
            }}
          >
            {'\u00A0'}
          </div>
        </div>
      );
      if (loading || !onColorChanged) {
        return presenter;
      }
      const {colorPickerVisible} = this.state;
      const handleColorPickerVisibility = (visible) => this.setState({
        colorPickerVisible: visible
      });
      const value = {
        r: color[0],
        g: color[1],
        b: color[2]
      };
      const onChange = (options) => {
        const {rgb = {}} = options;
        const {r = 255, g = 255, b = 255} = rgb;
        if (onColorChanged) {
          onColorChanged([r, g, b]);
        }
      };
      return (
        <Popover
          overlayClassName="popover-overlay-content-no-padding"
          onVisibleChange={handleColorPickerVisibility}
          key="color picker"
          trigger={['click']}
          content={colorPickerVisible && (
            <ChromePicker
              color={value}
              onChange={onChange}
              disableAlpha
            />
          )}
        >
          {presenter}
        </Popover>
      );
    }
    return null;
  };

  renderContrastConfiguration = () => {
    const {
      domain = [],
      contrastLimits = [],
      loading,
      onContrastLimitsChanged
    } = this.props;
    if (
      domain.length === contrastLimits.length &&
      contrastLimits.length === 2
    ) {
      return (
        <Slider
          style={{margin: '2px 6px'}}
          key="contrast limits"
          range
          value={contrastLimits.slice()}
          min={domain[0]}
          max={domain[1]}
          disabled={loading}
          onChange={onContrastLimitsChanged}
        />
      );
    }
    return null;
  };

  render () {
    const {
      className,
      name,
      style,
      visible,
      loading,
      onVisibilityChanged
    } = this.props;
    return (
      <div
        className={
          classNames(
            className,
            styles.channel
          )
        }
        style={style}
      >
        <div
          className={styles.header}
        >
          <Checkbox
            disabled={loading}
            style={{marginRight: 5}}
            checked={visible}
            onChange={e => onVisibilityChanged ? onVisibilityChanged(e.target.checked) : {}}
          />
          <b>{name}</b>
          <div style={{marginLeft: 'auto'}}>
            {this.renderColorConfiguration()}
          </div>
        </div>
        {this.renderContrastConfiguration()}
      </div>
    );
  }
}

Channel.propTypes = {
  className: PropTypes.string,
  identifier: PropTypes.string,
  name: PropTypes.string,
  color: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  domain: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  contrastLimits: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  loading: PropTypes.bool,
  onContrastLimitsChanged: PropTypes.func,
  onColorChanged: PropTypes.func,
  onVisibilityChanged: PropTypes.func,
  style: PropTypes.object,
  visible: PropTypes.bool
};

export default Channel;
