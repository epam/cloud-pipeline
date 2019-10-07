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
import {computed} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import {AxisDataType} from './utilities';
import tickGenerators from './ticks';

@inject('plot', 'data')
@observer
class ValueAxis extends React.PureComponent {
  state = {
    from: undefined,
    to: undefined,
    startTickBox: undefined,
    endTickBox: undefined
  };

  startTick;
  endTick;

  @computed
  get offset () {
    return 0;
  }

  @computed
  get dataRange () {
    const {from, to} = this.state;
    if (from === undefined || to === undefined) {
      return 0;
    }
    return to - from;
  }

  @computed
  get size () {
    const {plot} = this.props;
    const {chartArea, height} = plot.props;
    return height - chartArea.top - chartArea.bottom;
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
  get tickGenerator () {
    const {dataType} = this.props;
    return tickGenerators[dataType] || tickGenerators.default;
  }

  @computed
  get ticks () {
    const {from, to} = this.state;
    if (from === undefined || to === undefined) {
      return [];
    }
    if (this.tickGenerator) {
      return this.tickGenerator(from, to, this.size);
    }
    return [];
  }

  componentDidMount () {
    this.processDataGroup(this.props);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    if (
      nextProps.from !== this.props.from ||
      nextProps.to !== this.props.to ||
      nextProps.data !== this.props.data ||
      nextProps.dataGroup !== this.props.dataGroup ||
      !!nextProps.data !== !!this.props.data ||
      (
        nextProps.data &&
        nextProps.data[nextProps.dataGroup] !== this.props.data[this.props.dataGroup]
      )
    ) {
      this.processDataGroup(nextProps);
    }
  }

  processDataGroup = (props) => {
    let {from: propsFrom, to: propsTo, data, dataGroup, minimum, maximum} = props;
    const minimumCorrected = minimum === undefined ? -Infinity : minimum;
    const maximumCorrected = maximum === undefined ? Infinity : maximum;
    let from = minimum;
    let to = maximum;
    let dataFrom = minimum;
    let dataTo = maximum;
    if (data && dataGroup && data.hasOwnProperty(dataGroup)) {
      dataFrom = Math.min(from || Infinity, data[dataGroup].min);
      dataTo = Math.max(to || -Infinity, data[dataGroup].max);
      let range = dataTo - dataFrom;
      if (!isNaN(range)) {
        if (range === 0 && Math.abs(minimumCorrected) !== Infinity) {
          range = 2 * Math.abs(dataFrom - minimumCorrected || 0);
        }
        dataTo = Math.min(maximumCorrected, Math.max(minimumCorrected, dataTo + range * 0.2));
        dataFrom = Math.max(minimumCorrected, Math.min(maximumCorrected, dataFrom - range * 0.2));
      }
    }
    if (propsFrom !== undefined) {
      from = propsFrom;
    } else {
      from = dataFrom;
    }
    if (propsTo !== undefined) {
      to = propsTo;
    } else {
      to = dataTo;
    }
    this.setState({from, to});
  };

  getCanvasCoordinate = (plotCoordinate) => {
    if (this.plotToCanvasRatio === 0) {
      return 0;
    }
    const {from} = this.state;
    if (from === undefined) {
      return 0;
    }
    const {plot} = this.props;
    const {chartArea, height} = plot.props;
    return height - chartArea.bottom - (plotCoordinate - from) * this.plotToCanvasRatio;
  };

  getPlotCoordinate = (canvasCoordinate) => {
    if (this.canvasToPlotRatio === 0) {
      return 0;
    }
    const {plot} = this.props;
    const {chartArea, height} = plot.props;
    const {from} = this.state;
    return from + (height - chartArea.bottom - canvasCoordinate) * this.canvasToPlotRatio;
  };

  renderTick = (tick) => {
    const {plot, tickColor, position} = this.props;
    const {chartArea, width} = plot.props;
    const isLeft = position === 'left';
    const tickSize = tick.isBase ? 6 : 4;
    const y = this.getCanvasCoordinate(tick.tick);
    return (
      <line
        key={tick.tick}
        x1={isLeft ? chartArea.left : width - chartArea.right}
        y1={y}
        x2={isLeft ? chartArea.left - tickSize : width - chartArea.right + tickSize}
        y2={y}
        stroke={tickColor}
        strokeWidth={1}
      />
    );
  };

  updateTickBox = (name) => (text) => {
    if (text) {
      const box = text.getBBox();
      const top = box.y - box.height;
      const bottom = box.y + 2 * box.height;
      this[name] = {top, bottom};
    } else {
      this[name] = undefined;
    }
  };

  skipTickLabel = (y) => {
    const testTickBox = (box) => {
      if (box) {
        const {top, bottom} = box;
        if (top <= y && y <= bottom) {
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
    const {plot, fontColor, fontSize, position} = this.props;
    const isLeft = position === 'left';
    const {chartArea, width} = plot.props;
    const y = this.getCanvasCoordinate(tick.tick) + fontSize / 2.0 - 2;
    const x = isLeft ? chartArea.left - fontSize : width - chartArea.right + fontSize;
    const anchor = isLeft ? 'end' : 'start';
    let ref;
    if (tick.isStart) {
      ref = this.updateTickBox('startTick');
    } else if (tick.isEnd) {
      ref = this.updateTickBox('endTick');
    } else if (this.skipTickLabel(y)) {
      return null;
    }
    return (
      <text
        ref={ref}
        key={tick.tick}
        x={x}
        y={y}
        textAnchor={anchor}
        stroke={'none'}
        fill={fontColor}
        style={{fontSize}}
      >
        {tick.display}
      </text>
    );
  };

  render () {
    const {children, dataGroup, plot, tickColor, position} = this.props;
    if (!plot) {
      return null;
    }
    const {chartArea, width, height} = plot.props;
    const isLeft = position === 'left';
    return (
      <Provider
        valueAxis={this}
        dataGroup={dataGroup}
      >
        <g shapeRendering={'crispEdges'}>
          {children}
          <line
            x1={isLeft ? chartArea.left : width - chartArea.right}
            y1={chartArea.top}
            x2={isLeft ? chartArea.left : width - chartArea.right}
            y2={height - chartArea.bottom}
            stroke={tickColor}
            strokeWidth={1}
          />
          {this.ticks.map(this.renderTick)}
          {this.ticks.map(this.renderTickLabel)}
        </g>
      </Provider>
    );
  }
}

ValueAxis.propTypes = {
  data: PropTypes.object.isRequired,
  dataGroup: PropTypes.string,
  dataType: PropTypes.oneOf([
    AxisDataType.networkUsage,
    AxisDataType.mBytes,
    AxisDataType.bytes,
    AxisDataType.percent,
    AxisDataType.number
  ]),
  from: PropTypes.number,
  to: PropTypes.number,
  maximum: PropTypes.number,
  minimum: PropTypes.number,
  tickColor: PropTypes.string,
  position: PropTypes.oneOf(['left', 'right']),
  fontColor: PropTypes.string,
  fontSize: PropTypes.number
};

ValueAxis.defaultProps = {
  dataGroup: 'default',
  dataType: AxisDataType.number,
  tickColor: '#777',
  fontColor: '#777',
  fontSize: 11,
  position: 'left'
};

export default ValueAxis;
