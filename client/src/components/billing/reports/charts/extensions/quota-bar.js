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

import Chart from 'chart.js';
import 'chart.js/dist/Chart.css';

Chart.defaults['quota-bar'] = Chart.defaults.line;
Chart.controllers['quota-bar'] = Chart.controllers.line.extend({
  draw: function (ease) {
    const meta = this.getMeta();
    const {xAxisID} = meta;
    const chart = this.chart;
    if (xAxisID && chart && chart.scales[xAxisID]) {
      const ctx = this.chart.ctx;
      const {data = [], index} = meta || {};
      const {
        borderWidth,
        borderColor,
        borderDash,
        textColor,
        showDataLabels
      } = this.getDataset();
      const bars = this.chart.config.data.datasets
        .map((d, i) => this.chart.getDatasetMeta(i))
        .filter(d => d.index !== index && d.type === 'bar');
      const [values] = this.chart.config.data.datasets
        .filter(dataset => dataset.type === 'quota-bar')
        .map((dataset) => dataset.data);
      if (bars.length) {
        for (let i = 0; i < data.length; i++) {
          const dataItem = data[i];
          let lineWidth = 1;
          if (borderWidth !== undefined) {
            lineWidth = borderWidth;
          }
          const left = Math.min(
            ...bars
              .filter(b => b.data && b.data.length > i)
              .map(b => b.data[i]._view.x - b.data[i]._view.width / 2.0)
          );
          const right = Math.max(
            ...bars
              .filter(b => b.data && b.data.length > i)
              .map(b => b.data[i]._view.x + b.data[i]._view.width / 2.0)
          );
          const y = Math.max(
            Math.round(lineWidth / 2.0),
            Math.round(dataItem._view.y) - Math.round(lineWidth / 2.0)
          );
          const labelViewY = y < 20
            ? y + 15
            : y - 5;
          ctx.save();
          ctx.beginPath();
          if (borderColor) {
            ctx.strokeStyle = borderColor;
          }
          if (showDataLabels) {
            ctx.fillStyle = textColor || borderColor;
          }
          ctx.lineWidth = lineWidth;
          if (borderDash) {
            ctx.setLineDash(borderDash);
          } else {
            ctx.setLineDash([]);
          }
          ctx.moveTo(left, y);
          ctx.lineTo(right, y);
          ctx.stroke();
          if (showDataLabels) {
            const center = (right - left) / 2 + left;
            ctx.font = '14px serif';
            ctx.textAlign = 'center';
            ctx.fillText(values[i], center, labelViewY);
          }
          ctx.restore();
        }
      }
    }
  }
});

Chart.defaults.global.datasets['quota-bar'] = {showLine: false};
