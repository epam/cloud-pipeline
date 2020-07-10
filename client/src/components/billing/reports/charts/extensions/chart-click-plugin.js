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

const id = 'chart-click-plugin';

function mouseOverElement (mouse, element) {
  if (!element) {
    return false;
  }
  const {x, y} = mouse;
  const {top, bottom, left, right} = element;
  return top <= y && y <= bottom && left <= x && x <= right;
}

const plugin = {
  id,
  beforeEvent: function (chart, event, configuration) {
    const {pie} = configuration;
    if (pie) {
      return this.beforeEventPie(chart, event, configuration);
    }
    const {axis, handler, scaleHandler} = configuration;
    const {x, y, type} = event;
    const {scales} = chart;
    let scaleHovered = false;
    if (mouseOverElement(event, scales[axis])) {
      const {top} = scales[axis];
      const {highest} = scales[axis]._getLabelSizes();
      const {height, offset} = highest;
      if (y > height + offset + top) {
        scaleHovered = true;
      }
    }
    if (/^click$/i.test(type)) {
      if (scaleHovered && scaleHandler) {
        scaleHandler();
        return;
      }
      if (handler) {
        const value = scales[axis].getValueForPixel(x);
        if (value >= 0) {
          handler(value);
        }
      }
    } else if (/^mousemove$/i.test(type) && scaleHovered && scaleHandler) {
      // disable tooltip
      return false;
    }
    return true;
  },
  beforeEventPie: function (chart, event, configuration) {
    const {handler} = configuration;
    const {x, y, type} = event;
    const {config, chartArea: area, id} = chart;
    const {datasets} = config.data;
    const {left, top, right, bottom} = area;
    const center = {
      x: (left + right) / 2.0,
      y: (top + bottom) / 2.0
    };
    const dx = x - center.x;
    const dy = y - center.y;
    let angle = Math.atan2(dy, dx);
    if (angle < -Math.PI / 2.0) {
      angle += (2.0 * Math.PI);
    }
    const radius = Math.sqrt(Math.pow(dx, 2.0) + Math.pow(dy, 2.0));
    if (/^click$/i.test(type) && handler) {
      for (let i = 0; i < (datasets || []).length; i++) {
        const {_meta} = datasets[i];
        if (_meta && _meta[id]) {
          const {data} = _meta[id];
          for (let d = 0; d < (data || []).length; d++) {
            const {_view} = data[d];
            const {startAngle, endAngle, outerRadius, innerRadius} = _view;
            if (
              angle >= startAngle &&
              angle <= endAngle &&
              radius >= innerRadius &&
              radius <= outerRadius
            ) {
              handler(d);
            }
          }
        }
      }
      return;
    }
    return true;
  }
};

export {id, plugin};
