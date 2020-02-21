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
import {SummaryChart, PointDataLabelPlugin, VerticalLinePlugin} from './extensions';
import {colors} from './colors';
import {costTickFormatter} from '../utilities';
import {getTickFormat, getCurrentDate} from '../periods';
import moment from 'moment-timezone';
import styles from './charts.css';
import {Alert} from 'antd';

function dataIsEmpty (data) {
  return !data || data.filter((d) => !isNaN(d)).length === 0;
}

function generateEmptySet (filters) {
  if (!filters) {
    return null;
  }
  const {
    start: initial,
    end
  } = filters;
  const emptySet = [];
  let start = moment(initial);
  let unit = 'day';
  if (getTickFormat(initial, end) === '1M') {
    unit = 'M';
  }
  while (start <= end) {
    emptySet.push({
      dateValue: moment(start),
      key: moment(start).format('YYYY-MM-DD')
    });
    start = start.add(1, unit);
  }
  return emptySet;
}

function fillSet (filters, data) {
  const set = generateEmptySet(filters);
  if (!set || !set.length || !data || !data.length) {
    return data;
  }
  for (let i = 0; i < data.length; i++) {
    const item = data[i];
    const key = item.dateValue.format('YYYY-MM-DD');
    const [index] = set
      .map(({key}, index) => ({key, index}))
      .filter(e => e.key === key)
      .map(e => e.index);
    if (index >= 0) {
      set.splice(index, 1, item);
    }
  }
  set.forEach(e => delete e.key);
  return set;
}

function generateLabels (data, filters = {}) {
  if (!data || !data.length) {
    return {labels: []};
  }
  const {
    start,
    end,
    endStrict
  } = filters;
  const currentDate = endStrict ? moment(endStrict) : getCurrentDate();
  let currentDateIndex = data.length - 1;
  const checkUnit = (test, unit) => test.get(unit) === currentDate.get(unit);
  const checkUnits = (test, ...units) =>
    units.map(u => checkUnit(test, u)).reduce((r, c) => r && c, true);
  let isCurrentDateFn = (test) => checkUnits(test, 'Y', 'M', 'D');
  let format = 'DD MMM';
  let fullFormat = 'DD MMM YYYY';
  let tooltipFormat = 'MMMM DD, YYYY';
  let previousDateFn = date => moment(date).add(-1, 'M');
  if (getTickFormat(start, end) === '1M') {
    format = 'MMM';
    fullFormat = 'MMM YYYY';
    tooltipFormat = 'MMMM YYYY';
    isCurrentDateFn = (test) => checkUnits(test, 'Y', 'M');
    previousDateFn = date => moment(date).add(-1, 'Y');
  }
  const labels = [];
  let year;
  for (let i = 0; i < data.length; i++) {
    const date = data[i].dateValue;
    if (isCurrentDateFn(date)) {
      currentDateIndex = i;
    }
    let label = date.format(format);
    if (!year) {
      year = date.get('y');
    } else if (year !== date.get('y')) {
      year = date.get('y');
      label = date.format(fullFormat);
    }
    if (labels.indexOf(label) >= 0) {
      label = false;
    }
    labels.push({
      text: label,
      date,
      tooltip: date.format(tooltipFormat),
      previousTooltip: previousDateFn(date).format(tooltipFormat)
    });
  }
  return {
    labels,
    currentDateIndex
  };
}

function extractDataSet (data, title, type, color, options = {}) {
  if (dataIsEmpty(data)) {
    return false;
  }
  const {
    showPoints = true,
    currentDateIndex,
    borderWidth = 2
  } = options;
  return {
    label: title,
    type,
    data,
    fill: false,
    borderColor: color,
    borderWidth,
    pointRadius: data.map((e, index) => showPoints && index === currentDateIndex ? 2 : 0),
    pointBackgroundColor: color,
    cubicInterpolationMode: 'monotone'
  };
}

function parse (values, quota) {
  const data = (values || [])
    .map(d => ({
      date: d.dateValue,
      value: d.value || NaN,
      previous: d.previous || NaN,
      quota: quota
    }));
  return {
    quota: data.map(d => d.quota),
    currentData: data.map(d => d.value),
    previousData: data.map(d => d.previous)
  };
}

function Summary (
  {
    title,
    style,
    summary,
    quota: showQuota = true
  }
) {
  const data = summary && summary.loaded
    ? fillSet(summary.filters, summary.value.values || [])
    : [];
  const quotaValue = showQuota && summary && summary.loaded
    ? summary.value.quota
    : undefined;
  const error = summary
    ? summary.error
    : undefined;
  if (error) {
    return (
      <div style={Object.assign({height: '100%', position: 'relative', display: 'block'}, style)}>
        {!!title && <div className={styles.title}>{title}</div>}
        <Alert type="error" message={error} />
      </div>
    );
  }
  const {labels, currentDateIndex} = generateLabels(data, summary?.filters);
  const {currentData, previousData, quota} = parse(data, quotaValue);
  const dataConfiguration = {
    labels: labels.map(l => l.text),
    datasets: [
      extractDataSet(
        currentData,
        'Current period',
        SummaryChart.current,
        colors.current,
        {currentDateIndex, borderWidth: 3}
      ),
      extractDataSet(
        previousData,
        'Previous period',
        SummaryChart.previous,
        colors.previous,
        {currentDateIndex}
      ),
      quotaValue ? extractDataSet(
        quota,
        'Quota',
        SummaryChart.quota,
        colors.quota,
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
        gridLines: {
          drawOnChartArea: false
        },
        ticks: {
          display: true,
          maxRotation: 45,
          callback: (date) => date || ''
        },
        offset: true
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
        title: function () {
          return undefined;
        },
        label: function (tooltipItem, data) {
          let {label, type} = data.datasets[tooltipItem.datasetIndex];
          const value = costTickFormatter(tooltipItem.yLabel);
          const {xLabel: defaultTitle, index} = tooltipItem;
          if (index >= 0 && index < labels.length) {
            const {tooltip, previousTooltip} = labels[index];
            if (type === SummaryChart.previous) {
              label = previousTooltip || defaultTitle;
            } else {
              label = tooltip || defaultTitle;
            }
          }
          if (label) {
            return `${label}: ${value}`;
          }
          return value;
        }
      }
    },
    plugins: {
      [VerticalLinePlugin.id]: {
        index: currentDateIndex
      },
      [PointDataLabelPlugin.id]: {
        index: currentDateIndex
      }
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
