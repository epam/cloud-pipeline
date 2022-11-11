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
import {observer} from 'mobx-react';
import styles from './hcs-z-position-selector.css';

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

function HcsZPositionSelector (props) {
  const {
    className,
    image,
    selection: rawSelection = [0],
    onChange,
    multiple
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
    if (typeof onChange === 'function') {
      if (multiple && isSelected(z)) {
        newSelection = [...new Set(selection.filter(o => o !== z))];
      } else if (multiple) {
        newSelection = [...new Set([...selection, z])];
      } else {
        newSelection = [z];
      }
      onChange(newSelection);
    }
  };
  const onSelectAll = () => {
    if (typeof onChange === 'function') {
      onChange(positions.map(o => o.z));
    }
  };
  const sorted = positions
    .slice()
    .sort(zPositionSorter);
  return (
    <div
      className={
        classNames(
          className,
          styles.container
        )}
    >
      <div
        className={styles.title}
      >
        Z-planes
      </div>
      <div
        className={styles.sliderContainer}
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
  multiple: PropTypes.bool
};

export default observer(HcsZPositionSelector);
