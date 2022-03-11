/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import Chart from './base';
import {getColor, getFadedColor} from './utils/colors';
import {ChartClickPlugin, ChartHoverCursorPlugin} from './extensions';

const extractDataSets = (
  rawData,
  colors,
  currentPoolId,
  hiddenDatasets = []
) => {
  const dataEntries = rawData.map((entry, index) => ({
    ...entry,
    color: getColor(index, colors)
  }));
  const labels = ((dataEntries[0] || {}).records || [])
    .map(o => ({
      label: o.measureTime,
      display: o.displayTick,
      tooltip: o.tooltip
    }));
  const isCurrentPool = poolId => Number(currentPoolId) === Number(poolId);
  const isHidden = poolId => (hiddenDatasets || []).includes(Number(poolId));
  const datasets = dataEntries.map(({poolId, poolName, records, color}) => ({
    fill: isCurrentPool(poolId),
    backgroundColor: isCurrentPool(poolId)
      ? getFadedColor(color)
      : undefined,
    poolId,
    label: poolName,
    hidden: isHidden(poolId),
    borderColor: color,
    borderWidth: isCurrentPool(poolId)
      ? 4 : 1.5,
    data: records.map((o, index) => ({y: o.poolUtilization, x: index}))
  }));
  return {
    labels,
    datasets
  };
};

function OverallPoolChart ({
  title,
  style,
  onClick,
  rawData = [],
  units,
  colors,
  currentPoolId,
  onToggleDataset,
  hiddenDatasets,
  backgroundColor,
  lineColor,
  textColor,
  period,
  periodType
}) {
  const dataConfiguration = extractDataSets(
    rawData,
    colors,
    currentPoolId,
    hiddenDatasets
  );
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title,
      fontColor: textColor
    },
    legend: {
      labels: {
        fontColor: textColor
      },
      ...(onToggleDataset && {
        onClick: onToggleDataset
      })
    },
    plugins: {
      [ChartClickPlugin.id]: {
        handler: onClick ? poolId => onClick(poolId) : undefined
      },
      [ChartHoverCursorPlugin.id]: {
        handler: (event, isLineHovered) => {
          event.native.target.style.cursor = isLineHovered && onClick
            ? 'pointer'
            : 'default';
        }
      }
    }
  };
  return (
    <div
      style={
        Object.assign(
          {
            width: '50%',
            height: '450px',
            position: 'relative',
            display: 'block',
            marginRight: '5px'
          },
          style
        )
      }
    >
      <Chart
        data={dataConfiguration}
        options={options}
        type="line"
        plugins={[ChartClickPlugin.plugin, ChartHoverCursorPlugin.plugin]}
        units={units}
        backgroundColor={backgroundColor}
        lineColor={lineColor}
        textColor={textColor}
        period={period}
        periodType={periodType}
      />
    </div>
  );
}

OverallPoolChart.PropTypes = {
  rawData: PropTypes.array,
  onClick: PropTypes.func,
  title: PropTypes.string,
  units: PropTypes.string,
  colors: PropTypes.array,
  currentPoolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onToggleDataset: PropTypes.func,
  hiddenDatasets: PropTypes.arrayOf(PropTypes.string),
  backgroundColor: PropTypes.string,
  lineColor: PropTypes.string,
  textColor: PropTypes.string,
  period: PropTypes.string,
  periodType: PropTypes.string
};

export default OverallPoolChart;
