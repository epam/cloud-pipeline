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

const id = 'data-label-plugin';
const noDataIgnoreOption = 'data-plugin-ignore';

const plugin = {
  id,
  noDataIgnoreOption,
  afterDraw: function (chart, e, configuration) {
    const {error, label} = configuration;
    if (!this.hasData(chart.data) || error || label) {
      const ctx = chart.chart.ctx;
      const width = chart.chart.width;
      const height = chart.chart.height;

      ctx.save();
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.lineWidth = 1;
      ctx.fillStyle = error ? 'rgba(255, 0, 0, 0.6)' : 'rgba(0, 0, 0, 0.6)';
      ctx.font = '9pt sans-serif';
      ctx.fillText(error || label || 'No data to display', width / 2, height / 2);
      ctx.restore();
    }
  },
  hasData: function (data) {
    if (data.datasets && data.datasets.length > 0) {
      return data.datasets
        .filter(d => !d[this.noDataIgnoreOption])
        .some(d => d.data && d.data.length > 0);
    }

    return false;
  }
};

export {id, noDataIgnoreOption, plugin};
