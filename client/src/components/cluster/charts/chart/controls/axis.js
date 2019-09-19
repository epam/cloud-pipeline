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
import {
  AxisDataType,
  AxisDefaultSize,
  AxisPosition,
  generateTicks,
  networkUsageFormatter,
  memoryUsageFormatter
} from './utilities';

function numberFormatter (number, base = 2) {
  if (base > 0) {
    return Math.round(number);
  }
  const v = 10 ** (-base);
  return (Math.round(number * v) / v).toFixed(-base);
}

function percentageFormatter (number) {
  return `${number.toFixed()}%`;
}

function dateFormatter (value) {
  return moment.unix(value).format('HH:mm:ss');
}

function valueUnset (value) {
  return value === null || value === undefined || value === Infinity || value === -Infinity;
}

class Axis extends React.Component {
  static propTypes = {
    axisDataType: PropTypes.string,
    expandRangePercents: PropTypes.number,
    identifier: PropTypes.string,
    min: PropTypes.number,
    max: PropTypes.number,
    start: PropTypes.number,
    end: PropTypes.number,
    size: PropTypes.number,
    style: PropTypes.object,
    ticks: PropTypes.oneOfType([PropTypes.number, PropTypes.array]),
    tickSize: PropTypes.number,
    tickFontSizePx: PropTypes.number,
    tickFormatter: PropTypes.func,
    visible: PropTypes.bool,
    ticksVisible: PropTypes.bool
  };

  static defaultProps = {
    axisDataType: AxisDataType.number,
    expandRangePercents: 10,
    style: null,
    ticks: 10,
    tickSize: 5,
    tickFontSizePx: 10,
    tickFormatter: null,
    visible: true,
    ticksVisible: true
  };

  static defaultSize = AxisDefaultSize.horizontal;

  get size () {
    const {size} = this.props;
    return size || this.constructor.defaultSize;
  }

  get min () {
    const {min} = this.props;
    return min || -Infinity;
  }

  get max () {
    const {max} = this.props;
    return max || Infinity;
  }

  get dataValuesRangeSize () {
    if (this.dataValuesEnd === undefined || this.dataValuesStart === undefined) {
      return 0;
    }
    return this.dataValuesEnd - this.dataValuesStart;
  }

  get expandRangeSize () {
    const {expandRangePercents} = this.props;
    return this.dataValuesRangeSize * expandRangePercents / 100.0;
  }

  get dataValuesStart () {
    return undefined;
  }

  get dataValuesEnd () {
    return undefined;
  }

  get start () {
    const {start} = this.props;
    if (!valueUnset(start)) {
      return start;
    }
    if (!valueUnset(this.dataValuesStart)) {
      return Math.max(this.dataValuesStart - this.expandRangeSize, this.min);
    }
    return this.min;
  }

  get end () {
    const {end} = this.props;
    if (!valueUnset(end)) {
      return end;
    }
    if (!valueUnset(this.dataValuesEnd)) {
      return Math.min(this.dataValuesEnd + this.expandRangeSize, this.max);
    }
    return this.max;
  }

  get canvasSize () {
    return 0;
  }

  @computed
  get canvasToPlotRatio () {
    if (this.invalid || !this.canvasSize) {
      return 1;
    }
    return (this.end - this.start) / this.canvasSize;
  }

  @computed
  get plotToCanvasRatio () {
    return 1.0 / this.canvasToPlotRatio;
  }

  get axisLine () {
    return 0;
  }

  get canvasStart () {
    return 0;
  }

  get direction () {
    return 0;
  }

  get perpendicularDirection () {
    return 0;
  }

  get ticks () {
    const {ticks, axisDataType} = this.props;
    if (Array.isArray(ticks)) {
      return ticks;
    }
    return generateTicks(
      this.start,
      this.end,
      ticks,
      axisDataType === AxisDataType.networkUsage ? 1024 : 10);
  }

  get invalid () {
    return valueUnset(this.start) || valueUnset(this.end);
  }

  get tickFormatter () {
    const {axisDataType, tickFormatter} = this.props;
    if (tickFormatter) {
      return tickFormatter;
    }
    switch (axisDataType) {
      case AxisDataType.number: return numberFormatter;
      case AxisDataType.bytes: return memoryUsageFormatter;
      case AxisDataType.mBytes: return numberFormatter;
      case AxisDataType.date: return dateFormatter;
      case AxisDataType.percent: return percentageFormatter;
      case AxisDataType.networkUsage: return networkUsageFormatter;
      default: return numberFormatter;
    }
  }

  componentDidMount () {
    this.register(this.props);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    if (nextProps.plotContext !== this.props.plotContext) {
      this.unRegister(this.props);
      this.register(this.props);
    }
  }

  componentWillUnmount () {
    this.unRegister(this.props);
  }

  register = (props) => {
    const {plotContext} = props;
    if (plotContext) {
      plotContext.registerAxis(this);
    }
  };

  unRegister = (props) => {
    const {plotContext} = props;
    if (plotContext) {
      plotContext.unRegisterAxis(this);
    }
  };

  getCanvasCoordinate (value) {
    return this.canvasStart + this.direction * (value - this.start) * this.plotToCanvasRatio;
  }

  getPlotCoordinate (pixels) {
    return this.start + this.direction * (pixels - this.canvasStart) * this.canvasToPlotRatio;
  }

  render () {
    return null;
  }
}

@inject('plotContext')
@observer
class XAxis extends Axis {
  @computed
  get dataValuesStart () {
    const {plotContext} = this.props;
    const {min, max} = plotContext.dataXRange;
    if (min > max) {
      return undefined;
    }
    return min;
  }

  @computed
  get dataValuesEnd () {
    const {plotContext} = this.props;
    const {min, max} = plotContext.dataXRange;
    if (min > max) {
      return undefined;
    }
    return max;
  }

  get top () {
    const {
      plotContext,
      position
    } = this.props;
    if (plotContext) {
      const height = plotContext.height;
      const offset = plotContext.getAxisOffset(this);
      return position === AxisPosition.bottom
        ? height - offset - this.size
        : offset;
    }
    return 0;
  }

  get bottom () {
    const {
      plotContext,
      position
    } = this.props;
    if (plotContext) {
      const height = plotContext.height;
      const offset = plotContext.getAxisOffset(this);
      return position === AxisPosition.bottom
        ? height - offset
        : offset + this.size;
    }
    return 0;
  }

  get axisLine () {
    const {position} = this.props;
    return position === AxisPosition.bottom
      ? this.top
      : this.bottom;
  }

  get canvasStart () {
    return this.left;
  }

  get direction () {
    return 1;
  }

  get perpendicularDirection () {
    const {position} = this.props;
    return position === AxisPosition.bottom ? -1 : 1;
  }

  @computed
  get left () {
    const {
      plotContext
    } = this.props;
    if (plotContext) {
      return plotContext.left;
    }
    return 0;
  }

  @computed
  get right () {
    const {
      plotContext
    } = this.props;
    if (plotContext) {
      const width = plotContext.width;
      return width - plotContext.right;
    }
    return 0;
  }

  @computed
  get canvasSize () {
    return Math.abs(this.right - this.left);
  }

  renderTicks = (ticks) => {
    const {
      tickSize
    } = this.props;
    const mainY = this.axisLine;
    const renderTick = (tick) => {
      const x = this.getCanvasCoordinate(tick);
      const yTick1 = mainY;
      const yTick2 = Math.round(mainY - tickSize * this.perpendicularDirection);
      return `M ${x},${yTick1} L ${x},${yTick2}`;
    };
    const ticksPath = ticks.ticks.map(renderTick).join(' ');
    return (
      <path d={ticksPath} />
    );
  };

  renderTickLabels = (ticks) => {
    const {
      position,
      tickSize,
      tickFontSizePx,
      style
    } = this.props;
    const mainY = this.axisLine;
    const tickDirection = position === AxisPosition.bottom ? 1 : 0;
    const renderTickLabel = (tick) => {
      const x = this.getCanvasCoordinate(tick);
      const tickY = Math.round(
        mainY - tickSize * this.perpendicularDirection + tickFontSizePx * tickDirection
      );
      if (tickY - tickFontSizePx / 2.0 < 0 || tickY + tickFontSizePx / 2.0 > this.bottom) {
        return null;
      }
      let anchor = 'middle';
      return (
        <text
          key={tick}
          x={x}
          y={tickY}
          textAnchor={anchor}
          style={{
            fontSize: `${tickFontSizePx}px`,
            stroke: 'transparent',
            fill: (style || {}).stroke || '#999'
          }}
        >
          {this.tickFormatter(tick, ticks.base)}
        </text>
      );
    };
    return ticks.ticks.map(renderTickLabel);
  };

  render () {
    if (this.invalid) {
      return null;
    }
    const {
      plotContext,
      style,
      visible,
      ticksVisible
    } = this.props;
    if (!visible) {
      return null;
    }
    if (plotContext) {
      const ticks = this.ticks;
      return (
        <g style={Object.assign({stroke: '#999'}, style)}>
          <path d={`M ${this.left},${this.axisLine} L ${this.right},${this.axisLine}`} />
          {ticksVisible && this.renderTicks(ticks)}
          {ticksVisible && this.renderTickLabels(ticks)}
        </g>
      );
    }
    return null;
  }
}

@inject('plotContext')
@observer
class YAxis extends Axis {
  static defaultSize = AxisDefaultSize.vertical;

  @computed
  get dataValuesStart () {
    const {plotContext} = this.props;
    const {min, max} = plotContext.getDataRange(this);
    if (min > max) {
      return undefined;
    }
    return min;
  }

  @computed
  get dataValuesEnd () {
    const {plotContext} = this.props;
    const {min, max} = plotContext.getDataRange(this);
    if (min > max) {
      return undefined;
    }
    return max;
  }

  @computed
  get top () {
    const {
      plotContext
    } = this.props;
    if (plotContext) {
      return plotContext.top;
    }
    return 0;
  }

  @computed
  get bottom () {
    const {
      plotContext
    } = this.props;
    if (plotContext) {
      const height = plotContext.height;
      return height - plotContext.bottom;
    }
    return 0;
  }

  get left () {
    const {
      plotContext,
      position
    } = this.props;
    if (plotContext) {
      const width = plotContext.width;
      const offset = plotContext.getAxisOffset(this);
      return position === AxisPosition.right
        ? width - offset - this.size
        : offset;
    }
    return 0;
  }

  get right () {
    const {
      plotContext,
      position
    } = this.props;
    if (plotContext) {
      const width = plotContext.width;
      const offset = plotContext.getAxisOffset(this);
      return position === AxisPosition.right
        ? width - offset
        : offset + this.size;
    }
    return 0;
  }

  @computed
  get canvasSize () {
    return Math.abs(this.bottom - this.top);
  }

  get axisLine () {
    const {position} = this.props;
    return position === AxisPosition.right ? this.left : this.right;
  }

  get canvasStart () {
    return this.bottom;
  }

  get direction () {
    return -1;
  }

  get perpendicularDirection () {
    const {position} = this.props;
    return position === AxisPosition.right ? -1 : 1;
  }

  renderTicks = (ticks) => {
    const {
      tickSize
    } = this.props;
    const mainX = this.axisLine;
    const renderTick = (tick) => {
      const y = this.getCanvasCoordinate(tick);
      const xTick1 = mainX;
      const xTick2 = Math.round(mainX - tickSize * this.perpendicularDirection);
      return `M ${xTick1},${y} L ${xTick2},${y}`;
    };
    const ticksPath = ticks.ticks.map(renderTick).join(' ');
    return (
      <path d={ticksPath} />
    );
  };

  renderTickLabels = (ticks) => {
    const {
      position,
      tickSize,
      tickFontSizePx,
      style
    } = this.props;
    const mainX = this.axisLine;
    const renderTickLabel = (tick) => {
      const y = this.getCanvasCoordinate(tick) + tickFontSizePx / 3.0;
      const x = Math.round(mainX - tickSize * this.perpendicularDirection);
      let anchor = 'start';
      if (position === AxisPosition.left) {
        anchor = 'end';
      }
      return (
        <text
          key={tick}
          x={x}
          y={y}
          textAnchor={anchor}
          style={{
            fontSize: `${tickFontSizePx}px`,
            stroke: 'transparent',
            fill: (style || {}).stroke || '#999'
          }}
        >
          {this.tickFormatter(tick, ticks.base)}
        </text>
      );
    };
    return ticks.ticks.map(renderTickLabel);
  };

  render () {
    if (this.invalid) {
      return null;
    }
    const {
      plotContext,
      style,
      visible,
      ticksVisible
    } = this.props;
    if (!visible) {
      return null;
    }
    if (plotContext) {
      const ticks = this.ticks;
      return (
        <g style={Object.assign({stroke: '#999'}, style)}>
          <path d={`M ${this.axisLine},${this.top} L ${this.axisLine},${this.bottom}`} />
          {ticksVisible && this.renderTicks(ticks)}
          {ticksVisible && this.renderTickLabels(ticks)}
        </g>
      );
    }
    return null;
  }
}

XAxis.propTypes = {
  axisDataType: PropTypes.string,
  identifier: PropTypes.string,
  min: PropTypes.number,
  max: PropTypes.number,
  start: PropTypes.number,
  end: PropTypes.number,
  style: PropTypes.object,
  position: PropTypes.oneOf([AxisPosition.bottom, AxisPosition.top]),
  ticks: PropTypes.oneOfType([PropTypes.number, PropTypes.array]),
  tickSize: PropTypes.number,
  tickFontSizePx: PropTypes.number,
  tickFormatter: PropTypes.func,
  visible: PropTypes.bool,
  ticksVisible: PropTypes.bool
};

YAxis.propTypes = {
  axisDataType: PropTypes.string,
  identifier: PropTypes.string,
  min: PropTypes.number,
  max: PropTypes.number,
  start: PropTypes.number,
  end: PropTypes.number,
  style: PropTypes.object,
  position: PropTypes.oneOf([AxisPosition.left, AxisPosition.right]),
  ticks: PropTypes.oneOfType([PropTypes.number, PropTypes.array]),
  tickSize: PropTypes.number,
  tickFontSizePx: PropTypes.number,
  tickFormatter: PropTypes.func,
  visible: PropTypes.bool,
  ticksVisible: PropTypes.bool
};

XAxis.defaultProps = {
  expandRangePercents: 0,
  identifier: 'x',
  position: AxisPosition.bottom
};

YAxis.defaultProps = {
  identifier: 'y',
  position: AxisPosition.left
};

export {XAxis, YAxis};
