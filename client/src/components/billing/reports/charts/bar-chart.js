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
import {colors} from './colors';
import {BarchartDataLabelPlugin, ScaleTitleClickPlugin} from './extensions';
import {costTickFormatter} from '../utilities';
import {Alert} from 'antd';

import styles from './charts.css';

function toValueFormat (value) {
  return Math.round((+value || 0) * 100.0) / 100.0;
}

function getValues (data, propertyName = 'value') {
  return data.map(({item}) => toValueFormat(item[propertyName]));
}

function getMaximum (...values) {
  const trueMaximum = Math.max(...values.filter(v => !isNaN(v)), 0);
  const extended = trueMaximum * 1.2; // + 20%
  const step = trueMaximum / 10.0;
  const basis = 10 ** Math.floor(Math.log10(step));
  return Math.ceil(extended / basis) * basis;
}

function filterTopData (data, top) {
  const sortedData = Object.keys(data || {})
    .map((key) => ({name: key, item: data[key]}));
  sortedData
    .sort((a, b) => +b.item.value - (+a.item.value));
  if (top) {
    return sortedData.filter((o, i) => i < top);
  }
  return sortedData;
}

function BarChart (
  {
    axisPosition = 'left',
    data,
    error = null,
    dataSample = 'value',
    previousDataSample = 'previous',
    onSelect,
    title,
    style,
    subChart,
    top = 10,
    getBarAndNavigate,
    valueFormatter = costTickFormatter
  }
) {
  if (error) {
    return (
      <div style={Object.assign({height: '100%', position: 'relative', display: 'block'}, style)}>
        {!subChart && !!title && <div className={styles.title}>{title}</div>}
        <Alert type="error" message={error} />
      </div>
    );
  }
  const filteredData = filterTopData(data, top);
  const groups = filteredData.map(d => d.name);
  const previousData = getValues(filteredData, previousDataSample);
  const currentData = getValues(filteredData, dataSample);
  const maximum = getMaximum(
    ...previousData,
    ...currentData
  );
  const chartData = {
    labels: groups,
    datasets: [
      {
        label: 'Previous',
        type: 'quota-bar',
        data: previousData,
        borderWidth: 2,
        borderDash: [4, 4],
        borderColor: colors.blue,
        backgroundColor: colors.blue,
        borderSkipped: '',
        textColor: colors.blue,
        textBold: false,
        showDataLabels: false
      },
      {
        label: 'Current',
        data: currentData,
        borderWidth: 1,
        borderColor: colors.current,
        backgroundColor: colors.lightCurrent,
        borderSkipped: '',
        textColor: colors.darkCurrent,
        textBold: false
      }
    ]
  };
  const options = {
    animation: {duration: 0},
    scales: {
      xAxes: [{
        id: 'x-axis',
        gridLines: {
          drawOnChartArea: false
        },
        scaleLabel: {
          display: subChart,
          labelString: title
        }
      }],
      yAxes: [{
        position: axisPosition,
        ticks: {
          beginAtZero: true,
          callback: value => {
            if (value === maximum) {
              return '';
            }
            return valueFormatter(value);
          },
          max: maximum
        }
      }]
    },
    title: {
      display: !subChart && !!title,
      text: top ? `${title} (TOP ${top})` : title
    },
    legend: {
      display: false
    },
    tooltips: {
      intersect: false,
      mode: 'index',
      callbacks: {
        label: function (tooltipItem, data) {
          const {label} = data.datasets[tooltipItem.datasetIndex];
          const value = valueFormatter(tooltipItem.yLabel);
          if (label) {
            return `${label}: ${value}`;
          }
          return value;
        }
      }
    },
    hover: {
      onHover: function (e) {
        const point = this.getElementsAtXAxis(e);
        e.target.style.cursor = point.length && getBarAndNavigate
          ? 'pointer'
          : 'default';
      }
    },
    plugins: {
      [BarchartDataLabelPlugin.id]: {
        valueFormatter
      },
      [ScaleTitleClickPlugin.id]: {
        handler: () => {},
        axis: 'x-axis'
      }
    }
  };

  return (
    <div style={Object.assign({height: '100%', position: 'relative', display: 'block'}, style)}>
      <Chart
        data={chartData}
        type="bar"
        options={options}
        getBarAndNavigate={getBarAndNavigate}
        plugins={[
          BarchartDataLabelPlugin.plugin,
          ScaleTitleClickPlugin.plugin
        ]}
      />
    </div>
  );
}

export default observer(BarChart);
