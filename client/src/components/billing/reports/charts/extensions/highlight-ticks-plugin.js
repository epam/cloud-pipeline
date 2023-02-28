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

const id = 'highlight-ticks-plugin';

const plugin = {
  id,
  beforeDraw: function (chart, e, configuration) {
    const {
      axis = 'x-axis',
      highlightTickFn,
      request
    } = configuration;
    if (!chart || typeof highlightTickFn !== 'function') {
      return;
    }
    const ticks = ((chart.scales || {})[axis] || {})._ticks;
    if (ticks && ticks.length) {
      for (const tick of ticks) {
        const storage = (request || {}).value;
        const value = (storage || {})[tick.value];
        tick.major = highlightTickFn(value, tick);
      }
    }
  }
};

export {id, plugin};
