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
import Base from './base';
import {
  BarPlot,
  Legend,
  LinePlot,
  Plot,
  XAxis,
  YAxis,
  Tooltip
} from './controls';
import {
  AxisDataType,
  AxisPosition
} from './controls/utilities';

class MemoryUsageChart extends Base {
  renderPlot (data, width, height) {
    const barPlotHeight = 80;
    return (
      <div
        style={{width, height: height, display: 'flex', flexDirection: 'column'}}
      >
        <Plot
          identifier={'memory-usage'}
          data={data}
          width={width}
          height={height - barPlotHeight}
          {...this.plotProperties}
        >
          <XAxis
            axisDataType={AxisDataType.date}
            {...this.xAxisProperties}
          />
          <YAxis
            axisDataType={AxisDataType.mBytes}
            identifier={'memory usage'}
          />
          <YAxis
            identifier={'memory percent'}
            axisDataType={AxisDataType.percent}
            position={AxisPosition.right}
            min={0}
            start={0}
            end={100}
            max={100}
            ticks={4}
          />
          <LinePlot
            identifier={'usage'}
            name={'MB used'}
            yAxis={'memory usage'}
          />
          <LinePlot
            identifier={'percent'}
            name={'MB used (%)'}
            dataField={'percent'}
            yAxis={'memory percent'}
          />
          <Legend />
        </Plot>
        <Plot
          identifier={'memory-usage-percentage'}
          data={data}
          width={width}
          height={barPlotHeight}
          {...this.plotProperties}
          rangeChangeEnabled={false}
        >
          <XAxis
            axisDataType={AxisDataType.percent}
            visible={false}
            start={0}
            end={100}
          />
          <YAxis
            ticksVisible={false}
            min={-0.75}
            max={0.75}
          />
          <BarPlot
            identifier={'memory usage'}
            series={[
              {
                value: 'usage',
                total: 'capacity'
              }
            ]}
          />
        </Plot>
      </div>
    );
  }
}

export default MemoryUsageChart;
