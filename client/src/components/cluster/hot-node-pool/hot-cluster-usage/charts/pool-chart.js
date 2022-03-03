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
import PoolSelector from '../controls/pool-selector';
import styles from './cluster-chart.css';
import {getFadedColor} from './utils/colors';

const extractDataSets = (
  rawData,
  currentPoolId,
  colorOptions
) => {
  const clusterData = rawData.find(item => Number(item.poolId) === Number(currentPoolId)) || {};
  const {records = []} = clusterData;
  const labels = records.map(o => ({
    label: o.measureTime,
    display: o.displayTick,
    tooltip: o.tooltip
  }));
  const poolLimitDataSet = records.map((dataItem, index) => ({
    x: index,
    y: dataItem.poolLimit
  }));
  const poolUsageDataSet = records.map((dataItem, index) => ({
    x: index,
    y: dataItem.poolUsage
  }));
  const datasets = [
    {
      fill: true,
      backgroundColor: getFadedColor(colorOptions.usage),
      label: 'Pool Usage',
      borderColor: colorOptions.usage,
      data: poolUsageDataSet
    },
    {
      fill: false,
      backgroundColor: undefined,
      label: 'Pool Limit',
      borderColor: colorOptions.limit,
      data: poolLimitDataSet
    }
  ];
  return {
    labels,
    datasets
  };
};

function PoolChart ({
  title,
  style,
  units,
  rawData = {},
  currentPoolId,
  pools = [],
  onCurrentPoolChange,
  containerStyle,
  description,
  colorOptions,
  displayEmptyTitleRow,
  backgroundColor,
  lineColor,
  textColor,
  period,
  periodType
}) {
  const dataConfiguration = extractDataSets(
    rawData,
    currentPoolId,
    colorOptions
  );
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title || displayEmptyTitleRow,
      text: displayEmptyTitleRow ? '' : title,
      fontColor: textColor
    },
    legend: {
      labels: {
        fontColor: textColor
      }
    }
  };
  return (
    <div
      className={styles.container}
      style={containerStyle}
    >
      <div
        style={
          Object.assign(
            {width: '100%', flexGrow: '1', position: 'relative'},
            style
          )
        }
      >
        <Chart
          data={dataConfiguration}
          options={options}
          type="line"
          units={units}
          backgroundColor={backgroundColor}
          lineColor={lineColor}
          textColor={textColor}
          period={period}
          periodType={periodType}
        />
        <PoolSelector
          value={currentPoolId}
          pools={pools}
          onChange={onCurrentPoolChange}
          description={description}
        />
      </div>
    </div>
  );
}

PoolChart.PropTypes = {
  rawData: PropTypes.array,
  periodType: PropTypes.string,
  period: PropTypes.string,
  currentPoolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pools: PropTypes.array,
  onCurrentPoolChange: PropTypes.func,
  title: PropTypes.string,
  units: PropTypes.string,
  containerStyle: PropTypes.object,
  style: PropTypes.object,
  colorOptions: PropTypes.object,
  displayEmptyTitleRow: PropTypes.bool,
  backgroundColor: PropTypes.string,
  lineColor: PropTypes.string,
  textColor: PropTypes.string
};

export default PoolChart;
