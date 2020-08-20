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
import {ChartRenderer, Plot} from './controls';

class CPUUsageChart extends Base {
  renderPlot (data, width, height) {
    return (
      <Plot
        width={width}
        height={height}
        data={data}
        minimum={0}
        valueFrom={0}
        {...this.plotProperties}
        plots={
          [
            {title: 'CPU Usage', name: 'cpuMax', renderer: 'cpu-usage'},
            {title: 'CPU Usage (average)', name: 'cpu', renderer: 'cpu-usage'}
          ]
        }
      >
        <ChartRenderer identifier={'cpu-usage'} />
      </Plot>
    );
  }
}

export default CPUUsageChart;
