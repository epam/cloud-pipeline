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
import {observer} from 'mobx-react';
import Chart from './base';
import Export from '../export';
import {generateColors} from './colors';
import {
  ChartClickPlugin,
  PieChartDataLabelPlugin
} from './extensions';
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
    data: rawData,
    dataSample = 'value',
    previousDataSample = 'previous',
    onSelect,
    title,
    style,
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
  const filteredData = filterData(data);
  const error = request.error;
  const groups = filteredData.map(d => d.name);
  const currentData = getValues(filteredData, dataSample);
  const dataColors = generateColors(groups.length, true, false);
  const dataHoverColors = generateColors(groups.length, true, true);
  const chartData = {
    labels: groups,
    datasets: [
      {
        data: currentData,
        borderWidth: 2,
        backgroundColor: dataColors,
        hoverBackgroundColor: dataHoverColors,
        borderColor: '#fff',
        hoverBorderColor: '#fff'
      }
    ]
  };
  const options = {
    animation: {duration: 0},
    cutoutPercentage: 33,
    title: {
      display: !!title,
      text: title
    },
    legend: {
      // display: false,
      position: 'right'
    },
    tooltips: {
      callbacks: {
        label: function (tooltipItem, data) {
          const {index} = tooltipItem;
          const {data: values} = data.datasets[tooltipItem.datasetIndex];
          const value = valueFormatter(values[index]);
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
        valueFormatter
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

export default observer(PieChart);
