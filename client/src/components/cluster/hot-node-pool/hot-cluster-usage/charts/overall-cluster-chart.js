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
import moment from 'moment-timezone';
import Chart from './base';
import {ChartClickPlugin, ChartHoverCursorPlugin} from './extensions';
import {labelUtils} from './utils';

const extractDataSet = (rawDataSet, labels = [], format) => {
  return labels.map(label => {
    const current = rawDataSet
      .find(data => moment(data.measureTime).format(format) === label);
    if (current) {
      return current.poolUsage / current.poolLimit * 100;
    }
    return undefined;
  });
};

const extractDataSets = (rawData, filters, colors, currentCluster) => {
  const format = filters.periodType === 'Day'
    ? 'HH:mm'
    : 'DD MMM';
  const labels = filters.periodType === 'Day'
    ? labelUtils.getDayHours()
    : labelUtils.getMonthDays(filters.period, format);
  const dataEntries = Object.entries(rawData);
  return {
    labels: labels,
    datasets: dataEntries.map(([label, rawDataSet], index) => ({
      fill: false,
      label,
      borderColor: colors[index],
      borderWidth: currentCluster === label
        ? 4 : 1.5,
      data: extractDataSet(rawDataSet, labels, format)
    }))
  };
};

function OverallClusterChart ({
  title,
  style,
  filters,
  onClick,
  rawData = {},
  units,
  colors,
  currentCluster
}) {
  const dataConfiguration = extractDataSets(
    rawData,
    filters,
    colors,
    currentCluster
  );
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title
    },
    plugins: {
      [ChartClickPlugin.id]: {
        handler: onClick ? key => onClick(key) : undefined
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
      />
    </div>
  );
}

OverallClusterChart.PropTypes = {
  rawData: PropTypes.shape({}),
  filters: PropTypes.shape({
    periodType: PropTypes.string,
    period: PropTypes.string
  }),
  onClick: PropTypes.func,
  title: PropTypes.string,
  units: PropTypes.string,
  colors: PropTypes.array,
  currentCluster: PropTypes.string
};

export default OverallClusterChart;
