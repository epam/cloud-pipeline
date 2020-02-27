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
  afterEvent: function (chart, event, configuration) {
    const {axis, handler, scaleHandler} = configuration;
    const {x, y, type} = event;
    const {scales} = chart;
    if (/^click$/i.test(type)) {
      if (mouseOverElement(event, scales[axis])) {
        const {top} = scales[axis];
        const {highest} = scales[axis]._getLabelSizes();
        const {height, offset} = highest;
        if (scaleHandler && y > height + offset + top) {
          scaleHandler();
          return;
        }
      }
      if (handler) {
        const value = scales[axis].getValueForPixel(x);
        if (value >= 0) {
          handler(value);
        }
      }
    }
  }
};

export {id, plugin};
