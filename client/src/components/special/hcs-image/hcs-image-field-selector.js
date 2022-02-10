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
import HcsControlGrid from './hcs-control-grid';
import styles from './hcs-image.css';

class HcsImageFieldSelector extends React.Component {
  render () {
    const {
      className,
      fields = [],
      selectedField,
      style,
      onChange,
      width,
      height,
      wellName,
      wellRadius
    } = this.props;
    const selected = fields.find(o => o.id === selectedField);
    const onChangeField = ({row = 0, column = 0} = {}) => {
      if (onChange) {
        onChange({x: column + 1, y: row + 1});
      }
    };
    return (
      <div
        className={
          classNames(
            styles.selectorContainer,
            className
          )
        }
        style={style}
      >
        <HcsControlGrid
          className={styles.selector}
          rows={height}
          columns={width}
          dataCells={fields.map(o => ({row: o.y - 1, column: o.x - 1}))}
          selectedCell={
            selected
              ? {row: selected.y - 1, column: selected.x - 1}
              : undefined
          }
          onClick={onChangeField}
          cellShape={HcsControlGrid.Shapes.rect}
          gridShape={HcsControlGrid.Shapes.circle}
          gridRadius={wellRadius}
          flipVertical
          title={wellName}
          showLegend={false}
        />
      </div>
    );
  }
}

HcsImageFieldSelector.propTypes = {
  className: PropTypes.string,
  width: PropTypes.number,
  height: PropTypes.number,
  fields: PropTypes.array,
  selectedField: PropTypes.string,
  onChange: PropTypes.func,
  style: PropTypes.object,
  wellName: PropTypes.string,
  wellRadius: PropTypes.number
};

export default HcsImageFieldSelector;
