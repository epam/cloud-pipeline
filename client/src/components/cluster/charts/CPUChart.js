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

export default class CPUChart extends NodeChart {

  static title = 'CPU Usage';
  static noDataAvailableMessage = 'CPU usage is unavailable';
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
          labelString: 'Cores'
        },
        ticks: {
          min: 0,
          stepSize: 1,
          maxTicksLimit: 10
        }
      }]
    },
    legend: {
      display: false
    }
  };

  renderChart (entries) {
    if (this.chart && entries) {
      const items = entries.filter(entry => entry.cpuUsage && entry.cpuUsage.load)
        .map(entry => {
          return {
            value: Math.round(entry.cpuUsage.load * 100.0) / 100.0,
            label: this.getDate(entry)
          };
        });
      const data = {
        labels: items.map(i => i.label),
        datasets: [{
          type: 'line',
          label: 'CPU',
          fill: true,
          data: items.map(i => i.value),
          borderColor: '#2282BF',
          backgroundColor: 'rgba(34, 130, 191, 0.25)'
        }]
      };
      this.updateChartData(data);
    }
  };

}
