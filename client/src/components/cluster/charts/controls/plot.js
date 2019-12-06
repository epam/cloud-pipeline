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
import {observer, Provider} from 'mobx-react';
import {AxisDataType} from './utilities';
import Timeline from './timeline';
import ValueAxis from './value-axis';
import ChartRenderer from './chart-renderer';
import Legend from './legend';
import Tooltip from './tooltip';

@observer
class Plot extends React.PureComponent {
  canvas;

  canvasRef = (element) => {
    this.canvas = element;
  };

  render () {
    const {
      data,
      dataGroup = 'default',
      dataType,
      children,
      from,
      height,
      instanceFrom,
      instanceTo,
      minimum,
      maximum,
      valueFrom,
      valueTo,
      to,
      width,
      plots,
      onRangeChanged
    } = this.props;
    const percentRenderers = (plots || []).filter(p => p.isPercent)
      .map(p => ({...p, renderer: p.renderer || 'percent-renderer'}))
      .filter(
        (plot, index, array) =>
          array.map(p => p.renderer).indexOf(plot.renderer) === index
      );
    return (
      <Provider
        plot={this}
        data={data}
      >
        <svg
          ref={this.canvasRef}
          width={width}
          height={height}
        >
          <Timeline
            from={from || instanceFrom}
            to={to || instanceTo}
            minimum={instanceFrom}
            maximum={instanceTo}
            interactiveArea={this.canvas}
            onRangeChanged={onRangeChanged}
          >
            <ValueAxis
              dataGroup={dataGroup}
              dataType={dataType}
              data={data?.data}
              minimum={minimum || 0}
              maximum={maximum}
              from={valueFrom}
              to={valueTo}
            >
              {children}
            </ValueAxis>
            {
              percentRenderers.map((plot) => (
                <ValueAxis
                  position={'right'}
                  key={plot.renderer}
                  dataType={AxisDataType.percent}
                  data={data?.data}
                  dataGroup={plot.group || 'default'}
                  from={0}
                  to={100}
                  minimum={0}
                  maximum={100}
                >
                  <ChartRenderer
                    identifier={plot.renderer}
                    isPercent
                  />
                </ValueAxis>
              ))
            }
            <Tooltip />
          </Timeline>
          <Legend />
        </svg>
      </Provider>
    );
  }
}

Plot.propTypes = {
  data: PropTypes.object,
  dataGroup: PropTypes.string,
  dataType: PropTypes.oneOf([
    AxisDataType.percent,
    AxisDataType.number,
    AxisDataType.bytes,
    AxisDataType.mBytes,
    AxisDataType.networkUsage
  ]),
  width: PropTypes.number.isRequired,
  height: PropTypes.number.isRequired,
  instanceFrom: PropTypes.number.isRequired,
  instanceTo: PropTypes.number.isRequired,
  from: PropTypes.number,
  to: PropTypes.number,
  minimum: PropTypes.number,
  maximum: PropTypes.number,
  valueFrom: PropTypes.number,
  valueTo: PropTypes.number,
  chartArea: PropTypes.shape({
    left: PropTypes.number,
    right: PropTypes.number,
    top: PropTypes.number,
    bottom: PropTypes.number
  }),
  plots: PropTypes.arrayOf(PropTypes.shape({
    name: PropTypes.string,
    group: PropTypes.string,
    renderer: PropTypes.string,
    isPercent: PropTypes.bool,
    title: PropTypes.string
  })),
  onRangeChanged: PropTypes.func
};

Plot.defaultProps = {
  dataGroup: 'default',
  dataType: AxisDataType.number,
  chartArea: {
    left: 100,
    top: 15,
    right: 75,
    bottom: 30
  }
};

export default Plot;
