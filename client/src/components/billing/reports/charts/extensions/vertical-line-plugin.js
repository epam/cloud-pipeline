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

const id = 'vertical-line';

const plugin = {
  id,
  beforeDatasetsDraw: function (chart, ease, pluginOptions) {
    if (pluginOptions) {
      const {
        color = 'rgb(83, 157, 210)',
        dash = 4,
        width = 1,
        index,
        time
      } = pluginOptions;
      const horizontalScales = Object.values(chart.scales)
        .filter(s => ['left', 'right'].indexOf(s.position) >= 0);
      let top, bottom;
      if (horizontalScales.length > 0) {
        top = Math.min(...horizontalScales.map(s => s.top));
        bottom = Math.max(...horizontalScales.map(s => s.bottom));
      }
      let x;
      if (time) {
        const [timeScale] = Object.values(chart.scales).filter(s => s.type === 'time');
        if (timeScale) {
          x = timeScale.getPixelForOffset(time);
        }
      }
      if (!x && index) {
        const meta = chart.getDatasetMeta(0);
        const data = meta.data;
        x = data[pluginOptions.index]._model.x;
      }
      if (x && top !== undefined && bottom !== undefined) {
        const context = chart.chart.ctx;
        context.save();
        context.beginPath();
        context.lineWidth = width;
        context.setLineDash([dash, dash]);
        context.strokeStyle = color;
        context.moveTo(x, top);
        context.lineTo(x, bottom);
        context.stroke();
        context.restore();
      }
    }
  }
};

export {id, plugin};
