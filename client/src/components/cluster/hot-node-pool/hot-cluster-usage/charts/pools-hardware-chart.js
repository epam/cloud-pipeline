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

function PoolsHardwareChart ({
  mappings,
  title,
  units,
  style,
  rawData = [],
  colors,
  textColor,
  backgroundColor,
  lineColor,
  limitColor,
  period,
  periodType
}) {
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title,
      fontColor: textColor
    },
    scales: {
      xAxes: [{
        id: 'x-axis',
        stacked: true,
        gridLines: {
          display: false
        },
        ticks: {
          fontColor: textColor
        }
      }],
      yAxes: [{
        stacked: true,
        ticks: {
          beginAtZero: true,
          fontColor: textColor
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
    }
  };
  const data = extractHardwareData(
    rawData,
    mappings,
    colors,
    lineColor,
    limitColor,
    backgroundColor
  );
  return (
    <div
      style={
        Object.assign(
          {
            width: '50%',
            height: '450px',
            position: 'relative',
            display: 'block'
          },
          style
        )
      }
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
    </div>
  );
}

PoolsHardwareChart.PropTypes = {
  rawData: PropTypes.array,
  title: PropTypes.string,
  mappings: PropTypes.arrayOf(PropTypes.shape({
    title: PropTypes.string,
    key: PropTypes.string,
    type: PropTypes.string
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
