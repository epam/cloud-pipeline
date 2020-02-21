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

import Chart from 'chart.js';
import 'chart.js/dist/Chart.css';

const SummaryChart = {
  current: 'summary-current',
  previous: 'summary-previous',
  quota: 'summary-quota'
};

Chart.defaults.summary = Chart.defaults.line;
Chart.defaults[SummaryChart.current] = Chart.defaults.summary;
Chart.defaults[SummaryChart.previous] = Chart.defaults.summary;
Chart.defaults[SummaryChart.quota] = Chart.defaults.summary;
Chart.controllers.summary = Chart.controllers.line.extend({});
Chart.controllers[SummaryChart.current] = Chart.controllers.summary.extend({});
Chart.controllers[SummaryChart.previous] = Chart.controllers.summary.extend({});
Chart.controllers[SummaryChart.quota] = Chart.controllers.summary.extend({});

export default SummaryChart;
