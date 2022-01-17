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
import Chart from 'chart.js';
import 'chart.js/dist/Chart.css';

class BaseChart extends React.Component {
  chart;
  ctx;

  componentWillReceiveProps (nextProps, nextContext) {
    if (this.ctx) {
      this.chartRef(this.ctx, nextProps);
    }
  }

  chartRef = (ctx, props) => {
    if (ctx) {
      this.ctx = ctx;
      const {
        data,
        options = {},
        type,
        plugins,
        units
      } = props || this.props;
      const {plugins: optPlugins = {}, ...rest} = options;
      if (this.chart) {
        this.chart.data = data;
        this.chart.options = {
          ...rest,
          plugins: optPlugins,
          elements: {
            line: {
              tension: 0,
              borderJoinStyle: 'round'
            }
          },
          scales: {
            yAxes: [{
              ticks: {
                beginAtZero: true
              }
            }]
          },
          tooltips: {
            intersect: true,
            mode: 'point',
            callbacks: {
              label: function (tooltipItem, data) {
                const {datasetIndex} = tooltipItem;
                const {label} = data.datasets[datasetIndex];
                let value = Number(tooltipItem.value);
                if (!isNaN(value)) {
                  const formatter = new Intl.NumberFormat('en-US', {
                    minimumFractionDigits: 0,
                    maximumFractionDigits: 2
                  });
                  value = formatter.format(value);
                }
                return `${label}: ${value}${units}`;
              }
            }
          },
          maintainAspectRatio: false
        };
        this.chart.update();
      } else {
        this.chart = new Chart(ctx, {
          type,
          data,
          options: {
            ...rest,
            responsive: true,
            elements: {
              line: {
                tension: 0,
                borderJoinStyle: 'round'
              }
            },
            scales: {
              yAxes: [{
                ticks: {
                  beginAtZero: true
                }
              }]
            },
            tooltips: {
              intersect: true,
              mode: 'point',
              callbacks: {
                label: function (tooltipItem, data) {
                  const {datasetIndex} = tooltipItem;
                  const {label} = data.datasets[datasetIndex];
                  let value = Number(tooltipItem.value);
                  if (!isNaN(value)) {
                    const formatter = new Intl.NumberFormat('en-US', {
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 2
                    });
                    value = formatter.format(value);
                  }
                  return `${label}: ${value}${units}`;
                }
              }
            },
            plugins: optPlugins
          },
          plugins: [...(plugins || [])]
        });
      }
      this.chart.resize();
    }
  };

  render () {
    return (
      <canvas
        ref={this.chartRef}
        style={{position: 'relative', width: '100%', height: '100%'}}
      />
    );
  }
}

BaseChart.PropTypes = {
  data: PropTypes.object,
  options: PropTypes.object,
  type: PropTypes.string,
  plugins: PropTypes.array,
  units: PropTypes.string
};

export default BaseChart;
