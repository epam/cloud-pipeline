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

const INTERSECTION_MARGIN = 1;

function sectionsIntersects (a, b) {
  const {p1: ap1, p2: ap2} = a;
  const {p1: bp1, p2: bp2} = b;
  return Math.max(ap1, ap2, bp1, bp2) - Math.min(ap1, ap2, bp1, bp2) - 2 * INTERSECTION_MARGIN <
    Math.max(ap1, ap2) - Math.min(ap1, ap2) + Math.max(bp1, bp2) - Math.min(bp1, bp2);
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
    if (labels.length > 0) {
      const [any] = labels;
      const {top, bottom} = any.globalBounds;
      const spaces = [{
        p1: top,
        p2: bottom,
        left: true
      }, {
        p1: top,
        p2: bottom,
        left: false
      }];
      const addLabel = (boundaries) => {
        const {y, height, left} = boundaries;
        const [intersection] = spaces
          .filter(s => s.left === left && sectionsIntersects(s, {p1: y, p2: y + height}));
        if (intersection) {
          const before = {
            p1: Math.min(intersection.p1, y),
            p2: Math.min(intersection.p2, y),
            left
          };
          const after = {
            p1: Math.min(intersection.p2, y + height),
            p2: Math.max(intersection.p2, y + height),
            left
          };
          const newSpaces = [before, after].filter(({p1, p2}) => p2 - p1 > 0);
          const index = spaces.indexOf(intersection);
          spaces.splice(index, 1, ...newSpaces);
        }
      };
      for (let i = 0; i < labels.length; i++) {
        const config = labels[i];
        const {
          dataPoint,
          getLabelPosition,
          positionAvailability,
          labelHeight
        } = config.label;
        const findSpace = (left) => {
          const destinationY = dataPoint.y;
          return spaces
            .filter(s => s.left === left && (s.p2 - s.p1) >= labelHeight)
            .map(space => {
              const y = Math.max(
                Math.min(destinationY, space.p2 - labelHeight / 2.0),
                space.p1 + labelHeight / 2.0
              );
              return {
                space,
                distance: Math.abs(y - destinationY),
                position: getLabelPosition(y, left)
              };
            });
        };
        const {left, right} = positionAvailability;
        const l = left ? findSpace(true) : [];
        const r = right ? findSpace(false) : [];
        const found = [...l, ...r].sort((a, b) => a.distance - b.distance);
        const [space] = found;
        if (space) {
          addLabel(space.position);
          config.label.position = space.position;
        }
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
    const {datasetIndex} = configuration;
    let {index} = configuration;
    if (isNotSet(datasetIndex)) {
      return null;
    }
    const dataset = chart.getDatasetMeta(datasetIndex);
    if (!dataset) {
      return null;
    }
    const {type, data: elements, xAxisID, yAxisID, hidden} = dataset;
    if (hidden) {
      return null;
    }
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
    const labelText = costTickFormatter(dataItem);
    const labelWidth = ctx.measureText(labelText).width;
    const padding = {x: 5, y: 2};
    const margin = 5;
    const height = 15;
    let {x, y} = element.getCenterPoint();
    const dataPoint = {x, y};
    const leftByDefault = (globalBounds.left + globalBounds.right) / 2.0 < x;
    const labelTotalWidth = labelWidth + 2.0 * (margin + padding.x);
    const leftAvailable = x - globalBounds.left > labelTotalWidth;
    const rightAvailable = globalBounds.right - x > labelTotalWidth;
    const getLabelPosition = (yy = y, left = leftByDefault) => {
      const shift = left ? -labelTotalWidth : 0;
      return {
        x: x + margin + shift,
        y: yy - height / 2.0 - padding.y,
        width: labelWidth + padding.x * 2.0,
        height: height + padding.y * 2.0,
        labelX: x + margin + shift + padding.x,
        labelY: yy,
        left
      };
    };
    return {
      datasetConfig,
      globalBounds,
      type,
      label: {
        dataPoint,
        getLabelPosition,
        positionAvailability: {
          left: leftAvailable,
          right: rightAvailable
        },
        labelHeight: height + (margin + padding.y) * 2.0,
        text: labelText
      }
    };
  }
};

export {id, plugin};
