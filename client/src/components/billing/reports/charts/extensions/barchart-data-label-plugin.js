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
    const {valueFormatter = costTickFormatter} = pluginOptions || {};
    const ctx = chart.chart.ctx;
    const {
      scales = {}
    } = chart;
    const datasetLabels = chart.data.datasets
      .map((dataset, i) => {
        const meta = chart.getDatasetMeta(i);
        const {
          yAxisID
        } = meta;
        const yAxis = scales[yAxisID];
        const {
          hidden = false,
          showDataLabel: showLabel = !hidden,
          data: items = []
        } = dataset;
        const showDatasetLabel = (index) => {
          if (typeof showLabel === 'function') {
            const allData = chart.data.datasets.map((aDataset) => ({
              dataset: aDataset,
              value: (aDataset.data || [])[index]
            }));
            return showLabel(
              items[index],
              allData,
              {
                yAxis,
                dataset,
                getPxValue (aValue) {
                  return yAxis
                    ? yAxis.getPixelForValue(aValue)
                    : undefined;
                },
                getPxSize (aValue) {
                  return yAxis
                    ? (yAxis.bottom - yAxis.getPixelForValue(aValue))
                    : undefined;
                },
                pxValue: yAxis ? yAxis.getPixelForValue(items[index]) : undefined,
                minPxValue: yAxis ? yAxis.getPixelForValue(yAxis.min) : undefined,
                pxSize: yAxis ? (yAxis.bottom - yAxis.getPixelForValue(items[index])) : undefined
              }
            );
          }
          return showLabel;
        };
        if (meta) {
          return meta.data.map((element, index) => {
            const show = showDatasetLabel(index);
            if (show) {
              return this.getInitialLabelConfig(
                dataset,
                element,
                index,
                meta,
                chart,
                valueFormatter
              );
            }
            return undefined;
          }).filter(Boolean);
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
          dataset
        } = config;
        const {
          dataPoint,
          getLabelPosition,
          labelHeight
        } = config.label;
        const {
          details = false
        } = dataset || {};
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
                above: y < destinationY,
                position: getLabelPosition(y)
              };
            });
        };
        const found = findSpace()
          .filter((info) => !details || !info.above)
          .sort((a, b) => a.distance - b.distance);
        const [space] = found;
        if (space) {
          addLabel(space.position);
          config.label.position = space.position;
          config.label.visible = true;
        } else {
          config.label.visible = false;
        }
      }
    }
  },
  drawLabel: function (ctx, label) {
    const {dataset = {}, label: config} = label;
    if (config && config.visible) {
      const {
        borderColor = 'black',
        textBold = false,
        textColor,
        flagColor,
        showFlag = true
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
          text,
          position.x + position.width / 2.0, position.y + position.height
        );
        if (flagColor && showFlag) {
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
    valueFormatter
  ) {
    const {
      data,
      textBold = false,
      dataItemTitle = ''
    } = dataset;
    const {xAxisID, yAxisID} = meta;
    const xAxis = chart.scales[xAxisID];
    const yAxis = chart.scales[yAxisID];
    const dataItem = data && data.length > index ? data[index] : null;
    if (!element || !xAxis || !yAxis || isNotSet(dataItem)) {
      return null;
    }
    const ctx = yAxis.ctx;
    const {
      stacked = false
    } = yAxis.options || {};
    const globalBounds = {
      top: yAxis.top,
      bottom: yAxis.bottom,
      left: xAxis.left,
      right: xAxis.right
    };
    const getLabelXY = () => {
      let currentLabelY = yAxis.getPixelForValue(dataItem);
      if (stacked) {
        const datasetIndex = (chart.data.datasets || []).indexOf(dataset);
        const datasets = (chart.data.datasets || [])
          .filter((aDataset, index) => index < datasetIndex)
          .filter((aDataset) => aDataset === dataset ||
            (!!aDataset.stack && aDataset.stack === dataset.stack)
          );
        const valuesBefore = datasets
          .map((aDataset) => (aDataset.data || [])[index] || 0)
          .reduce((r, c) => r + c, 0);
        currentLabelY = yAxis.getPixelForValue(dataItem + valuesBefore);
      }
      return {
        x: element.getCenterPoint().x,
        y: currentLabelY
      };
    };
    let labelText = valueFormatter(dataItem);
    if (dataItemTitle && dataItemTitle.length) {
      labelText = `${dataItemTitle}: ${labelText}`;
    }
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
