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
  Icon,
  Slider
} from 'antd';
import ColorPicker from '../../color-picker';
import styles from './hcs-image-controls.css';

function LockCheckbox (
  {
    disabled,
    style,
    locked,
    onLockedChanged
  }
) {
  const onClick = (event) => {
    if (event) {
      event.stopPropagation();
    }
    if (typeof onLockedChanged === 'function') {
      onLockedChanged(!locked);
    }
  };
  return (
    <Icon
      style={{
        cursor: 'pointer',
        fontSize: 'larger',
        opacity: locked && !disabled ? 1 : 0.25,
        ...(style || {})
      }}
      type={locked ? 'lock' : 'unlock'}
      onClick={onClick}
    />
  );
}

class Channel extends React.PureComponent {
  state = {
    colorPickerVisible: false
  };

  renderColorConfiguration = () => {
    const {
      color = [],
      onColorChanged
    } = this.props;
    if (color.length === 3) {
      const onChange = (channels) => {
        const {r = 255, g = 255, b = 255} = channels;
        if (onColorChanged) {
          onColorChanged([r, g, b]);
        }
      };
      return (
        <ColorPicker
          color={`rgb(${color.join(',')})`}
          onChange={onChange}
          ignoreAlpha
          channels
        />
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
      locked,
      loading,
      onVisibilityChanged,
      onLockedChanged
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
          <LockCheckbox
            disabled={loading}
            locked={locked}
            onLockedChanged={onLockedChanged}
            style={{
              marginRight: 5
            }}
          />
          <Checkbox
            disabled={loading}
            checked={visible}
            onChange={e => onVisibilityChanged ? onVisibilityChanged(e.target.checked) : {}}
          >
            <b>{name}</b>
            {
              locked && (
                <i
                  className="cp-text-not-important"
                  style={{fontWeight: 'normal'}}
                >
                  {' - locked'}
                </i>
              )
            }
          </Checkbox>
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
  onLockedChanged: PropTypes.func,
  style: PropTypes.object,
  visible: PropTypes.bool,
  locked: PropTypes.bool
};

export default Channel;
