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
import {AxisDataType} from './controls/utilities';
import {ChartRenderer, Plot, UsagePlot, formatters} from './controls';

class MemoryUsageChart extends Base {
  renderPlot (data, width, height) {
    const barPlotHeight = 80;
    return (
      <div
        style={{width, height: height, display: 'flex', flexDirection: 'column'}}
      >
        <Plot
          width={width}
          height={height - barPlotHeight}
          data={data}
          {...this.plotProperties}
          plots={[{
            name: 'memoryMax', renderer: 'memory-usage', group: 'default', title: 'MB used (max)'
          }, {
            name: 'percentMax', isPercent: true, group: 'percent', title: 'MB used (%, max)'
          }, {
            name: 'memory', renderer: 'memory-usage', group: 'default', title: 'MB used (average)'
          }, {
            name: 'percent', isPercent: true, group: 'percent', title: 'MB used (%, average)'
          }]}
          dataType={AxisDataType.mBytes}
        >
          <ChartRenderer identifier={'memory-usage'} />
        </Plot>
        <UsagePlot
          width={width}
          height={barPlotHeight}
          data={data}
          config={[{
            group: 'capacity',
            value: 'usage',
            total: 'capacity',
            formatter: formatters.memoryUsage
          }]}
        />
      </div>
    );
  }
}

export default MemoryUsageChart;
