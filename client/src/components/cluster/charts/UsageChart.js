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

import NodeChart from './NodeChart';
import Chart from 'chart.js';

export default class UsageChart extends NodeChart {

  static chartType = 'horizontalBar';
  static plugins = [{
    afterDatasetsDraw: function (chart) {
      const ctx = chart.ctx;
      chart.data.datasets.forEach(function (dataset, i) {
        if (!dataset.dataLabel || !dataset.drawDataLabel) {
          return;
        }
        const meta = chart.getDatasetMeta(i);
        if (!meta.hidden) {
          meta.data.forEach((element, index) => {
            ctx.fillStyle = 'rgb(0, 0, 0)';
            const fontSize = 12;
            const fontStyle = 'normal';
            const fontFamily = 'sans-serif';
            ctx.font = Chart.helpers.fontString(fontSize, fontStyle, fontFamily);
            const dataString = dataset.dataLabel[index].toString();
            ctx.textAlign = 'right';
            ctx.textBaseline = 'middle';
            const padding = 5;
            const y = element.tooltipPosition().y;
            ctx.fillText(
              dataString,
              chart.chartArea.right - padding, y);
          });
        }
      });
    }
  }];

  static colors = [
    {r: 34, g: 130, b: 191},
    {r: 191, g: 43, b: 62},
    {r: 253, g: 145, b: 53},
    {r: 50, g: 206, b: 205},
    {r: 129, g: 76, b: 251},
    {r: 253, g: 193, b: 68}
  ];

  static noDataAvailableMessage = 'Usage is unavailable';
  static title = 'Usage';
  static chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    title: {
      display: false
    },
    tooltips: {
      enabled: false
    },
    hover: {
      mode: 'nearest',
      intersect: true
    },
    scales: {
      xAxes: [{
        display: false,
        stacked: true,
        ticks: {
          beginAtZero: true,
          min: 0,
          max: 100,
          maxTicksLimit: 10
        },
        gridLines: {
          display: false
        }
      }],
      yAxes: [{
        display: true,
        stacked: true,
        gridLines: {
          display: false
        },
        scaleLabel: {
          display: false
        },
        maxBarThickness: 30
      }]
    },
    legend: {
      display: false
    }
  };

  getColorRepresentation = (index, alpha) => {
    const indexCorrected = index % this.constructor.colors.length;
    const color = this.constructor.colors[indexCorrected];
    return `rgba(${color.r}, ${color.g}, ${color.b}, ${alpha})`;
  };

  static postfixes = ['',
    'KiB',
    'MiB',
    'GiB',
    'TiB',
    'PiB',
    'EiB'
  ];

  getValueRepresentation = (value) => {
    let index = 0;
    while (value > 1024 && index < this.constructor.postfixes.length - 1) {
      value /= 1024;
      index += 1;
    }
    return `${value.toFixed(2)} ${this.constructor.postfixes[index]}`;
  };

  getUsageDescription = (usage, capacity, percentage) => {
    const usageDescription = this.getValueRepresentation(usage);
    const capacityDescription = this.getValueRepresentation(capacity);
    return `${usageDescription} of ${capacityDescription} (${percentage.toFixed(2)}%)`;
  };

  updateChartOptions () {};

  renderUsages (usages) {
    if (usages && this.chart) {
      this.updateChartOptions();
      const getColor = this.getColorRepresentation;
      const {labels, keys, entryData} = usages;
      const data = {
        labels: labels,
        datasets: [{
          label: 'Usage',
          data: keys.map(key => entryData[key].value),
          dataLabel: keys.map(key => entryData[key].label),
          drawDataLabel: true,
          borderColor: keys.map((k, i) => getColor(i, 1)),
          backgroundColor: keys.map((k, i) => getColor(i, 0.25)),
          borderWidth: 1
        }, {
          label: 'Free',
          data: keys.map(key => entryData[key].freeValue),
          borderColor: '#ddd',
          backgroundColor: '#f1f1f1',
          borderWidth: 1
        }]
      };
      this.updateChartData(data);
    }
  };

  transformEntries (entries) {
    return undefined;
  };

  renderChart (entries) {
    if (entries && this.chart) {
      this.renderUsages(this.transformEntries(entries));
    }
  };
}
