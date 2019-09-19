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
import {interpolate, PlotColors} from './utilities';

@inject('plotContext', 'plotData')
@observer
class LinePlot extends React.Component {
  @observable index = 0;

  @computed
  get color () {
    return PlotColors[this.index % PlotColors.length];
  }

  getData = (item) => {
    const {dataField} = this.props;
    if (dataField) {
      if (typeof dataField === 'string') {
        return item[dataField];
      }
      return dataField(item);
    }
    return 0;
  };

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
      this.index = plotContext.registerPlot(this);
    }
  };

  unRegister = (props) => {
    const {plotContext} = props;
    if (plotContext) {
      plotContext.unRegisterPlot(this);
    }
  };

  get xAxis () {
    const {plotContext, xAxis} = this.props;
    if (plotContext) {
      return plotContext.getAxis(xAxis);
    }
    return null;
  }

  get yAxis () {
    const {plotContext, yAxis} = this.props;
    if (plotContext) {
      return plotContext.getAxis(yAxis);
    }
    return null;
  }

  get data () {
    const {plotData} = this.props;
    if (plotData) {
      return plotData;
    }
    return [];
  }

  getDataPoint = (xAxis, yAxis) => (item) => {
    const x = xAxis.getCanvasCoordinate(item.x);
    const y = yAxis.getCanvasCoordinate(this.getData(item));
    return {x, y};
  };

  getDataPoints = (xAxis, yAxis, data) => {
    const result = data.map(this.getDataPoint(xAxis, yAxis));
    result.sort((a, b) => {
      if (a.x > b.x) {
        return 1;
      }
      if (a.x < b.x) {
        return -1;
      }
      return 0;
    });
    return result;
  };

  interpolateDataPoints = (data) => {
    const {interpolate: interpolateData} = this.props;
    if (interpolateData) {
      return interpolate(data);
    }
    return data;
  };

  extendDataPointsWithBaseLine = (baseAxis, dataPoints) => {
    if (!dataPoints || !dataPoints.length) {
      return dataPoints;
    }
    const p0 = dataPoints[0];
    const pN = dataPoints[dataPoints.length - 1];
    const axisLine = baseAxis.axisLine + baseAxis.perpendicularDirection;
    return [...dataPoints, {x: pN.x, y: axisLine}, {x: p0.x, y: axisLine}];
  };

  renderItem = (item, index) => {
    const {x, y} = item;
    if (index === 0) {
      return `M ${x},${y}`;
    }
    return `L ${x},${y}`;
  };

  renderPoint = (point, index) => {
    return (
      <circle
        key={index}
        cx={point.x}
        cy={point.y}
        r={3}
        strokeWidth={2}
        fill={'white'}
      />
    );
  };

  renderLine = (dataPoints) => {
    return dataPoints.map(this.renderItem).join(' ');
  };

  render () {
    const data = this.data;
    const x = this.xAxis;
    const y = this.yAxis;
    if (data && x && y) {
      const dataPoints = this.getDataPoints(x, y, data);
      const interpolated = this.interpolateDataPoints(dataPoints);
      const extendedPoints = this.extendDataPointsWithBaseLine(x, interpolated);
      const {plotContext} = this.props;
      return (
        <g
          mask={`url(#plot-mask-${plotContext.identifier})`}
          stroke={this.color}
          fill={this.color}
          shapeRendering={'geometricprecision'}
        >
          <path d={this.renderLine(interpolated)} fill={'none'} strokeWidth={2} />
          <path d={this.renderLine(extendedPoints)} opacity={0.125} />
          {
            dataPoints.map(this.renderPoint)
          }
        </g>
      );
    }
    return null;
  }
}

LinePlot.propTypes = {
  identifier: PropTypes.string,
  name: PropTypes.string,
  interpolate: PropTypes.bool,
  plotContext: PropTypes.object,
  plotData: PropTypes.object,
  xAxis: PropTypes.string,
  yAxis: PropTypes.string,
  dataField: PropTypes.oneOfType([PropTypes.func, PropTypes.string])
};

LinePlot.defaultProps = {
  identifier: 'line-plot',
  interpolate: false,
  xAxis: 'x',
  yAxis: 'y',
  dataField: 'y'
};

export default LinePlot;
