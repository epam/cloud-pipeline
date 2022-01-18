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
import {ChartClickPlugin} from './extensions';
import {colors, labelUtils} from './utils';

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

const extractDataSets = (rawData, filters) => {
  const format = filters.periodType === 'Day'
    ? 'HH:mm'
    : 'YYYY-MM-DD';
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
      data: extractDataSet(rawDataSet, labels, format)
    }))
  };
};

function OverallClusterChart ({
  title,
  style,
  showByPoolName,
  filters,
  onClick,
  rawData = {},
  units
}) {
  const dataConfiguration = extractDataSets(rawData, filters, showByPoolName);
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title
    },
    hover: {
      onHover: function (event) {
        const point = this.getDatasetAtEvent(event);
        event.target.style.cursor = point.length && onClick
          ? 'pointer'
          : 'default';
      }
    },
    plugins: {
      [ChartClickPlugin.id]: {
        handler: onClick ? key => onClick(key) : undefined
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
        plugins={[ChartClickPlugin.plugin]}
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
  units: PropTypes.string
};

export default OverallClusterChart;
