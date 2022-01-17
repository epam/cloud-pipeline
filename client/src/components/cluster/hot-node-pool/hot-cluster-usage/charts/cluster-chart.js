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
import {colors, labelUtils} from './utils';

const extractDataSet = (rawDataSet, labels = [], dataEntry, format) => {
  return labels.map(label => {
    const current = rawDataSet
      .find(data => moment(data.measureTime).format(format) === label);
    if (current) {
      return current[dataEntry];
    }
    return undefined;
  });
};

const extractDataSets = (rawData, filters, currentCluster) => {
  const format = filters.periodType === 'Day' ? 'HH:mm' : 'YYYY-MM-DD';
  const labels = filters.periodType === 'Day'
    ? labelUtils.getDayHours()
    : labelUtils.getMonthDays(filters.period, format);
  const data = rawData[currentCluster].reduce((acc, cur) => {
    acc['poolLimit'].push({
      poolLimit: cur.poolLimit,
      measureTime: cur.measureTime
    });
    acc['poolUsage'].push({
      poolUsage: cur.poolUsage,
      measureTime: cur.measureTime
    });
    return acc;
  }, {
    'poolLimit': [],
    'poolUsage': []
  });
  return {
    labels,
    datasets: Object.entries(data).map(([label, rawDataSet], index) => ({
      fill: false,
      label,
      borderColor: colors[index],
      data: extractDataSet(rawDataSet, labels, label, format)
    }))
  };
};

function ClusterChart ({
  title,
  style,
  units,
  filters,
  rawData = {},
  currentCluster
}) {
  const dataConfiguration = extractDataSets(
    rawData,
    filters,
    currentCluster
  );
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title
    }
  };
  return (
    <div
      style={
        Object.assign(
          {width: '50%', height: '450px', position: 'relative', display: 'block'},
          style
        )
      }
    >
      <Chart
        data={dataConfiguration}
        options={options}
        type="line"
        units={units}
      />
    </div>
  );
}

ClusterChart.PropTypes = {
  rawData: PropTypes.shape({}),
  filters: PropTypes.shape({
    periodType: PropTypes.string,
    period: PropTypes.string
  }),
  currentCluster: PropTypes.string,
  title: PropTypes.string,
  units: PropTypes.string
};

export default ClusterChart;
