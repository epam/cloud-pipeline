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
import {LinePlot, Plot, XAxis, YAxis} from './controls';
import {AxisDataType} from './controls/utilities';

class CPUUsageChart extends Base {
  renderPlot (data, width, height) {
    return (
      <Plot
        identifier={'cpu-usage'}
        data={data}
        width={width}
        height={height}
        {...this.plotProperties}
      >
        <XAxis
          axisDataType={AxisDataType.date}
          {...this.xAxisProperties}
        />
        <YAxis
          min={0}
          start={0}
        />
        <LinePlot
          name={'CPU Usage'}
          tooltip={item => Math.round(item.y * 10000) / 10000.0}
        />
      </Plot>
    );
  }
}

export default CPUUsageChart;
