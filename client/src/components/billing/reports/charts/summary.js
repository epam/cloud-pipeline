/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import Chart from './base';
import {PointDataLabelPlugin, VerticalLinePlugin} from './extensions';
import {colors, getColor} from './colors';
import {costTickFormatter} from '../utilities';
import moment from 'moment-timezone';

function extractDataSet (data, title, type, color, options = {}) {
  const {showPoints = true, currentDateIndex} = options;
  return {
    label: title,
    type,
    data: data,
    fill: false,
    borderColor: color,
    borderWidth: 2,
    pointRadius: data.map((e, index) => showPoints && index === currentDateIndex ? 2 : 0),
    pointBackgroundColor: color,
    cubicInterpolationMode: 'monotone'
  };
}

function parse (values, quota, highlightedDate = moment.utc()) {
  const data = (values || [])
    .map(d => ({
      date: moment(d.date),
      y: d.value || NaN,
      y2: d.previous || NaN,
      quota: quota
    }));
  let currentDateIndex;
  let currentDate;
  if (highlightedDate) {
    const year = highlightedDate.get('y');
    const month = highlightedDate.get('M');
    const day = highlightedDate.get('D');
    currentDate = moment({y: year, M: month, D: day}).toDate();
    const [highlighted] = data
      .filter(d =>
        d.date && d.date.get('y') === year && d.date.get('M') === month && d.date.get('D') === day
      );
    if (highlighted) {
      currentDateIndex = data.indexOf(highlighted);
    }
  }
  return {
    quota: data.map(d => ({x: d.date.toDate(), y: d.quota})),
    currentData: data.map(d => ({x: d.date.toDate(), y: d.y})),
    previousData: data.map(d => ({x: d.date.toDate(), y: d.y2})),
    currentDate,
    currentDateIndex
  };
}

function Summary ({colors: colorsConfig, data, title, quota: quotaValue, style}) {
  const {currentData, previousData, quota, currentDate, currentDateIndex} = parse(data, quotaValue);
  const dataConfiguration = {
    datasets: [
      extractDataSet(
        currentData,
        'Current period',
        'summary-current',
        getColor(colorsConfig, 'current') || colors.current,
        {currentDateIndex}
      ),
      extractDataSet(
        previousData,
        'Previous period',
        'summary-previous',
        getColor(colorsConfig, 'previous') || colors.previous,
        {currentDateIndex}
      ),
      quotaValue ? extractDataSet(
        quota,
        'Quota',
        'summary-quota',
        getColor(colorsConfig, 'quota') || colors.quota,
        {showPoints: false, currentDateIndex}
      ) : false
    ].filter(Boolean)
  };
  const options = {
    animation: {duration: 0},
    title: {
      display: !!title,
      text: title
    },
    scales: {
      xAxes: [{
        type: 'time',
        time: {tooltipFormat: 'DD MMM YYYY'},
        bounds: 'data',
        gridLines: {
          drawOnChartArea: false
        },
        tick: {
          display: true,
          source: 'auto'
        }
      }],
      yAxes: [{
        ticks: {
          callback: costTickFormatter
        }
      }]
    },
    legend: {
      position: 'right'
    },
    tooltips: {
      intersect: false,
      mode: 'nearest',
      axis: 'x',
      callbacks: {
        label: function (tooltipItem, data) {
          const {label} = data.datasets[tooltipItem.datasetIndex];
          const value = costTickFormatter(tooltipItem.yLabel);
          if (label) {
            return `${label}: ${value}`;
          }
          return value;
        }
      }
    },
    plugins: {
      [VerticalLinePlugin.id]: {
        index: currentDateIndex,
        time: currentDate
      },
      [PointDataLabelPlugin.id]: [
        {datasetIndex: 0, index: currentDateIndex},
        {datasetIndex: 1, index: currentDateIndex},
        quotaValue ? {datasetIndex: 2, index: currentDateIndex} : false
      ].filter(Boolean)
    }
  };
  return (
    <div style={Object.assign({height: '100%', position: 'relative', display: 'block'}, style)}>
      <Chart
        data={dataConfiguration}
        type="summary"
        options={options}
        plugins={[
          PointDataLabelPlugin.plugin,
          VerticalLinePlugin.plugin
        ]}
      />
    </div>
  );
}

export default observer(Summary);
