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
import {
  BarchartDataLabelPlugin,
  ChartClickPlugin
} from './extensions';
import Export from '../export';
import {costTickFormatter} from '../utilities';

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

function filterTopData (data, top, dataSample = 'value') {
  const sortedData = Object.keys(data || {})
    .map((key) => ({name: key, item: data[key]}));
  sortedData
    .sort((a, b) => +b.item[dataSample] - (+a.item[dataSample]));
  if (top) {
    return sortedData.filter((o, i) => i < top);
  }
  return sortedData;
}

function BarChart (
  {
    axisPosition = 'left',
    request,
    data: rawData,
    dataSample = 'value',
    previousDataSample = 'previous',
    onSelect,
    onScaleSelect,
    title,
    style,
    subChart,
    top = 10,
    valueFormatter = costTickFormatter,
    useImageConsumer = true,
    onImageDataReceived
  }
) {
  if (!request) {
    return null;
  }
  const loading = request.pending && !request.loaded;
  const data = rawData || (request.loaded ? (request.value || {}) : {});
  const error = request.error;
  const filteredData = filterTopData(data, top, dataSample);
  const groups = filteredData.map(d => d.name);
  const previousData = getValues(filteredData, previousDataSample);
  const currentData = getValues(filteredData, dataSample);
  const maximum = getMaximum(
    ...previousData,
    ...currentData
  );
  const disabled = isNaN(maximum);
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
        showDataLabels: false,
        maxBarThickness: 70
      },
      {
        label: 'Current',
        data: currentData,
        borderWidth: 2,
        borderColor: colors.current,
        backgroundColor: colors.lightCurrent,
        borderSkipped: '',
        textColor: colors.darkCurrent,
        textBold: false,
        maxBarThickness: 70
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
          display: !disabled && subChart,
          labelString: title
        }
      }],
      yAxes: [{
        position: axisPosition,
        gridLines: {
          display: !disabled
        },
        ticks: {
          display: !disabled,
          beginAtZero: true,
          callback: value => {
            if (value === maximum) {
              return '';
            }
            return valueFormatter(value);
          },
          max: !disabled ? maximum : undefined
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
      itemSort: function ({datasetIndex: a}, {datasetIndex: b}) {
        // reverse tooltip orders
        return b - a;
      },
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
        e.target.style.cursor = point.length && onSelect
          ? 'pointer'
          : 'default';
      }
    },
    plugins: {
      [BarchartDataLabelPlugin.id]: {
        valueFormatter
      },
      [ChartClickPlugin.id]: {
        handler: onSelect ? index => onSelect({key: groups[index]}) : undefined,
        scaleHandler: onScaleSelect,
        axis: 'x-axis'
      }
    }
  };

  const Container = ({style: cssStyle, children}) => {
    if (useImageConsumer) {
      return (
        <Export.ImageConsumer
          style={cssStyle}
          order={2}
        >
          {children}
        </Export.ImageConsumer>
      );
    }
    return (
      <div style={cssStyle}>
        {children}
      </div>
    );
  };

  return (
    <Container
      style={
        Object.assign(
          {height: '100%', position: 'relative', display: 'block'},
          style
        )
      }
    >
      <Chart
        data={chartData}
        error={error}
        loading={loading}
        type="bar"
        options={options}
        plugins={[
          BarchartDataLabelPlugin.plugin,
          ChartClickPlugin.plugin
        ]}
        useChartImageGenerator={useImageConsumer}
        onImageDataReceived={onImageDataReceived}
      />
    </Container>
  );
}

export default observer(BarChart);
