/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import {Checkbox, Tooltip} from 'antd';
import Slider from 'rc-slider';
import {observer} from 'mobx-react';
import styles from './hcs-z-position-selector.css';

const MIN_SLIDER_MARK_SPACE = 25;
const MIN_SLIDER_HEIGHT = 200;

export const Z_SELECTOR_MODES = {
  badge: 'badge',
  slider: 'slider'
};

function zPositionSorter (a, b) {
  return a.z - b.z;
}

function buildZPositionsArray (max, zSize, zUnit) {
  const basePower = Math.floor(Math.log10(zSize || 1));
  const base = 10 ** basePower;
  const decimalDigits = 2;
  const format = o => {
    const rounded = Math.round(o / base * (10 ** decimalDigits)) / (10 ** decimalDigits);
    const postfix = basePower !== 0 ? `e${basePower}` : '';
    return [
      `${rounded}${postfix}`,
      zUnit
    ].filter(Boolean).join('');
  };
  return (new Array(max))
    .fill('')
    .map((o, z) => ({
      z,
      title: format((z + 1) * zSize),
      width: format((max + 1) * zSize).length
    }));
}

@observer
class HcsZPositionSlider extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    selection: PropTypes.arrayOf(PropTypes.number),
    positions: PropTypes.array
  };

  state={
    value: 0
  }

  sliderRef;
  sliderContainerRef;
  arrangeRAF;

  @observable
  _height;

  @observable
  _marks = {};

  @computed
  get marks () {
    return this._marks;
  }

  @computed
  get height () {
    return this._height;
  }

  componentDidMount () {
    this.arrangeRAF = requestAnimationFrame(this.arrangeMarks);
    this.updateValueFromProps();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.selection !== this.props.selection) {
      this.updateValueFromProps();
    }
  }

  componentWillUnmount () {
    cancelAnimationFrame(this.arrangeRAF);
  }

  updateValueFromProps = () => {
    const {selection} = this.props;
    const {value} = this.state;
    if (selection && selection[0] !== undefined && selection[0] !== value) {
      this.setState({value: selection[0]});
    }
  };

  arrangeMarks = () => {
    this.arrangeRAF = requestAnimationFrame(this.arrangeMarks);
    if (!this.sliderRef || !this.sliderContainerRef) {
      return;
    }
    const {positions} = this.props;
    const {top} = this.sliderRef.getBoundingClientRect();
    const spaceBelow = window.innerHeight - top;
    const height = positions.length <= 20
      ? MIN_SLIDER_HEIGHT
      : Math.max(spaceBelow - 40, MIN_SLIDER_HEIGHT);
    if (this.height === height) {
      return;
    }
    this._height = height;
    const step = Math.ceil(MIN_SLIDER_MARK_SPACE / (this.height / positions.length));
    this._marks = positions.reduce((acc, item, index) => {
      const isLast = index === positions.length - 1;
      const isFirst = index === 0;
      if (!isLast && positions.length - index < step) {
        return acc;
      }
      if (
        isFirst ||
        isLast ||
        index % step === 0
      ) {
        acc[item.z] = item.title;
      }
      return acc;
    }, {});
  };

  onChangeSliderValue = value => this.setState({value});

  render () {
    const {selection, positions, onChange} = this.props;
    const getZPositionObject = (z) => positions.find(item => item.z === z);
    return (
      <div
        className={styles.sliderContainer}
        ref={(el) => { this.sliderContainerRef = el; }}
      >
        {selection?.length ? (
          <p className={classNames(
            styles.title,
            'cp-title'
          )}>
            Position: {getZPositionObject(selection[0])?.title || ''}
          </p>
        ) : null}
        <div
          className={styles.sliderContainer}
          style={{paddingLeft: 5, minHeight: this.height}}
          ref={(el) => { this.sliderRef = el; }}
        >
          <Slider
            className="cp-hcs-z-position-slider"
            min={0}
            max={Math.max(positions.length - 1, 0)}
            value={this.state.value}
            vertical
            marks={this.marks}
            onChange={this.onChangeSliderValue}
            onAfterChange={onChange}
            style={{flex: 1}}
            handle={({value, dragging, ...restProps}) => {
              return (
                <Tooltip
                  overlay={getZPositionObject(value)?.title || ''}
                  visible={dragging}
                  placement="top"
                  key={value}
                >
                  <Slider.Handle {...restProps} />
                </Tooltip>
              );
            }}
          />
        </div>
      </div>
    );
  };
}

function HcsZPositionSelector (props) {
  const {
    className,
    style,
    image,
    selection: rawSelection = [0],
    onChange,
    mergeZPlanes,
    multiple,
    mode,
    onChangeMode: onChangeModeProp,
    sliderMinPositionsTreshold = 2
  } = props;
  if (!image) {
    return null;
  }
  const {
    depth = 1,
    physicalDepthSize = 1,
    depthUnit = ''
  } = image;
  const positions = buildZPositionsArray(depth, physicalDepthSize, depthUnit);
  const sliderModeAvailable = positions.length > sliderMinPositionsTreshold;
  if (positions.length <= 1) {
    return null;
  }
  const selection = rawSelection.length > 0
    ? rawSelection
    : [0];
  const isSelected = z => selection.includes(z);
  const onChangeWrapper = (z, event) => {
    const multiple = event && event.shiftKey;
    let newSelection = [z];
    let newMergeZPlanes = mergeZPlanes;
    if (typeof onChange === 'function') {
      if (sliderModeAvailable && mode === Z_SELECTOR_MODES.slider) {
        newSelection = [z];
      } else if (multiple && isSelected(z)) {
        newSelection = [...new Set(selection.filter(o => o !== z))];
      } else if (multiple) {
        newSelection = [...new Set([...selection, z])];
      } else {
        newSelection = [z];
      }
      newMergeZPlanes = newSelection.length > 1 ? mergeZPlanes : false;
      onChange(newSelection, newMergeZPlanes);
    }
  };
  const onSelectAll = () => {
    if (typeof onChange === 'function') {
      onChange(positions.map(o => o.z), mergeZPlanes);
    }
  };
  const onChangeMerge = (event) => {
    if (typeof onChange === 'function') {
      onChange(selection, event.target.checked);
    }
  };
  const onChangeMode = () => {
    const toggler = {
      [Z_SELECTOR_MODES.badge]: Z_SELECTOR_MODES.slider,
      [Z_SELECTOR_MODES.slider]: Z_SELECTOR_MODES.badge
    };
    if (mode === Z_SELECTOR_MODES.badge) {
      onChangeWrapper(selection[0]);
    }
    onChangeModeProp && onChangeModeProp(toggler[mode]);
  };
  const sorted = positions
    .slice()
    .sort(zPositionSorter);
  const renderBadges = () => {
    return (
      <div
        className={styles.badgesContainer}
      >
        {
          sorted.map(position => (
            <div
              key={`z-${position.z}`}
              className={
                classNames(
                  styles.zItem,
                  {
                    [styles.active]: isSelected(position.z),
                    'cp-timepoint-button-active': isSelected(position.z),
                    'cp-timepoint-button': !isSelected(position.z)
                  }
                )
              }
              style={{
                minWidth: `${position.width}em`
              }}
              onClick={(event) => onChangeWrapper(position.z, event)}
            >
              {position.title}
            </div>
          ))
        }
        {
          positions.length > selection.length && multiple && (
            <a
              className={styles.zItem}
              onClick={onSelectAll}
            >
              Select all
            </a>
          )
        }
      </div>
    );
  };
  const renderSlider = () => {
    return (
      <HcsZPositionSlider
        onChange={onChangeWrapper}
        selection={selection}
        positions={sorted}
      />
    );
  };
  return (
    <div
      className={
        classNames(
          className,
          styles.container
        )}
      style={style}
    >
      <div
        className={styles.title}
      >
        <b>Z-planes</b>
        {
          selection.length > 1 && (
            <div>
              <Checkbox
                onChange={onChangeMerge}
                checked={mergeZPlanes}
              >
                Use planes projection
              </Checkbox>
            </div>
          )
        }
        {
          sliderModeAvailable && (
            <div>
              <Checkbox
                onChange={onChangeMode}
                checked={mode === Z_SELECTOR_MODES.slider}
              >
                Slider view
              </Checkbox>
            </div>
          )
        }
      </div>
      {mode === Z_SELECTOR_MODES.slider && sliderModeAvailable
        ? renderSlider()
        : renderBadges()
      }
    </div>
  );
}

HcsZPositionSelector.propTypes = {
  className: PropTypes.string,
  image: PropTypes.object,
  selection: PropTypes.oneOfType([
    PropTypes.object,
    PropTypes.arrayOf(PropTypes.number)
  ]),
  onChange: PropTypes.func,
  multiple: PropTypes.bool,
  mergeZPlanes: PropTypes.bool,
  mode: PropTypes.string,
  sliderMinPositionsTreshold: PropTypes.number,
  onChangeMode: PropTypes.func
};

export default observer(HcsZPositionSelector);
