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

import SummaryChart from './summary-chart';
import './previous-line-bar';
import './quota-bar';
import * as BarchartDataLabelPlugin from './barchart-data-label-plugin';
import * as ChartClickPlugin from './chart-click-plugin';
import * as DataLabelPlugin from './data-label-plugin';
import * as GenerateImagePlugin from './generate-image-plugin';
import * as PieChartDataLabelPlugin from './pie-chart-data-label-plugin';
import * as PointDataLabelPlugin from './point-data-label-plugin';
import * as VerticalLinePlugin from './vertical-line-plugin';
import * as HighlightTicksPlugin from './highlight-ticks-plugin';
import * as HighlightAxisPlugin from './highlight-axis-plugin';

export {
  BarchartDataLabelPlugin,
  ChartClickPlugin,
  DataLabelPlugin,
  GenerateImagePlugin,
  PieChartDataLabelPlugin,
  PointDataLabelPlugin,
  VerticalLinePlugin,
  SummaryChart,
  HighlightTicksPlugin,
  HighlightAxisPlugin
};
