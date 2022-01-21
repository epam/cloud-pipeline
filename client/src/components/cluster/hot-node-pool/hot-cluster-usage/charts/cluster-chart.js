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
import PoolSelector from '../controls/pool-selector';
import {labelUtils} from './utils';
import styles from './cluster-chart.css';

const LABEL_STRING = {
  poolLimit: 'Pool limit',
  poolUsage: 'Pool usage'
};

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

const extractDataSets = (rawData, filters, currentCluster, colorOptions) => {
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
    datasets: Object.entries(data).map(([datasetLabel, rawDataSet], index) => ({
      fill: false,
      label: LABEL_STRING[datasetLabel],
      borderColor: datasetLabel === 'poolLimit'
        ? colorOptions.limit
        : colorOptions.usage,
      data: extractDataSet(rawDataSet, labels, datasetLabel, format)
    }))
  };
};

function ClusterChart ({
  title,
  style,
  units,
  filters,
  rawData = {},
  currentCluster,
  clusterNames,
  onCurrentClusterChange,
  containerStyle,
  description,
  colorOptions,
  displayEmptyTitleRow
}) {
  const dataConfiguration = extractDataSets(
    rawData,
    filters,
    currentCluster,
    colorOptions
  );
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title || displayEmptyTitleRow,
      text: displayEmptyTitleRow ? '' : title
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
        />
        <PoolSelector
          value={currentCluster}
          clusterNames={clusterNames}
          onChange={onCurrentClusterChange}
          description={description}
        />
      </div>
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
  clusterNames: PropTypes.arrayOf(PropTypes.string),
  onCurrentClusterChange: PropTypes.func,
  title: PropTypes.string,
  units: PropTypes.string,
  containerStyle: PropTypes.object,
  style: PropTypes.object,
  colorOptions: PropTypes.object,
  displayEmptyTitleRow: PropTypes.bool
};

export default ClusterChart;
