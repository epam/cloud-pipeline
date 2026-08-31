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
import {getPeriod, Period} from '../../../../special/periods';

class BaseChart extends React.Component {
  chart;
  ctx;

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.ctx) {
      this.chartRef(this.ctx, this.props);
    }
  }

  chartRef = (ctx, props) => {
    if (ctx) {
      const {
        lineColor,
        textColor,
        period,
        periodType
      } = this.props;
      this.ctx = ctx;
      const {
        data,
        options = {},
        type,
        plugins,
        units
      } = props || this.props;
      const {plugins: optPlugins = {}, tooltips = {}, skipHiddenXPoints = false, ...rest} = options;
      const format = periodType === Period.day ? 'D MMMM, YYYY' : 'MMMM YYYY';
      const {start} = getPeriod(periodType, period);
      const xAxisLabel = start.format(format);
      const chartOptions = {
        plugins: optPlugins,
        elements: {
          line: {
            tension: 0,
            borderJoinStyle: 'round'
          },
          point: {
            borderWidth: 1,
            radius: 2
          }
        },
        scales: {
          yAxes: [{
            ticks: {
              beginAtZero: true,
              fontColor: textColor
            },
            gridLines: {
              color: lineColor,
              zeroLineColor: lineColor
            }
          }],
          xAxes: [{
            gridLines: {
              display: false,
              zeroLineColor: lineColor
            },
            ticks: {
              fontColor: textColor,
              callback: function (value) {
                const {display, label} = value;
                if (display) {
                  return label;
                }
                return null;
              }
            },
            scaleLabel: {
              display: true,
              labelString: xAxisLabel,
              fontColor: textColor
            }
          }]
        },
        tooltips: {
          intersect: true,
          mode: 'point',
          filter: function (tooltipItem) {
            const {xLabel} = tooltipItem;
            const {display = true} = xLabel || {};
            return !skipHiddenXPoints || display;
          },
          callbacks: {
            title: function (tooltipItems = []) {
              const {xLabel} = tooltipItems[0] || {};
              const {tooltip} = xLabel || {};
              if (tooltip) {
                return tooltip;
              }
              return null;
            },
            label: function (tooltipItem, data) {
              const {
                datasetIndex,
                value: tooltipItemValue,
                index: dataIndex,
                xLabel
              } = tooltipItem || {};
              const {
                display: displayXLabel = true
              } = xLabel || {};
              const {label} = data.datasets[datasetIndex];
              let value = Number(tooltipItemValue);
              const formatter = new Intl.NumberFormat('en-US', {
                minimumFractionDigits: 0,
                maximumFractionDigits: 2
              });
              if (!Number.isNaN(value)) {
                value = formatter.format(value);
              } else if (!displayXLabel) {
                const {
                  data: items = []
                } = data.datasets[datasetIndex] || {};
                const prev = items[dataIndex - 1];
                value = prev !== undefined && !Number.isNaN(Number(prev))
                  ? formatter.format(Number(prev))
                  : undefined;
              } else {
                value = undefined;
              }
              if (value) {
                return `${label}: ${value}${units}`;
              }
              return `${label}: -`;
            }
          },
          ...tooltips
        },
        maintainAspectRatio: false,
        ...rest
      };
      if (this.chart) {
        this.chart.data = data;
        this.chart.options = chartOptions;
        this.chart.update();
      } else {
        this.chart = new Chart(ctx, {
          type,
          data,
          options: chartOptions,
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
        style={{
          position: 'relative',
          width: '100%',
          height: '100%'
        }}
      />
    );
  }
}

BaseChart.PropTypes = {
  data: PropTypes.object,
  options: PropTypes.object,
  type: PropTypes.string,
  plugins: PropTypes.array,
  units: PropTypes.string,
  backgroundColor: PropTypes.string,
  lineColor: PropTypes.string,
  textColor: PropTypes.string,
  period: PropTypes.string,
  periodType: PropTypes.string
};

export default BaseChart;
