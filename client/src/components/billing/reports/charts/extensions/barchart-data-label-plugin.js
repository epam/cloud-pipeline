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

import {costTickFormatter} from '../../utilities';
import {sectionsIntersects} from './utilities/label-positioning';

const id = 'barchart-data-label';

function isNotSet (v) {
  return v === undefined || v === null;
}

const plugin = {
  id,
  afterDatasetsDraw: function (chart, ease, pluginOptions) {
    const {valueFormatter = costTickFormatter, chartType} = pluginOptions || {};
    const ctx = chart.chart.ctx;
    const datasetLabels = chart.data.datasets
      .map((dataset, i) => {
        const meta = chart.getDatasetMeta(i);
        const {showDataLabel = true} = dataset;
        if (meta && showDataLabel) {
          return meta.data.map((element, index) => this.getInitialLabelConfig(
            dataset,
            element,
            index,
            meta,
            chart,
            valueFormatter,
            chartType
          ));
        }
        return [];
      });
    const dataLabels = [];
    for (let i = 0; i < datasetLabels.length; i++) {
      const data = datasetLabels[i];
      for (let j = 0; j < data.length; j++) {
        if (dataLabels.length <= j) {
          dataLabels.push([]);
        }
        const labels = dataLabels[j];
        if (data[j]) {
          labels.push(data[j]);
        }
      }
    }
    dataLabels.forEach(labels => this.arrangeLabels(labels));
    dataLabels.forEach(labels => labels.forEach(label => this.drawLabel(ctx, label)));
  },
  arrangeLabels: function (labels) {
    if (!labels || !labels.length) {
      return;
    }
    if (labels.length > 0) {
      const [any] = labels;
      const {top, bottom} = any.globalBounds;
      const spaces = [{
        p1: top,
        p2: bottom
      }];
      const addLabel = (boundaries) => {
        const {y, height} = boundaries;
        const [intersection] = spaces
          .filter(s => sectionsIntersects(s, {p1: y, p2: y + height}));
        if (intersection) {
          const before = {
            p1: Math.min(intersection.p1, y),
            p2: Math.min(intersection.p2, y)
          };
          const after = {
            p1: Math.min(intersection.p2, y + height),
            p2: Math.max(intersection.p2, y + height)
          };
          const newSpaces = [before, after].filter(({p1, p2}) => p2 - p1 > 0);
          const index = spaces.indexOf(intersection);
          spaces.splice(index, 1, ...newSpaces);
        }
      };
      labels
        .map(({label}) => label)
        .filter(Boolean)
        .map(({dataPoint}) => dataPoint)
        .filter(Boolean)
        .map(({y}) => ({y, height: 2}))
        .forEach(addLabel);
      for (let i = 0; i < labels.length; i++) {
        const config = labels[i];
        const {
          dataPoint,
          getLabelPosition,
          labelHeight
        } = config.label;
        const findSpace = () => {
          const destinationY = dataPoint.y - labelHeight / 2.0;
          return spaces
            .filter(s => (s.p2 - s.p1) >= labelHeight)
            .map(space => {
              const y = Math.max(
                Math.min(destinationY, space.p2 - labelHeight / 2.0),
                space.p1 + labelHeight / 2.0
              );
              return {
                space,
                distance: Math.abs(y - destinationY),
                position: getLabelPosition(y)
              };
            });
        };
        const found = findSpace().sort((a, b) => a.distance - b.distance);
        const [space] = found;
        if (space) {
          addLabel(space.position);
          config.label.position = space.position;
        }
      }
    }
  },
  drawLabel: function (ctx, label) {
    const {dataset = {}, label: config} = label;
    if (config) {
      const {
        borderColor = 'black',
        textBold = false,
        textColor,
        flagColor,
        dataLabelText
      } = dataset;
      const color = textColor || borderColor;
      const {position, text} = config;
      if (position) {
        ctx.save();
        ctx.fillStyle = color;
        ctx.font = `${textBold ? 'bold ' : ''}9pt sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'bottom';
        ctx.fillText(
          `${dataLabelText || ''}${text}`,
          position.x + position.width / 2.0, position.y + position.height
        );
        if (flagColor && !dataLabelText) {
          ctx.beginPath();
          ctx.arc(
            position.x,
            position.y + position.height / 2.0,
            2.5,
            0,
            Math.PI * 2,
            true
          );
          ctx.fillStyle = flagColor;
          ctx.fill();
        }
      }
    }
  },
  getInitialLabelConfig: function (
    dataset,
    element,
    index,
    meta,
    chart,
    valueFormatter,
    chartType
  ) {
    const {
      data,
      hidden,
      textBold = false,
      showDataLabel = false
    } = dataset;
    const {xAxisID, yAxisID} = meta;
    if (hidden && !showDataLabel) {
      return null;
    }
    const xAxis = chart.scales[xAxisID];
    const yAxis = chart.scales[yAxisID];
    const dataItem = data && data.length > index ? data[index] : null;
    if (!element || !xAxis || !yAxis || isNotSet(dataItem)) {
      return null;
    }
    const ctx = yAxis.ctx;
    const globalBounds = {
      top: yAxis.top,
      bottom: yAxis.bottom,
      left: xAxis.left,
      right: xAxis.right
    };
    const getLabelXY = () => {
      const visibleOnlyLabel = hidden && showDataLabel;
      let currentLabelY = yAxis.getPixelForValue(dataItem);
      if (visibleOnlyLabel) {
        const padding = (globalBounds.bottom - globalBounds.top) * 0.1;
        currentLabelY = yAxis.getPixelForValue(data[index]) - padding;
      }
      let offset = 0;
      if (chartType === 'stacked') {
        const datasetIndex = element._datasetIndex;
        if (datasetIndex > 0 && !visibleOnlyLabel) {
          for (let i = 0; i < datasetIndex; i++) {
            const dataset = chart.data.datasets[i];
            const prevData = (dataset || {}).data || [];
            const prevLabelHeight = globalBounds.bottom - yAxis
              .getPixelForValue(prevData[index] || 0);
            offset += prevLabelHeight;
          }
        }
      }
      return {
        x: element.getCenterPoint().x,
        y: currentLabelY - offset
      };
    };
    const labelText = valueFormatter(dataItem);
    ctx.font = `${textBold ? 'bold ' : ''}9pt sans-serif`;
    const {width: labelWidth} = ctx.measureText(labelText);
    const padding = {x: 5, y: 2};
    const margin = 3;
    const labelHeight = 10;
    const {x, y} = getLabelXY();
    const labelTotalWidth = labelWidth + 2.0 * (margin + padding.x);
    const labelTotalHeight = labelHeight + 2.0 * (margin + padding.y);
    const getLabelPosition = (yy = y) => {
      return {
        x: x - labelTotalWidth / 2.0,
        y: yy - labelTotalHeight / 2.0 + padding.y + margin,
        width: labelTotalWidth,
        height: labelHeight + padding.y * 2.0
      };
    };
    return {
      dataset,
      globalBounds,
      label: {
        dataPoint: {x, y},
        getLabelPosition,
        labelHeight: labelTotalHeight,
        text: labelText
      }
    };
  }
};

export {id, plugin};
