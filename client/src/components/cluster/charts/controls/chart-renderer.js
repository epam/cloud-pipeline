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
import {PlotColors} from './utilities';

function plotsMapper (plot, index) {
  return {...plot, index};
}

function plotsConfigurationsAreEqual (array1, array2) {
  if (!array1 && !array2) {
    return true;
  }
  if (!array1 || !array2) {
    return false;
  }
  if (array1.length !== array2.length) {
    return false;
  }
  for (let i = 0; i < array1.length; i++) {
    if (array1[i].name !== array2[i].name || array1[i].index !== array2[i].index) {
      return false;
    }
  }
  return true;
}

function ChartRenderer (
  {
    data,
    dataGroup = 'default',
    plot,
    timeline,
    valueAxis,
    identifier = 'plot',
    isPercent = false
  }
) {
  if (
    !valueAxis ||
    !timeline ||
    !plot ||
    !data ||
    !data.data ||
    !data.data?.hasOwnProperty(dataGroup)
  ) {
    return null;
  }
  const {plots} = plot.props;
  if (!plots) {
    return null;
  }
  const maskIdentifier = `${identifier.replace(/ /, '-')}-mask`;
  const {from} = timeline.state;
  const {from: yFrom} = valueAxis.state;
  const {xMin} = data;
  const {chartArea, height, width} = plot.props;
  const xOffset = (xMin - from) * timeline.plotToCanvasRatio + chartArea.left;
  return (
    <g transform={`translate(${xOffset || 0}, 0)`}>
      <mask id={maskIdentifier}>
        <rect
          stroke={'transparent'}
          strokeWidth={0}
          fill={'white'}
          x={chartArea.left - (xOffset || 0)}
          y={0}
          width={width - chartArea.left - chartArea.right}
          height={height - chartArea.bottom}
        />
      </mask>
      <ChartRendererWithOffset
        baseLine={height - chartArea.bottom}
        canvasYStart={height - chartArea.bottom}
        xRatio={timeline.plotToCanvasRatio}
        yFrom={yFrom}
        yRatio={valueAxis.plotToCanvasRatio}
        values={data.data[dataGroup]?.data || []}
        xMin={xMin}
        plots={
          plots.map(plotsMapper)
            .filter(plot => plot.renderer === identifier || plot.isPercent === isPercent)
        }
        maskIdentifier={maskIdentifier}
      />
    </g>
  );
}

function splitDataParts(data) {
  const result = [];
  let current;
  for (let d = 0; d < (data || []).length; d++) {
    const item = data[d];
    if (item.y === undefined && current) {
      current = null;
    } else if (item.y !== undefined) {
      if (!current) {
        current = [];
        result.push(current);
      }
      current.push(item);
    }
  }
  return result;
}

@observer
class ChartRendererWithOffset extends React.PureComponent {
  state = {
    baseLine: 0,
    data: [],
    xMin: 0,
    xRatio: 0,
    yFrom: 0,
    yRatio: 0,
    canvasYStart: 0,
    plots: []
  };

  @computed
  get data () {
    const {canvasYStart, data, xRatio, yFrom, yRatio, plots} = this.state;
    const getXCoordinate = (plotX) => {
      return plotX * xRatio;
    };
    const getYCoordinate = (plotY) => {
      if (plotY === undefined) {
        return undefined;
      }
      return canvasYStart - (plotY - yFrom) * yRatio;
    };
    return plots.map((plot) => ({
      plot: plot.name,
      color: PlotColors[plot.index % PlotColors.length],
      data: splitDataParts(
        (data || [])
          .map(i => ({x: getXCoordinate(i.x), y: getYCoordinate(i[plot.name])}))
      )
    }));
  }

  componentDidMount () {
    const newState = {};
    const props = this.props;
    newState.baseLine = props.baseLine || 0;
    newState.data = (props.values || []).map(i => ({...i, x: i.x - (props.xMin || 0)}));
    newState.xMin = props.xMin || 0;
    newState.xRatio = props.xRatio || 0;
    newState.canvasYStart = props.canvasYStart || 0;
    newState.yFrom = props.yFrom || 0;
    newState.yRatio = props.yRatio || 0;
    newState.plots = props.plots || [];
    this.setState(newState);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    const newState = {};
    if (nextProps.baseLine !== this.props.baseLine) {
      newState.baseLine = nextProps.baseLine;
    }
    if (nextProps.values !== this.props.values || nextProps.xMin !== this.props.xMin) {
      newState.data = (nextProps.values || []).map(i => ({...i, x: i.x - nextProps.xMin}));
    }
    if (nextProps.xMin !== this.props.xMin) {
      newState.xMin = nextProps.xMin;
    }
    if (nextProps.xRatio !== this.props.xRatio) {
      newState.xRatio = nextProps.xRatio;
    }
    if (nextProps.canvasYStart !== this.props.canvasYStart) {
      newState.canvasYStart = nextProps.canvasYStart;
    }
    if (nextProps.yFrom !== this.props.yFrom) {
      newState.yFrom = nextProps.yFrom;
    }
    if (nextProps.yRatio !== this.props.yRatio) {
      newState.yRatio = nextProps.yRatio;
    }
    if (!plotsConfigurationsAreEqual(nextProps.plots, this.props.plots)) {
      newState.plots = nextProps.plots;
    }
    if (Object.keys(newState).length > 0) {
      this.setState(newState);
    }
  }

  renderPlot = (plot, index) => {
    if (!plot || !plot.data || !plot.data.length) {
      return null;
    }
    const {baseLine} = this.state;
    const lineParts = plot.data.map(part => part.map((coordinate, index) => {
      if (index === 0) {
        return `M ${coordinate.x},${coordinate.y}`;
      }
      return `L ${coordinate.x},${coordinate.y}`;
    }).join(' ')).join(' ');
    const areaParts = plot.data.map(part => [
      `M ${part[0].x}, ${baseLine}`,
      ...part.map(coordinate => `L ${coordinate.x},${coordinate.y}`),
      `L ${part[part.length - 1].x}, ${baseLine}`,
      'Z'
    ].join(' ')).join(' ');
    return (
      <g
        key={index}
        shapeRendering={'auto'}
      >
        <path d={areaParts} stroke={'none'} fill={plot.color} opacity={0.2} />
        <path d={lineParts} stroke={plot.color} strokeWidth={2} fill={'none'} />
        {plot.data.reduce((r, c) => ([...r, ...c]), []).map(({x, y}, index) => (
          <circle
            key={index}
            cx={x}
            cy={y}
            r={3}
            stroke={plot.color}
            fill={'white'}
            strokeWidth={2}
          />
        ))}
      </g>
    );
  };

  render () {
    const {maskIdentifier} = this.props;
    return (
      <g mask={`url(#${maskIdentifier})`}>
        {this.data.map(this.renderPlot)}
      </g>
    );
  }
}

ChartRendererWithOffset.propTypes = {
  baseLine: PropTypes.number,
  xRatio: PropTypes.number,
  yFrom: PropTypes.number,
  canvasYStart: PropTypes.number,
  yRatio: PropTypes.number,
  values: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  xMin: PropTypes.number,
  plots: PropTypes.arrayOf(PropTypes.shape({
    name: PropTypes.string,
    index: PropTypes.number,
    renderer: PropTypes.string
  })),
  maskIdentifier: PropTypes.string,
  isPercent: PropTypes.bool
};

export default inject('data', 'dataGroup', 'plot', 'timeline', 'valueAxis')(
  observer(ChartRenderer)
);
