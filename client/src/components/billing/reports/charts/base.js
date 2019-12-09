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
import PropTypes from 'prop-types';
import Chart from 'chart.js';
import * as NoDataLabelPlugin from './extensions/no-data-label-plugin';
import 'chart.js/dist/Chart.css';
import './extensions';

class ChartWrapper extends React.Component {
  static propTypes = {
    data: PropTypes.object,
    type: PropTypes.string,
    options: PropTypes.object,
    plugins: PropTypes.array
  };
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
      const {data, options, type, plugins} = props || this.props;
      if (this.chart) {
        this.chart.data = data;
        this.chart.options = {...options, maintainAspectRatio: false};
        this.chart.update();
      } else {
        this.chart = new Chart(ctx, {
          type,
          data,
          options: {...options, maintainAspectRatio: false},
          plugins: [...plugins, NoDataLabelPlugin.plugin]
        });
      }
      this.chart.resize();
    }
  };

  handleClick = (e) => {
    const {getBarAndNavigate} = this.props;
    const {options} = this.chart;
    const activePoints = this.chart.getElementsAtEventForMode(e, 'nearest', options);
    if (activePoints && activePoints.length) {
      const currentChart = activePoints[0];
      const {_datasetIndex, _index, _chart} = currentChart;
      const {data, options} = _chart.config;
      const title = options.title.text;
      const chartId = _chart.id;
      const label = data.labels && data.labels.length
        ? data.labels[_index]
        : null;
      const values = data.datasets.map((val) => val.data[_index]);
      const currentBar = {
        chartId,
        datasetId: _datasetIndex,
        barId: _index,
        label,
        values,
        title
      };
      getBarAndNavigate && getBarAndNavigate(currentBar);
    }
  };

  render () {
    return (
      <canvas
        ref={this.chartRef}
        style={{position: 'relative', width: '100%', height: '100%'}}
        onClick={this.handleClick}
      />
    );
  }
}

export default ChartWrapper;
