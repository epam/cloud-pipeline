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

function getQuotaBarGroups (chart, dataIndex) {
  const datasets = chart?.config?.data?.datasets || [];
  const quotaBarDatasets = datasets
    .filter(dataset => dataset.type === 'quota-bar')
    .filter(dataset => dataIndex === undefined || dataset.data[dataIndex]);
  return [...new Set(quotaBarDatasets.map(dataset => dataset.group))];
}

Chart.defaults['quota-bar'] = Chart.defaults.line;
Chart.controllers['quota-bar'] = Chart.controllers.line.extend({
  draw: function (ease) {
    const meta = this.getMeta();
    const {xAxisID, yAxisID} = meta;
    const chart = this.chart;
    if (xAxisID && yAxisID && chart && chart.scales[xAxisID] && chart.scales[yAxisID]) {
      const yAxisScale = chart.scales[yAxisID];
      const ctx = this.chart.ctx;
      const {index} = meta || {};
      const dataset = this.getDataset();
      const {
        data: dataItems = [],
        backgroundColor,
        borderWidth,
        borderColor,
        borderDash,
        textColor,
        showDataLabels,
        group,
        quota
      } = dataset;
      const bars = this.chart.config.data.datasets
        .map((d, i) => this.chart.getDatasetMeta(i))
        .filter(d => d.index !== index && d.type === 'bar');
      const [values] = this.chart.config.data.datasets
        .filter(dataset => dataset.type === 'quota-bar')
        .map((dataset) => dataset.data);
      if (bars.length) {
        for (let i = 0; i < dataItems.length; i++) {
          const quotaBarGroups = getQuotaBarGroups(this.chart, i);
          const quotaBarGroupsCount = quotaBarGroups.length;
          const dataItem = dataItems[i];
          if (yAxisScale.max && dataItem > yAxisScale.max) {
            continue;
          }
          let lineWidth = 1;
          if (borderWidth !== undefined) {
            lineWidth = borderWidth;
          }
          const left = Math.min(
            ...bars
              .filter(b => b.data && b.data.length > i)
              .map(b => b.data[i]._view.x - b.data[i]._view.width / 2.0)
          ) + 2;
          const right = Math.max(
            ...bars
              .filter(b => b.data && b.data.length > i)
              .map(b => b.data[i]._view.x + b.data[i]._view.width / 2.0)
          ) - 2;
          const barSize = Math.abs(right - left);
          const groupSize = quotaBarGroupsCount
            ? barSize / quotaBarGroupsCount
            : 0;
          const groupOffset = 0;// Math.min(3, Math.round(groupSize / 6));
          const groupIndex = Math.max(0, quotaBarGroups.indexOf(group));
          const baseLine = yAxisScale.getPixelForValue(0) - lineWidth / 2.0;
          const y = Math.min(
            yAxisScale.getPixelForValue(dataItem) + lineWidth / 2.0,
            baseLine
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
          const x1 = left + groupIndex * groupSize + groupOffset + lineWidth / 2.0;
          const x2 = left + (groupIndex + 1) * groupSize - groupOffset - lineWidth / 2.0;
          if (quota) {
            ctx.moveTo(x1, y);
            ctx.lineTo(x2, y);
            ctx.stroke();
          } else {
            ctx.fillStyle = backgroundColor;
            ctx.moveTo(x1, baseLine);
            ctx.lineTo(x1, y);
            ctx.lineTo(x2, y);
            ctx.lineTo(x2, baseLine);
            ctx.lineTo(x1, baseLine);
            ctx.stroke();
            ctx.fill();
          }
          if (showDataLabels) {
            const center = (x1 + x2) / 2.0;
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
