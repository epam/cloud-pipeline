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

export default class MemoryChart extends NodeChart {
  static title = 'Memory Usage';
  static noDataAvailableMessage = 'Memory usage is unavailable';
  static chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    title: {
      display: false
    },
    tooltips: {
      mode: 'index',
      intersect: false
    },
    hover: {
      mode: 'nearest',
      intersect: true
    },
    scales: {
      xAxes: [{
        display: true,
        gridLines: {
          display: true
        }
      }],
      yAxes: [{
        display: true,
        gridLines: {
          display: true
        },
        scaleLabel: {
          display: true,
          labelString: 'Megabytes used'
        },
        ticks: {
          beginAtZero: true,
          min: 0,
          maxTicksLimit: 5
        },
        id: 'usage'
      }, {
        display: true,
        gridLines: {
          display: false
        },
        scaleLabel: {
          display: true,
          labelString: '%'
        },
        ticks: {
          beginAtZero: false,
          maxTicksLimit: 5
        },
        id: 'usage-percentage',
        position: 'right'
      }]
    },
    legend: {
      display: true
    }
  };

  renderChart (entries) {
    if (this.chart) {
      const items = entries.filter(entry => entry.memoryUsage)
        .map(entry => {
          return {
            value: Math.round(entry.memoryUsage.usage / 1024 / 1024),
            label: this.getDate(entry),
            max: entry.memoryUsage.capacity / 1024 / 1024,
            percentage: Math.round(
              entry.memoryUsage.usage / entry.memoryUsage.capacity * 10000.0
            ) / 100.0
          };
        });

      if (items.length > 0) {
        let max, min;
        for (let i = 0; i < items.length; i++) {
          if (max === undefined || max < items[i].percentage) {
            max = items[i].percentage;
          }
          if (min === undefined || min > items[i].percentage) {
            min = items[i].percentage;
          }
        }
        const delta = max - min;
        max = Math.min(100, max + delta / 5.0);
        min = Math.max(0, min - delta / 5.0);
        this.chart.options.scales.yAxes[0].ticks.suggestedMax = items[0].max;
        this.chart.options.scales.yAxes[1].ticks.min = min;
        this.chart.options.scales.yAxes[1].ticks.max = max;
      }
      const data = {
        labels: items.map(i => i.label),
        datasets: [{
          type: 'line',
          label: 'MB used',
          fill: true,
          data: items.map(i => i.value),
          borderColor: '#2282BF',
          backgroundColor: 'rgba(34, 130, 191, 0.25)',
          yAxisID: 'usage'
        }, {
          type: 'line',
          label: '%',
          fill: true,
          data: items.map(i => i.percentage),
          borderColor: '#bf2b3e',
          backgroundColor: 'rgba(191, 43, 62, 0.25)',
          yAxisID: 'usage-percentage'
        }]
      };
      this.updateChartData(data);
    }
  };
}
