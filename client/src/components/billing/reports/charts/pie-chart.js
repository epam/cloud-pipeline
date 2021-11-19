/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import Chart from './base';
import Export from '../export';
import {
  ChartClickPlugin,
  PieChartDataLabelPlugin
} from './extensions';
import {discounts} from '../discounts';
import {costTickFormatter} from '../utilities';

function toValueFormat (value) {
  return Math.round((+value || 0) * 100.0) / 100.0;
}

function getValues (data, propertyName = 'value') {
  return data.map(({item}) => toValueFormat(item[propertyName]));
}

function filterData (data, dataSample = 'value') {
  const sortedData = Object.keys(data || {})
    .map((key) => ({name: key, item: data[key]}));
  sortedData
    .sort((a, b) => +b.item[dataSample] - (+a.item[dataSample]));
  return sortedData;
}

function PieChart (
  {
    request,
    discounts: discountsFn,
    data: rawData,
    dataSample = 'value',
    previousDataSample = 'previous',
    onSelect,
    title,
    style,
    valueFormatter = costTickFormatter,
    useImageConsumer = true,
    onImageDataReceived,
    reportThemes
  }
) {
  if (!request) {
    return null;
  }
  const loading = Array.isArray(request)
    ? (request.filter(r => r.loading).length > 0)
    : (request.pending && !request.loaded);
  const loaded = Array.isArray(request)
    ? (request.filter(r => !r.loaded).length === 0)
    : (request.loaded);
  const value = rawData ||
    (
      Array.isArray(request)
        ? (loaded ? request.map(r => r.value || {}) : {})
        : (loaded ? (request.value || {}) : {})
    );
  const data = discounts.applyGroupedDataDiscounts(
    value,
    discountsFn
  );
  const filteredData = filterData(data);
  const error = request.error;
  const groups = filteredData.map(d => d.name);
  const currentData = getValues(filteredData, dataSample);
  const dataColors = reportThemes.generateColors(groups.length, true, false);
  const dataHoverColors = reportThemes.generateColors(groups.length, true, true);
  const chartData = {
    labels: groups,
    datasets: [
      {
        data: currentData,
        borderWidth: 2,
        backgroundColor: dataColors,
        hoverBackgroundColor: dataHoverColors,
        borderColor: reportThemes.backgroundColor,
        hoverBorderColor: reportThemes.backgroundColor
      }
    ]
  };
  const options = {
    animation: {duration: 0},
    cutoutPercentage: 33,
    title: {
      display: !!title,
      text: title,
      fontColor: reportThemes.textColor
    },
    legend: {
      // display: false,
      position: 'right',
      labels: {
        fontColor: reportThemes.textColor
      }
    },
    tooltips: {
      callbacks: {
        label: function (tooltipItem, data) {
          const {index} = tooltipItem;
          const {data: values} = data.datasets[tooltipItem.datasetIndex];
          const total = values.reduce((r, c) => r + c, 0);
          const percentage = Math.round(values[index] / total * 10000.0) / 100.0;
          const value = `${valueFormatter(values[index])} (${percentage}%)`;
          const label = data.labels[index];
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
      [PieChartDataLabelPlugin.id]: {
        valueFormatter,
        textColor: reportThemes.textColor,
        background: reportThemes.backgroundColor
      },
      [ChartClickPlugin.id]: {
        pie: true,
        handler: onSelect ? index => onSelect({key: groups[index]}) : undefined
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
        type="doughnut"
        options={options}
        useChartImageGenerator={useImageConsumer}
        onImageDataReceived={onImageDataReceived}
        plugins={[PieChartDataLabelPlugin.plugin, ChartClickPlugin.plugin]}
      />
    </Container>
  );
}

export default inject('reportThemes')(observer(PieChart));
