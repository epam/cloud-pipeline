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

const LEGEND_ICON_WIDTH = 30;
const MARGIN = 15;

@inject('plot', 'themes')
@observer
class Legend extends React.Component {
  state = {
    legendSizes: {}
  };

  @computed
  get plotColors () {
    return getThemedPlotColors(this);
  }

  @computed
  get backgroundColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-background-color'] || 'white';
    }
    return 'white';
  }

  getTotalLegendsWidth (elements) {
    const {legendSizes} = this.state;
    return Object.keys(legendSizes)
      .filter(key => elements.indexOf(key) >= 0)
      .map(key => legendSizes[key].width + MARGIN)
      .reduce((total, current) => total + current, 0);
  }

  renderLegendItem = (y, width) => (plot, index, array) => {
    const {name: plotName, title} = plot;
    const name = title || plotName;
    const {fontSize} = this.props;
    const initializeTextElement = (text) => {
      if (text) {
        const {width, height} = text.getBBox();
        const {legendSizes} = this.state;
        if (legendSizes.hasOwnProperty(name)) {
          return;
        }
        legendSizes[name] = {
          width: width + LEGEND_ICON_WIDTH,
          height
        };
        this.setState({legendSizes});
      }
    };
    const color = this.plotColors[index % this.plotColors.length];
    const x = width / 2.0 -
      this.getTotalLegendsWidth(array.map(a => a.title || a.name)) / 2.0 +
      this.getTotalLegendsWidth(array.map(a => a.title || a.name).slice(0, index)) +
      MARGIN / 2.0;
    const {legendSizes} = this.state;
    const style = {fontSize};
    let height = 0;
    if (!legendSizes[name]) {
      style.opacity = 0;
    } else {
      height = legendSizes[name].height;
    }
    return (
      <g
        key={index}
        shapeRendering={'auto'}
      >
        <line
          x1={x + 5}
          y1={y}
          x2={x + LEGEND_ICON_WIDTH - 5}
          y2={y}
          stroke={color}
          fill={'none'}
          strokeWidth={2}
        />
        <circle
          cx={x + LEGEND_ICON_WIDTH / 2.0}
          cy={y}
          r={3}
          strokeWidth={2}
          fill={this.backgroundColor}
          stroke={color}
        />
        <text
          x={x + LEGEND_ICON_WIDTH}
          y={y + height / 4.0}
          textAnchor={'start'}
          stroke={'none'}
          fill={color}
          ref={initializeTextElement}
          style={style}
        >
          {name}
        </text>
      </g>
    );
  };

  render () {
    const {plot} = this.props;
    if (!plot) {
      return null;
    }
    const {plots} = plot.props;
    if (plots.length < 2) {
      return null;
    }
    const {chartArea, width} = plot.props;
    const top = chartArea.top;
    return (
      <g>
        {plots.map(this.renderLegendItem(top / 2.0, width))}
      </g>
    );
  }
}

Legend.propTypes = {
  fontSize: PropTypes.number
};

Legend.defaultProps = {
  fontSize: 12
};

export default Legend;
