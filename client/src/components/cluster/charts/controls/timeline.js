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
import moment from 'moment';
import {computed} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import DateTimeTicksRules from './ticks/date-time-rules';
import attachMoveHandler from './utilities/attach-move-handler';
import attachZoomHandler from './utilities/attach-zoom-handler';

const SizePerTickPx = 100;

@inject('plot', 'data')
@observer
class Timeline extends React.PureComponent {
  state = {
    from: undefined,
    to: undefined,
    startTickBox: undefined,
    endTickBox: undefined
  };

  startTick;
  endTick;

  detachMoveHandler;
  detachZoomHandler;

  @computed
  get dataRange () {
    const {from, to} = this.state;
    if (!from || !to) {
      return 0;
    }
    return to - from;
  }

  @computed
  get size () {
    const {plot} = this.props;
    const {chartArea, width} = plot.props;
    return width - chartArea.left - chartArea.right;
  }

  @computed
  get plotToCanvasRatio () {
    if (this.dataRange === 0) {
      return 0;
    }
    return this.size / this.dataRange;
  }

  @computed
  get canvasToPlotRatio () {
    if (this.plotToCanvasRatio === 0) {
      return 0;
    }
    return 1.0 / this.plotToCanvasRatio;
  }

  @computed
  get ticks () {
    if (this.baseTickRule === null) {
      return [];
    }
    const {from, to} = this.state;
    if (!from || !to) {
      return null;
    }
    return this.baseTickRule.fillRange(moment.unix(from), moment.unix(to), true);
  }

  @computed
  get baseTicksCount () {
    return Math.floor(this.size / SizePerTickPx);
  }

  @computed
  get baseTickRule () {
    if (this.dataRange === 0) {
      return null;
    }
    const duration = moment.duration(this.dataRange, 's');
    const durations = DateTimeTicksRules
      .map(rule => ({
        ...rule,
        duration: rule.fn(duration)
      }));
    return durations
      .filter(d => d.duration <= this.baseTicksCount).pop() || durations[0];
  }

  componentDidMount () {
    const {from, to, interactiveArea, onRangeChanged} = this.props;
    this.setState({
      from, to
    });
    this.detachMoveHandler = attachMoveHandler(this, interactiveArea, onRangeChanged);
    this.detachZoomHandler = attachZoomHandler(this, interactiveArea, onRangeChanged);
  }

  componentWillUnmount () {
    if (this.detachMoveHandler) {
      this.detachMoveHandler();
    }
    if (this.detachZoomHandler) {
      this.detachZoomHandler();
    }
  }

  componentWillReceiveProps (nextProps, nextContext) {
    const newState = {};
    if (nextProps.from !== this.props.from) {
      newState.from = nextProps.from;
    }
    if (nextProps.to !== this.props.to) {
      newState.to = nextProps.to;
    }
    if (nextProps.interactiveArea !== this.props.interactiveArea) {
      if (this.detachMoveHandler) {
        this.detachMoveHandler();
      }
      if (this.detachZoomHandler) {
        this.detachZoomHandler();
      }
      this.detachMoveHandler = attachMoveHandler(
        this,
        nextProps.interactiveArea,
        nextProps.onRangeChanged
      );
      this.detachZoomHandler = attachZoomHandler(
        this,
        nextProps.interactiveArea,
        nextProps.onRangeChanged
      );
    }
    if (Object.keys(newState).length > 0) {
      this.setState(newState);
    }
  }

  getCanvasCoordinate = (plotCoordinate) => {
    if (this.plotToCanvasRatio === 0) {
      return 0;
    }
    const {from} = this.state;
    if (!from) {
      return 0;
    }
    const {plot} = this.props;
    const {chartArea} = plot.props;
    return chartArea.left + (plotCoordinate - from) * this.plotToCanvasRatio;
  };

  getPlotCoordinate = (canvasCoordinate) => {
    if (this.canvasToPlotRatio === 0) {
      return 0;
    }
    const {plot} = this.props;
    const {chartArea} = plot.props;
    const {from} = this.state;
    return from + (canvasCoordinate - chartArea.left) * this.canvasToPlotRatio;
  };

  renderTick = (tick) => {
    const {plot, tickColor} = this.props;
    const {chartArea, height} = plot.props;
    const tickSize = tick.isBase ? 6 : 4;
    const x = this.getCanvasCoordinate(tick.tick);
    return (
      <line
        key={tick.tick}
        x1={x}
        y1={height - chartArea.bottom}
        x2={x}
        y2={height - chartArea.bottom + tickSize}
        stroke={tickColor}
        strokeWidth={1}
      />
    );
  };

  updateTickBox = (name) => (text) => {
    if (text) {
      const box = text.getBBox();
      const left = box.x - box.width / 2.0;
      const right = box.x + 3 * box.width / 2.0;
      this[name] = {left, right};
    } else {
      this[name] = undefined;
    }
  };

  skipTickLabel = (x) => {
    const testTickBox = (box) => {
      if (box) {
        const {left, right} = box;
        if (left <= x && x <= right) {
          return true;
        }
      }
      return false;
    };
    return testTickBox(this.startTick) || testTickBox(this.endTick);
  };

  renderTickLabel = (tick) => {
    if (!tick.isBase) {
      return null;
    }
    const {plot, fontColor, fontSize} = this.props;
    const {chartArea, height} = plot.props;
    const x = this.getCanvasCoordinate(tick.tick);
    const y = height - chartArea.bottom + 6 + fontSize;
    let ref;
    if (tick.isStart) {
      ref = this.updateTickBox('startTick');
    } else if (tick.isEnd) {
      ref = this.updateTickBox('endTick');
    } else if (this.skipTickLabel(x, y)) {
      return null;
    }
    return (
      <text
        ref={ref}
        key={tick.tick}
        x={x}
        y={y}
        textAnchor={'middle'}
        stroke={'none'}
        fill={fontColor}
        style={{fontSize}}
      >
        {tick.display}
      </text>
    );
  };

  renderZoomArea = () => {
    const {zoom} = this.state;
    const {plot, fontColor, fontSize} = this.props;
    const {chartArea, height, width} = plot.props;
    if (zoom && zoom.start && zoom.end && this.baseTickRule) {
      const from = Math.min(zoom.start, zoom.end);
      const to = Math.max(zoom.start, zoom.end);
      const x1 = Math.max(this.getCanvasCoordinate(from), chartArea.left);
      const x2 = Math.min(this.getCanvasCoordinate(to), width - chartArea.right);
      return (
        <g>
          <rect
            x={x1}
            width={x2 - x1}
            y={chartArea.top}
            height={height - chartArea.top - chartArea.bottom}
            fill={`#333`}
            opacity={0.1}
          />
          <line
            x1={x1}
            x2={x1}
            y1={chartArea.top}
            y2={height - chartArea.bottom}
            stroke={'#999'}
            strokeWidth={1}
          />
          <line
            x1={x2}
            x2={x2}
            y1={chartArea.top}
            y2={height - chartArea.bottom}
            stroke={'#999'}
            strokeWidth={1}
          />
          <text
            x={x1 - fontSize}
            y={height / 2.0}
            textAnchor={'end'}
            stroke={'none'}
            fill={fontColor}
            style={{fontSize}}
          >
            {this.baseTickRule.getFullDescription(moment.unix(from))}
          </text>
          <text
            x={x2 + fontSize}
            y={height / 2.0}
            textAnchor={'start'}
            stroke={'none'}
            fill={fontColor}
            style={{fontSize}}
          >
            {this.baseTickRule.getFullDescription(moment.unix(to))}
          </text>
        </g>
      );
    }
    return null;
  };

  render () {
    const {children, plot, tickColor} = this.props;
    if (!plot) {
      return null;
    }
    const {chartArea, width, height} = plot.props;
    return (
      <Provider
        timeline={this}
      >
        <g shapeRendering={'crispEdges'}>
          {children}
          <line
            x1={chartArea.left}
            y1={height - chartArea.bottom}
            x2={width - chartArea.right}
            y2={height - chartArea.bottom}
            stroke={tickColor}
            strokeWidth={1}
          />
          {this.ticks.map(this.renderTick)}
          {this.ticks.map(this.renderTickLabel)}
          {this.renderZoomArea()}
        </g>
      </Provider>
    );
  }
}

Timeline.propTypes = {
  from: PropTypes.number.isRequired,
  maximum: PropTypes.number,
  minimum: PropTypes.number,
  to: PropTypes.number.isRequired,
  tickColor: PropTypes.string,
  fontColor: PropTypes.string,
  fontSize: PropTypes.number,
  interactiveArea: PropTypes.object,
  onRangeChanged: PropTypes.func
};

Timeline.defaultProps = {
  tickColor: '#777',
  fontColor: '#777',
  fontSize: 11
};

export default Timeline;
