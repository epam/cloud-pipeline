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
import moment from 'moment';
import {PlotColors} from './utilities';

const MARGIN = 5;

@inject('plotContext')
@observer
class Tooltip extends React.Component {
  state = {
    sizes: {}
  };

  @computed
  get tooltipFn () {
    const {plotContext} = this.props;
    if (plotContext) {
      const tooltips = plotContext.plots
        .filter(p => !!p.props.tooltip)
        .map(p => ({
          name: p.props.name || p.props.identifier,
          color: PlotColors[p.index % PlotColors.length],
          tooltipFn: p.props.tooltip
        }));
      if (!tooltips.length) {
        return null;
      }
      return [
        {
          color: '#333',
          tooltipFn: obj => moment.unix(obj.x).format('D MMM YYYY, HH:mm:ss')
        },
        ...tooltips
      ];
    }
    return null;
  }

  renderTooltip = (x) => {
    const tooltips = this.tooltipFn;
    const arrowMargin = 5;
    const {fontSize, plotContext} = this.props;
    const {sizes} = this.state;
    const {width: totalWidth, height: totalHeight} = tooltips
      .map(t => `${t.name || ''}-${x}`)
      .map(key => sizes[key] || {width: 0, height: 0})
      .reduce((result, current) => ({
        width: Math.max(result.width, current.width),
        height: result.height + current.height + MARGIN
      }), {width: 0, height: MARGIN});
    const direction = x > plotContext.width / 2.0 ? -1 : 1;
    const xShift = x > plotContext.width / 2.0 ? -totalWidth - arrowMargin : arrowMargin;
    const getHeightBefore = (index) => {
      return tooltips
        .map(t => `${t.name || ''}-${x}`)
        .map(key => sizes[key] || {width: 0, height: 0})
        .filter((t, i) => i < index)
        .reduce((result, current) => result + current.height + MARGIN, MARGIN);
    };
    const renderSingleTooltip = (tooltip, index) => {
      const key = `${tooltip.name || ''}-${x}`;
      const ref = (text) => {
        if (text && !this.state.sizes[key]) {
          const {width, height} = text.getBBox();
          const sizes = this.state;
          sizes[key] = {
            width: width + 2 * MARGIN,
            height: height
          };
          this.setState({sizes});
        }
      };
      const size = this.state.sizes[key];
      const style = {
        stroke: 'none',
        fill: tooltip.color,
        fontSize,
        fontWeight: 'bold'
      };
      if (!size) {
        style.opacity = 0;
      }
      let textX = x + xShift + MARGIN;
      let textY = plotContext.height / 2.0 -
        totalHeight / 2.0 +
        getHeightBefore(index);
      if (size) {
        textY += (size.height / 2.0);
      }
      return (
        <text
          key={key}
          ref={ref}
          x={textX}
          y={textY}
          style={style}
          alignmentBaseline={'middle'}
        >
          {tooltip.name ? `${tooltip.name}: ` : ''}
          {tooltip.tooltipFn(plotContext.hoveredItem)}
        </text>
      );
    };
    const tooltipBorderPoints = [{
      x,
      y: plotContext.height / 2.0
    }, {
      x: x + direction * arrowMargin,
      y: plotContext.height / 2.0 - arrowMargin
    }, {
      x: x + direction * arrowMargin,
      y: plotContext.height / 2.0 - totalHeight / 2.0
    }, {
      x: x + direction * arrowMargin + direction * totalWidth,
      y: plotContext.height / 2.0 - totalHeight / 2.0
    }, {
      x: x + direction * arrowMargin + direction * totalWidth,
      y: plotContext.height / 2.0 + totalHeight / 2.0
    }, {
      x: x + direction * arrowMargin,
      y: plotContext.height / 2.0 + totalHeight / 2.0
    }, {
      x: x + direction * arrowMargin,
      y: plotContext.height / 2.0 + arrowMargin
    }].map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x},${p.y}`)
      .concat('Z')
      .join(' ');
    return (
      <g shapeRendering={'crispEdges'}>
        <path
          d={tooltipBorderPoints}
          stroke={'#ccc'}
          fill={'white'}
          strokeWidth={1}
        />
        {tooltips.map(renderSingleTooltip)}
      </g>
    );
  };

  render () {
    const {plotContext, xAxis: xAxisIdentifier} = this.props;
    if (!plotContext || !plotContext.hoveredItem || !this.tooltipFn) {
      return null;
    }
    const {top, bottom, height} = plotContext;
    const xAxis = xAxisIdentifier
      ? plotContext.getAxis(xAxisIdentifier)
      : plotContext.xAxis;
    if (!xAxis) {
      return null;
    }
    const x = Math.round(xAxis.getCanvasCoordinate(plotContext.hoveredItem.x));
    return (
      <g>
        <line
          x1={x}
          x2={x}
          y1={top}
          y2={height - bottom}
          stroke={'#ccc'}
          strokeWidth={1}
        />
        {this.renderTooltip(x)}
      </g>
    );
  }
}

Tooltip.propTypes = {
  fontSize: PropTypes.number,
  xAxis: PropTypes.string
};

Tooltip.defaultProps = {
  fontSize: 12
};

export default Tooltip;
