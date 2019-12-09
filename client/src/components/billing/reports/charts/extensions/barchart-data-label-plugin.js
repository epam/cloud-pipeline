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
const id = 'barchart-data-label';

function isNotSet (v) {
  return v === undefined || v === null;
}

const plugin = {
  id,
  afterDatasetsDraw: function (chart, ease, pluginOptions) {
    if (pluginOptions && pluginOptions.showDataLabels) {
      const {
        datasetLabels = [],
        labelPosition = 'inner',
        textColor
      } = pluginOptions;
      const ctx = chart.chart.ctx;
      let colorInverse = false;
      const labelCoordinates = {};
      const getDataIndex = (data = [], label) => {
        return data.reduce((searchIndex, data, index) => {
          if (data.label === label) {
            searchIndex = index;
          }
          return searchIndex;
        }, null);
      };
      const getDatasetIndex = (dataset = []) => {
        return datasetLabels.includes(dataset.label)
          ? getDataIndex(chart.data.datasets, dataset.label)
          : null;
      };
      const getTextColor = (element, title) => {
        const borderColor = element._view.borderColor;
        const backgroundColor = element._view.backgroundColor;
        let color = textColor || borderColor;
        const partsColorDifferent = (borderColor !== backgroundColor) ||
          (backgroundColor === 'transparent');
        if (!textColor && labelPosition === 'inner' && title !== 'Billing centers') {
          color = colorInverse || partsColorDifferent
            ? element._model.borderColor
            : 'white';
        }
        if (element._options?.borderColor) {
          color = element._options.borderColor;
        }
        return color;
      };
      const getLabelsGap = (labelCoordinates, index, i) => {
        return labelCoordinates[0][index] - labelCoordinates[i][index];
      };
      const getLabelYPosition = (
        meta,
        position,
        labelsGap,
        quotaOutOfBounds
      ) => {
        const fontSize = 14;
        const padding = 5;
        const dashLineGap = 20;
        const labelsOverlapping = labelsGap > 0 && labelsGap < 40;
        const overlapArea = labelsOverlapping
          ? labelsGap
          : 0;
        const bottomBorder = meta.controller.chart.chartArea.bottom;
        const outOfBoundsArea = quotaOutOfBounds ? dashLineGap : 0;
        const bottomBorderIntersect = bottomBorder - position.y < fontSize;
        let positionY;
        if (labelPosition === 'inner' && !bottomBorderIntersect) {
          colorInverse = false;
          positionY = meta.type === 'quota-bar'
            ? position.y + fontSize - dashLineGap + padding + outOfBoundsArea
            : position.y + fontSize + padding + overlapArea + (quotaOutOfBounds && labelsGap > 0 && labelsGap < 60 && outOfBoundsArea + padding);
        } else {
          positionY = position.y - padding;
        }
        if (bottomBorderIntersect) {
          positionY = meta.type === 'quota-bar'
            ? position.y + fontSize - dashLineGap + padding + outOfBoundsArea
            : position.y - (labelsGap > -25 && labelsGap < 25 && Math.abs(labelsGap - padding - fontSize));
          colorInverse = true;
        }
        return positionY;
      };
      chart.data.datasets
        .map((dataset, i) => {
          const dataIndex = getDatasetIndex(dataset);
          if (isNotSet(dataIndex)) {
            return null;
          }
          const meta = chart.getDatasetMeta(dataIndex);
          if (meta) {
            meta.data.forEach((element, index) => {
              const title = element._chart.options.title.text;
              const dataString = costTickFormatter(dataset.data[index]);
              const position = element.tooltipPosition();
              labelCoordinates[i]
                ? labelCoordinates[i][index] = position.y
                : labelCoordinates[i] = {[index]: position.y};
              const labelsGap = getLabelsGap(labelCoordinates, index, i);
              let quotaOutOfBounds = labelCoordinates[0][index] > 0 &&
                labelCoordinates[0][index] < 30;
              const calculatedYPos = getLabelYPosition(meta, position, labelsGap, quotaOutOfBounds);
              ctx.fillStyle = getTextColor(element, title);
              ctx.font = '11px sans-serif';
              ctx.textAlign = 'center';
              ctx.textBaseline = 'bottom';
              ctx.fillText(dataString, position.x, calculatedYPos);
            });
          }
        });
    }
  }
};

export {id, plugin};
