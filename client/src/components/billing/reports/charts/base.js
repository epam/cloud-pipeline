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
import Export from '../export';
import {DataLabelPlugin, GenerateImagePlugin} from './extensions';
import 'chart.js/dist/Chart.css';

class ChartWrapper extends React.Component {
  static propTypes = {
    data: PropTypes.object,
    error: PropTypes.string,
    loading: PropTypes.bool,
    type: PropTypes.string,
    options: PropTypes.object,
    plugins: PropTypes.array,
    useChartImageGenerator: PropTypes.bool,
    onImageDataReceived: PropTypes.func
  };

  static defaultProps = {
    useChartImageGenerator: true
  };

  chart;
  ctx;

  _graphImage;
  _graphImageError;

  componentDidMount () {
    const {useChartImageGenerator, imageGenerator} = this.props;
    if (useChartImageGenerator && imageGenerator) {
      imageGenerator.registerGenerator(this.generateImage);
    }
  }

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
        error,
        loading,
        options = {},
        type,
        plugins,
        onImageDataReceived
      } = props || this.props;
      const {plugins: optPlugins = {}, ...rest} = options;
      optPlugins[DataLabelPlugin.id] = {
        error,
        label: loading ? 'Loading...' : undefined
      };
      optPlugins[GenerateImagePlugin.id] = {
        onImageReady: (data) => {
          this._graphImage = data;
          if (onImageDataReceived) {
            onImageDataReceived(data);
          }
        },
        onImageError: (error) => {
          this._graphImageError = error;
          if (onImageDataReceived) {
            onImageDataReceived(null);
          }
        }
      };
      if (this.chart) {
        this.chart.data = data;
        this.chart.options = {
          ...rest,
          plugins: optPlugins,
          maintainAspectRatio: false
        };
        this.chart.update();
      } else {
        this.chart = new Chart(ctx, {
          type,
          data,
          options: {
            ...rest,
            plugins: optPlugins,
            maintainAspectRatio: false
          },
          plugins: [...(plugins || []), DataLabelPlugin.plugin, GenerateImagePlugin.plugin]
        });
      }
      this.chart.resize();
    }
  };

  generateImage = () => {
    if (this.chart) {
      if (this._graphImage) {
        return Promise.resolve(this._graphImage);
      } else if (this._graphImageError) {
        return Promise.reject(this._graphImageError);
      }
    }
    return Promise.resolve(null);
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

export default Export.ImageConsumer.Generator(ChartWrapper);
