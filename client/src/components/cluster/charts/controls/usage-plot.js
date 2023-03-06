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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {getThemedPlotColors} from './utilities';

const maxHeight = 40;

@inject('themes')
@observer
class UsagePlot extends React.PureComponent {
  @computed
  get plotColors () {
    return getThemedPlotColors(this);
  }

  @computed
  get backgroundColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-header-background'] || '#ccc';
    }
    return '#ccc';
  }

  @computed
  get fontColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@application-color'] || 'rgba(0, 0, 0, 0.65)';
    }
    return 'rgba(0, 0, 0, 0.65)';
  }

  renderSingleUsageBar = (config, index, barHeight, totalCount) => {
    const {
      chartArea,
      data,
      fontSize,
      height,
      margin,
      width
    } = this.props;
    if (
      !chartArea ||
      !config ||
      !data ||
      !data.data ||
      !data.data[config.group]
    ) {
      return null;
    }
    const barData = data.data[config.group]?.data || [];
    if (!barData || !barData.length) {
      return null;
    }
    const dataItem = barData[barData.length - 1];
    const value = dataItem[config.value];
    const total = dataItem[config.total];
    const barWidth = width - chartArea.left - chartArea.right;
    const y = chartArea.top +
      (height - chartArea.top - chartArea.bottom) / 2.0 - (totalCount / 2.0 - index) * barHeight;
    const h = Math.min(maxHeight, barHeight) - 2 * margin;
    const color = this.plotColors[index % this.plotColors.length];
    let {formatter} = config;
    if (!formatter) {
      formatter = o => o !== undefined && o !== null ? o.toFixed(2) : '';
    }
    return (
      <g key={index}>
        <rect
          x={chartArea.left + value / total * barWidth}
          y={y + barHeight / 2.0 - h / 2.0 + margin}
          width={(1.0 - value / total) * barWidth}
          height={h}
          stroke={'none'}
          fill={this.backgroundColor}
          opacity={0.2}
        />
        <rect
          x={chartArea.left + value / total * barWidth}
          y={y + barHeight / 2.0 - h / 2.0 + margin}
          width={(1.0 - value / total) * barWidth}
          height={h}
          stroke={this.backgroundColor}
          strokeWidth={1}
          fill={'none'}
        />
        <rect
          x={chartArea.left}
          y={y + barHeight / 2.0 - h / 2.0 + margin}
          width={value / total * barWidth}
          height={h}
          stroke={'none'}
          fill={color}
          opacity={0.2}
        />
        <rect
          x={chartArea.left}
          y={y + barHeight / 2.0 - h / 2.0 + margin}
          width={value / total * barWidth}
          height={h}
          stroke={color}
          strokeWidth={1}
          fill={'none'}
        />
        {
          config.title && (
            <text
              stroke={'none'}
              fill={this.fontColor}
              x={chartArea.left + fontSize}
              y={y + barHeight / 2.0 + 3.0 * fontSize / 4.0}
              textAnchor={'start'}
              style={{fontSize}}
            >
              {config.title}
            </text>
          )
        }
        <text
          stroke={'none'}
          fill={this.fontColor}
          x={chartArea.left + barWidth - fontSize}
          y={y + barHeight / 2.0 + 3.0 * fontSize / 4.0}
          textAnchor={'end'}
          style={{fontSize}}
        >
          {formatter(value)} of {formatter(total)} ({(value / total * 100).toFixed(2)}%)
        </text>
      </g>
    );
  };

  render () {
    const {chartArea, data, config, height, width} = this.props;
    if (!data || !config || !config.length || !chartArea) {
      return null;
    }
    const barHeight = (height - chartArea.top - chartArea.bottom) / config.length;
    return (
      <svg
        width={width}
        height={height}
        shapeRendering={'crispEdges'}
      >
        {
          config.map(
            (c, index) =>
              this.renderSingleUsageBar(c, index, barHeight, config.length)
          )
        }
      </svg>
    );
  }
}

UsagePlot.propTypes = {
  config: PropTypes.arrayOf(PropTypes.shape({
    group: PropTypes.string,
    value: PropTypes.string,
    total: PropTypes.string,
    formatter: PropTypes.func
  })),
  chartArea: PropTypes.shape({
    left: PropTypes.number,
    right: PropTypes.number,
    top: PropTypes.number,
    bottom: PropTypes.number
  }),
  data: PropTypes.object,
  height: PropTypes.number,
  width: PropTypes.number,
  margin: PropTypes.number,
  fontSize: PropTypes.number
};

UsagePlot.defaultProps = {
  margin: 5,
  chartArea: {
    left: 10,
    top: 5,
    right: 10,
    bottom: 5
  },
  fontSize: 12
};

export default UsagePlot;
