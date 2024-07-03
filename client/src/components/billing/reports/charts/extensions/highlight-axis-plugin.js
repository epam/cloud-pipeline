/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

const id = 'highlight-axis-plugin';

const plugin = {
  id,
  beforeDraw: function (chart, e, configuration = {}) {
    const {highlightAxis} = configuration;
    if (!chart || highlightAxis === undefined || !configuration.backgroundColor) {
      return;
    }
    const getSegmentBounds = () => {
      const xAxisId = (chart.options.scales.xAxes[0] || {}).id;
      const yAxisId = (chart.options.scales.yAxes[0] || {}).id;
      const globalBounds = {
        top: (chart.scales[yAxisId] || {}).top || 0,
        bottom: (chart.scales[yAxisId] || {}).bottom || 0,
        left: (chart.scales[xAxisId] || {}).left || 0,
        right: (chart.scales[xAxisId] || {}).right || 0
      };
      const ticks = chart.scales[xAxisId].ticks
        .map((tick, index) => chart.scales[xAxisId].getPixelForTick(index));
      const tickCenter = ticks[highlightAxis];
      const width = highlightAxis > 0
        ? tickCenter - ticks[highlightAxis - 1]
        : (ticks[highlightAxis] - globalBounds.left) * 2;
      return {
        xFrom: tickCenter - width / 2,
        yFrom: globalBounds.top,
        height: globalBounds.bottom,
        width
      };
    };
    this.highlightTick(
      chart.ctx,
      configuration,
      getSegmentBounds()
    );
  },
  highlightTick: function (ctx, configuration, bounds) {
    ctx.save();
    ctx.globalCompositeOperation = 'destination-over';
    ctx.fillStyle = configuration.backgroundColor;
    ctx.fillRect(bounds.xFrom, bounds.yFrom, bounds.width, bounds.height);
    ctx.restore();
  }
};

export {id, plugin};
