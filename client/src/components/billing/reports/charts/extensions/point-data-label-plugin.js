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
import {costTickFormatter} from '../../utilities';

const id = 'point-data-label';

function isNotSet (v) {
  return v === undefined || v === null;
}

const plugin = {
  id,
  afterDatasetsDraw: function (chart, ease, pluginOptions) {
    if (pluginOptions) {
      let configurations = pluginOptions;
      if (!Array.isArray(configurations)) {
        configurations = chart.config.data.datasets.map((d, i) => ({
          datasetIndex: i,
          ...pluginOptions
        }));
      }
      const labels = configurations
        .map(configuration => this.getInitialLabelConfig(chart, ease, configuration))
        .filter(Boolean);
      this.arrangeLabels(chart, ease, labels);
      this.drawLabels(chart.ctx, labels);
    }
  },
  arrangeLabels: function (chart, ease, labels) {
    if (!labels || !labels.length) {
      return;
    }
    const [
      {label: {position: currentPosition}},
      {label: {position: previousPosition}},
      quota
    ] = labels;
    const margin = 5;
    const paddingX = 5;

    if (quota) {
      const {label: {position: quotaPosition}} = quota;
      const quotaOverlapsCurrent = (
        quotaPosition.y <= currentPosition.y + margin &&
        quotaPosition.y >= currentPosition.y - currentPosition.height - margin
      ) || (
        quotaPosition.y - quotaPosition.height <= currentPosition.y + margin &&
        quotaPosition.y - quotaPosition.height >=
        currentPosition.y - currentPosition.height - margin
      );
      const quotaOverlapsPrevious = (
        quotaPosition.y <= previousPosition.y + margin &&
        quotaPosition.y >= previousPosition.y - previousPosition.height - margin
      ) || (
        quotaPosition.y - quotaPosition.height <= previousPosition.y + margin &&
        quotaPosition.y - quotaPosition.height >=
        previousPosition.y - previousPosition.height - margin
      );

      if (quotaOverlapsCurrent || quotaOverlapsPrevious) {
        const shiftLength =
          Math.max(quotaPosition.width, currentPosition.width, previousPosition.width) +
          margin * 2;
        quotaPosition.x += shiftLength * quotaPosition.direction;
        quotaPosition.labelX += shiftLength * quotaPosition.direction;
      }
    }

    const currentOverlapsPrevious = (
      currentPosition.y <= previousPosition.y + margin &&
      currentPosition.y >= previousPosition.y - previousPosition.height - margin
    ) || (
      currentPosition.y - currentPosition.height <= previousPosition.y + margin &&
      currentPosition.y - currentPosition.height >=
      previousPosition.y - previousPosition.height - margin
    );
    if (currentOverlapsPrevious) {
      if (currentPosition.direction === -1) {
        currentPosition.x = currentPosition.point.x + margin;
        currentPosition.direction = 1;
        currentPosition.labelX = currentPosition.x + paddingX;
      } else {
        previousPosition.direction = -1;
        previousPosition.x = previousPosition.point.x - margin - previousPosition.width;
        previousPosition.labelX = previousPosition.x + paddingX;
      }
    }
  },
  drawLabels: function (ctx, labels) {
    labels.forEach(label => this.drawLabel(ctx, label));
  },
  drawLabel: function (ctx, labelConfig) {
    ctx.save();
    ctx.beginPath();
    const {datasetConfig, label} = labelConfig;
    const {borderColor: stroke} = datasetConfig || {};
    const {text, position} = label;
    if (stroke) {
      ctx.strokeStyle = stroke;
    }
    ctx.lineWidth = 2;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
    ctx.rect(position.x, position.y, position.width, position.height);
    ctx.fill();
    ctx.stroke();
    ctx.lineWidth = 1;
    ctx.fillStyle = '#606060';
    ctx.fillText(text, position.labelX, position.labelY);
    ctx.restore();
  },
  getInitialLabelConfig: function (chart, ease, configuration) {
    const {datasetIndex, index} = configuration;
    if (isNotSet(datasetIndex)) {
      return null;
    }
    const dataset = chart.getDatasetMeta(datasetIndex);
    if (!dataset) {
      return null;
    }
    const {data: elements, xAxisID, yAxisID} = dataset;
    const {data, ...datasetConfig} = dataset.controller.getDataset();
    const xAxis = chart.scales[xAxisID];
    const yAxis = chart.scales[yAxisID];
    const element = elements && elements.length > index ? elements[index] : null;
    const dataItem = data && data.length > index ? data[index] : null;
    if (!element || !xAxis || !yAxis || !dataItem) {
      return null;
    }
    const ctx = yAxis.ctx;
    const globalBounds = {
      top: yAxis.top,
      bottom: yAxis.bottom,
      left: xAxis.left,
      right: xAxis.right
    };
    const labelText = costTickFormatter(dataItem.y);
    const labelWidth = ctx.measureText(labelText).width;
    const padding = {x: 5, y: 2};
    const margin = 5;
    const height = 15;
    let {x, y} = element.getCenterPoint();
    const globalCenter = (globalBounds.right + globalBounds.left) / 2.0;
    let direction;
    if (x > globalCenter) {
      // left
      x = x - margin - labelWidth - 2 * padding.x;
      direction = -1;
    } else {
      // right
      x = x + margin;
      direction = 1;
    }
    const labelPosition = {
      x: x,
      y: y - height / 2.0 - padding.y,
      width: labelWidth + padding.x * 2.0,
      height: height + 2 * padding.y,
      labelX: x + padding.x,
      labelY: y,
      point: element.getCenterPoint(),
      direction
    };
    return {
      datasetConfig,
      globalBounds,
      label: {
        text: labelText,
        position: labelPosition
      }
    };
  }
};

export {id, plugin};
