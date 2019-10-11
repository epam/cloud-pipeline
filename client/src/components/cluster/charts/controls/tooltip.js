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
import {computed, observable} from 'mobx';
import moment from 'moment';
import {PlotColors} from './utilities';

const MARGIN = 5;

class TooltipRenderer extends React.PureComponent {
  state = {
    sizes: {}
  };

  componentWillReceiveProps (nextProps, nextContext) {
    if (nextProps.tooltipString !== this.props.tooltipString) {
      this.setState({sizes: {}});
    }
  }

  @computed
  get tooltips () {
    const {xPoint, tooltips} = this.props;
    if (tooltips && xPoint) {
      return [
        {
          title: 'Date',
          color: '#666',
          value: moment.unix(xPoint).format('D MMM, YYYY HH:mm')
        },
        ...tooltips
      ];
    }
    return [];
  }

  renderTooltip = (x) => {
    const tooltips = this.tooltips;
    const arrowMargin = 5;
    const arrowDistance = 5;
    const {fontSize, left, right, top, bottom} = this.props;
    const canvasWidth = right - left;
    const canvasHeight = bottom - top;
    const {sizes} = this.state;
    const {width: totalWidth, height: totalHeight} = tooltips
      .map(tooltip => `${tooltip.title}: ${tooltip.value}`)
      .map(key => sizes[key] || {width: 0, height: 0})
      .reduce((result, current) => ({
        width: Math.max(result.width, current.width),
        height: result.height + current.height + MARGIN
      }), {width: 0, height: MARGIN});
    const direction = x > canvasWidth / 2.0 ? -1 : 1;
    const xShift = x > canvasWidth / 2.0
      ? -totalWidth - arrowMargin - arrowDistance
      : arrowMargin + arrowDistance;
    const getHeightBefore = (index) => {
      return tooltips
        .map(tooltip => `${tooltip.title}: ${tooltip.value}`)
        .map(key => sizes[key] || {width: 0, height: 0})
        .filter((t, i) => i < index)
        .reduce((result, current) => result + current.height + MARGIN, MARGIN);
    };
    const renderSingleTooltip = (tooltip, index) => {
      const key = `${tooltip.title}: ${tooltip.value}`;
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
      let textY = canvasHeight / 2.0 -
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
          {tooltip.title}: {tooltip.value}
        </text>
      );
    };
    const tooltipBorderPoints = [{
      x: x + arrowDistance * direction,
      y: canvasHeight / 2.0
    }, {
      x: x + direction * (arrowMargin + arrowDistance),
      y: canvasHeight / 2.0 - arrowMargin
    }, {
      x: x + direction * (arrowMargin + arrowDistance),
      y: canvasHeight / 2.0 - totalHeight / 2.0
    }, {
      x: x + direction * (arrowMargin + arrowDistance + totalWidth),
      y: canvasHeight / 2.0 - totalHeight / 2.0
    }, {
      x: x + direction * (arrowMargin + arrowDistance + totalWidth),
      y: canvasHeight / 2.0 + totalHeight / 2.0
    }, {
      x: x + direction * (arrowMargin + arrowDistance),
      y: canvasHeight / 2.0 + totalHeight / 2.0
    }, {
      x: x + direction * (arrowMargin + arrowDistance),
      y: canvasHeight / 2.0 + arrowMargin
    }].map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x},${p.y}`)
      .concat('Z')
      .join(' ');
    return (
      <g shapeRendering={'crispEdges'}>
        <path
          d={tooltipBorderPoints}
          stroke={'none'}
          fill={'white'}
          opacity={0.85}
        />
        <path
          d={tooltipBorderPoints}
          stroke={'#ccc'}
          fill={'none'}
          strokeWidth={1}
        />
        {tooltips.map(renderSingleTooltip)}
      </g>
    );
  };

  render () {
    const {
      visible,
      xPoint,
      left,
      top,
      bottom,
      ratio,
      timelineStart,
      tooltips
    } = this.props;
    if (!visible || !tooltips || tooltips.length === 0) {
      return null;
    }
    const x = left + (xPoint - timelineStart) * ratio;
    return (
      <g shapeRendering={'cripsEdges'}>
        <line
          x1={x}
          x2={x}
          y1={top}
          y2={bottom}
          stroke={'#666'}
          strokeWidth={1}
        />
        {this.renderTooltip(x, xPoint)}
      </g>
    );
  }
}

TooltipRenderer.propTypes = {
  visible: PropTypes.bool,
  xPoint: PropTypes.number,
  timelineStart: PropTypes.number,
  ratio: PropTypes.number,
  left: PropTypes.number,
  right: PropTypes.number,
  top: PropTypes.number,
  bottom: PropTypes.number,
  tooltipString: PropTypes.string,
  tooltips: PropTypes.array,
  fontSize: PropTypes.number
};

TooltipRenderer.defaultProps = {
  fontSize: 12
};

@inject('data', 'plot', 'timeline')
@observer
class Tooltip extends React.PureComponent {
  @observable hoveredItem;

  componentDidMount () {
    window.addEventListener('mousemove', this.mouseMove);
    if (this.props.data) {
      this.props.data.registerListener(this.dataUpdated);
    }
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.mouseMove);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    if (nextProps.data !== this.props.data) {
      if (this.props.data) {
        this.props.data.unRegisterListener(this.dataUpdated);
      }
      if (nextProps.data) {
        nextProps.data.registerListener(this.dataUpdated);
      }
    }
  }

  dataUpdated = () => {
    this.hoveredItem = null;
  };

  mouseMove = (event) => {
    const {data, plot, timeline} = this.props;
    if (data && event.buttons === 0 && timeline && plot) {
      const {canvas} = plot;
      const {chartArea} = plot.props;
      const {from} = timeline.state;
      const dim = canvas.getBoundingClientRect();
      const x = event.clientX - dim.left;
      const y = event.clientY - dim.top;
      if (
        x >= chartArea.left &&
        x <= dim.width - chartArea.right &&
        y >= chartArea.top &&
        y <= dim.height - chartArea.bottom
      ) {
        const timelinePosition = from + (x - chartArea.left) * timeline.canvasToPlotRatio;
        let xPoint = data.xPoints[0];
        let diff = Math.abs(xPoint - timelinePosition);
        for (let i = 1; i < data.xPoints.length; i++) {
          const candidate = Math.abs(data.xPoints[i] - timelinePosition);
          if (candidate < diff) {
            diff = candidate;
            xPoint = data.xPoints[i];
          }
        }
        this.hoveredItem = data.groups.map(group => {
          const [result] = (data.data[group]?.data || []).filter(item => item.x === xPoint);
          if (result) {
            return {[group]: result};
          }
          return {};
        }).reduce((r, c) => ({...r, ...c}), {xPoint});
        return;
      }
    }
    this.hoveredItem = null;
  };

  render () {
    const {plot, timeline} = this.props;
    if (!plot || !timeline || !this.hoveredItem) {
      return null;
    }
    const {from} = timeline.state;
    const {chartArea, dataGroup, height, plots, width} = plot.props;
    const defaultFormatter = o => o ? o.toFixed(2) : '';
    const tooltips = plots
      .map((p, i) => ({...p, color: PlotColors[i % PlotColors.length]}))
      .filter(p => (p.group || 'default') === (dataGroup || 'default') || p.isPercent)
      .map(p => ({
        title: p.title || p.name,
        color: p.color,
        formatter: p.formatter || defaultFormatter,
        value: (this.hoveredItem[p.group || 'default'] || {})[p.name]
      }))
      .filter(p => p.value !== undefined)
      .map(p => ({
        ...p,
        value: p.formatter(p.value)
      }));
    return (
      <TooltipRenderer
        visible={!!this.hoveredItem}
        xPoint={this.hoveredItem ? this.hoveredItem.xPoint : null}
        left={chartArea.left}
        right={width - chartArea.right}
        top={chartArea.top}
        bottom={height - chartArea.bottom}
        ratio={timeline.plotToCanvasRatio}
        timelineStart={from}
        tooltips={tooltips}
        tooltipString={tooltips.map(t => `${t.title}: ${t.value}`).join('\n')}
      />
    );
  }
}

Tooltip.propTypes = {
  config: PropTypes.arrayOf(PropTypes.shape({
    group: PropTypes.string,
    field: PropTypes.string,
    title: PropTypes.string,
    formatter: PropTypes.func
  }))
};

export default Tooltip;
