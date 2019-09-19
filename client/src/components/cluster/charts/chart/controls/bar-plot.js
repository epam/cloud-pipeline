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
import {observable} from 'mobx';
import {PlotColors, usageFormatter} from './utilities';

function getColor (index) {
  return PlotColors[index % PlotColors.length];
}

@inject('plotContext', 'plotData')
@observer
class BarPlot extends React.Component {
  @observable index = 0;

  getData = (item, fn) => {
    if (fn) {
      if (typeof fn === 'string') {
        return item[fn];
      }
      return fn(item);
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
    const {plotData, series} = this.props;
    if (plotData && plotData.length && series) {
      const lastItem = plotData[plotData.length - 1];
      return series.map(s => ({
        name: s.name,
        value: this.getData(lastItem, s.value),
        total: this.getData(lastItem, s.total)
      }));
    }
    return [];
  }

  renderSerie = (xAxis, yAxis) => (serie, index) => {
    if (!xAxis || !yAxis) {
      return null;
    }
    const {maxBarHeight} = this.props;
    const x1 = xAxis.getCanvasCoordinate(0);
    const x2 = xAxis.getCanvasCoordinate(serie.value / serie.total * 100.0);
    const x3 = xAxis.getCanvasCoordinate(100) - 1;
    const y = yAxis.getCanvasCoordinate(index);
    const y1 = Math.max(y - maxBarHeight / 2.0, yAxis.getCanvasCoordinate(index + 0.5)) + 2;
    const y2 = Math.min(y + maxBarHeight / 2.0, yAxis.getCanvasCoordinate(index - 0.5)) - 2;
    const color = getColor(index);
    return (
      <g key={index} shapeRendering={'crispEdges'}>
        <rect
          x={x2}
          y={y1}
          width={x3 - x2}
          height={y2 - y1}
          fill={'#aaa'}
          opacity={0.25}
          stroke={'none'}
        />
        <rect
          x={x2}
          y={y1}
          width={x3 - x2}
          height={y2 - y1}
          fill={'none'}
          stroke={'#aaa'}
          strokeWidth={1}
        />
        <rect
          x={x1}
          y={y1}
          width={x2 - x1}
          height={y2 - y1}
          fill={color}
          opacity={0.25}
          stroke={'none'}
        />
        <rect
          x={x1}
          y={y1}
          width={x2 - x1}
          height={y2 - y1}
          fill={'none'}
          stroke={color}
          strokeWidth={1}
        />
        {
          serie.name &&
          (
            <text
              x={x1 + 1}
              y={(y1 + y2) / 2.0 + 5}
              textAnchor={'start'}
              stroke={'none'}
              fill={color}
              style={{fontSize: 12}}
            >
              {serie.name}
            </text>
          )
        }
        <text
          x={x3 - 5}
          y={(y1 + y2) / 2.0 + 5}
          textAnchor={'end'}
          stroke={'none'}
          fill={'#333'}
          style={{fontSize: 12}}
        >
          {usageFormatter(serie.value, serie.total)}
        </text>
      </g>
    );
  };

  render () {
    const data = this.data;
    const x = this.xAxis;
    const y = this.yAxis;
    if (data && x && y) {
      const {plotContext} = this.props;
      return (
        <g
          mask={`url(#plot-mask-${plotContext.identifier})`}
          shapeRendering={'geometricprecision'}
        >
          {
            data.map(this.renderSerie(x, y))
          }
        </g>
      );
    }
    return null;
  }
}

const SeriesPropTypes = PropTypes.shape({
  name: PropTypes.string,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
  total: PropTypes.oneOfType([PropTypes.string, PropTypes.func])
});

BarPlot.propTypes = {
  identifier: PropTypes.string,
  name: PropTypes.string,
  plotContext: PropTypes.object,
  plotData: PropTypes.object,
  xAxis: PropTypes.string,
  yAxis: PropTypes.string,
  series: PropTypes.arrayOf(SeriesPropTypes),
  maxBarHeight: PropTypes.number
};

BarPlot.defaultProps = {
  identifier: 'bar-plot',
  xAxis: 'x',
  yAxis: 'y',
  maxBarHeight: 40
};

export default BarPlot;
