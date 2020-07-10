/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import {costTickFormatter} from '../../utilities';

const id = 'pie-chart-data-label';

const plugin = {
  id,
  afterDatasetDraw: function (chart, dataset, pluginOptions) {
    const {valueFormatter = costTickFormatter} = pluginOptions || {};
    const {config, ctx, chartArea: area} = chart;
    const {datasets} = config.data;
    const {meta} = dataset;
    const {data = []} = meta;
    const {left, top, right, bottom} = area;
    const center = {
      x: (left + right) / 2.0,
      y: (top + bottom) / 2.0
    };
    for (let i = 0; i < data.length; i++) {
      const {_view, _index, _datasetIndex} = data[i];
      const dataElement = datasets[_datasetIndex].data[_index];
      const total = datasets[_datasetIndex].data.reduce((r, c) => r + c, 0);
      const percentage = Math.round(dataElement / total * 10000.0) / 100.0;
      const color = Array.isArray(datasets[_datasetIndex].backgroundColor)
        ? datasets[_datasetIndex].backgroundColor[_index]
        : datasets[_datasetIndex].backgroundColor;
      const {startAngle, endAngle, outerRadius, innerRadius} = _view;
      const angle = (startAngle + endAngle) / 2.0;
      const radius = Math.max(outerRadius - 10.0, (outerRadius + innerRadius) / 2.0);
      const x = center.x + radius * Math.cos(angle);
      const y = center.y + radius * Math.sin(angle);
      const arcLength = (endAngle - startAngle) * radius;
      this.drawLabel(
        ctx,
        x,
        y,
        valueFormatter(dataElement),
        `${percentage}%`,
        {
          display: (size) => size < arcLength,
          color
        }
      );
    }
  },
  drawLabel: function (ctx, x, y, label, subLabel, config) {
    const {display, color} = config || {};
    ctx.font = '9pt sans-serif';
    const {width: mainLabelWidth} = ctx.measureText(label);
    const {width: subLabelWidth} = ctx.measureText(subLabel);
    const labelWidth = Math.max(mainLabelWidth, subLabelWidth);
    const labelHeight = 13;
    const padding = {
      x: 2,
      y: 2
    };
    const renderRect = (lWidth, lHeight) => {
      ctx.save();
      ctx.beginPath();
      ctx.lineWidth = 2;
      ctx.strokeStyle = color || 'transparent';
      ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
      ctx.rect(
        x - lWidth / 2.0 - padding.x,
        y - lHeight - padding.y * 1.5,
        lWidth + 2.0 * padding.x,
        2.0 * lHeight + 3.0 * padding.y
      );
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = 'rgb(0, 0, 0)';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
    };
    if (display(labelWidth + 2.0 * padding.x)) {
      renderRect(labelWidth, labelHeight);
      ctx.fillText(label, x, y - padding.y);
      ctx.fillText(subLabel, x, y + labelHeight + padding.y);
      ctx.restore();
    } else if (display(subLabelWidth + 2.0 * padding.x)) {
      const subLabelHeight = labelHeight / 2;
      renderRect(subLabelWidth, subLabelHeight);
      ctx.fillText(subLabel, x, y + subLabelHeight + padding.y);
      ctx.restore();
    }
  }
};

export {id, plugin};
