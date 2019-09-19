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
import {BarPlot, Plot, XAxis, YAxis} from './controls';
import {AxisDataType} from './controls/utilities';

class FileSystemUsageChart extends Base {
  renderPlot (data, width, height) {
    if (!data.data || !data.data.length) {
      return null;
    }
    const item = data.data[data.data.length - 1];
    const devices = Object.keys(item.stats);
    return (
      <Plot
        identifier={'file-system-usage-percentage'}
        data={data}
        width={width}
        height={height}
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
          max={devices.length - 0.25}
        />
        <BarPlot
          identifier={'memory usage'}
          series={
            devices.map((d, index) => ({
              name: `FS ${index + 1}: ${d}`,
              value: obj => obj.stats[d].usableSpace,
              total: obj => obj.stats[d].capacity
            }))
          }
        />
      </Plot>
    );
  }
}

export default FileSystemUsageChart;
