/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import {extractHardwareData} from './utils/hardware-chart-utils';
import PoolSelector from '../controls/pool-selector';
import {getPeriod, Period} from '../../../../special/periods';

function PoolsHardwareChart ({
  mappings,
  title,
  currentPoolId,
  onCurrentPoolChange,
  pools,
  units,
  style,
  className,
  rawData = [],
  colors,
  textColor,
  backgroundColor,
  lineColor,
  period,
  periodType,
  showPoolSelector = false
}) {
  const data = extractHardwareData(
    rawData,
    mappings,
    colors,
    lineColor
  );
  const format = periodType === Period.day ? 'D MMMM, YYYY' : 'MMMM YYYY';
  const {start} = getPeriod(periodType, period);
  const xAxisLabel = start.format(format);
  const {
    max
  } = data || {};
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title || showPoolSelector,
      text: showPoolSelector ? '' : title,
      fontColor: textColor
    },
    scales: {
      xAxes: [{
        id: 'x-axis',
        stacked: true,
        gridLines: {
          display: false,
          zeroLineColor: lineColor
        },
        ticks: {
          fontColor: textColor,
          callback: function (value) {
            const {display, label} = value;
            if (display) {
              return label;
            }
            return null;
          }
        },
        scaleLabel: {
          display: true,
          labelString: xAxisLabel,
          fontColor: textColor
        }
      }],
      yAxes: [{
        stacked: true,
        ticks: {
          beginAtZero: true,
          fontColor: textColor,
          stepSize: max < 5 ? 1 : undefined
        },
        gridLines: {
          color: lineColor
        }
      }]
    },
    legend: {
      labels: {
        fontColor: textColor
      }
    },
    tooltips: {
      mode: 'index',
      intersect: false
    }
  };
  return (
    <div
      style={
        Object.assign(
          {
            position: 'relative',
            display: 'block'
          },
          style
        )
      }
      className={className}
    >
      <Chart
        data={data}
        options={options}
        type="bar"
        units={units}
        period={period}
        periodType={periodType}
        backgroundColor={backgroundColor}
        lineColor={lineColor}
        textColor={textColor}
      />
      {showPoolSelector && (
        <PoolSelector
          value={currentPoolId}
          pools={pools}
          onChange={onCurrentPoolChange}
          description={(<b>{title}</b>)}
          showPoolDescription
        />
      )}
    </div>
  );
}

PoolsHardwareChart.PropTypes = {
  rawData: PropTypes.array,
  title: PropTypes.string,
  mappings: PropTypes.arrayOf(PropTypes.shape({
    title: PropTypes.string,
    key: PropTypes.string,
    type: PropTypes.string,
    valueFormatter: PropTypes.func
  })),
  units: PropTypes.string,
  colors: PropTypes.array,
  backgroundColor: PropTypes.string,
  lineColor: PropTypes.string,
  limitColor: PropTypes.string,
  textColor: PropTypes.string,
  period: PropTypes.string,
  periodType: PropTypes.string
};

export default PoolsHardwareChart;
