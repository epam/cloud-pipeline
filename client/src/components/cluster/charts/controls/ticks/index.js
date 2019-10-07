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

import {AxisDataType} from '../utilities';
import defaultTicksGenerator from './default-ticks-generator';
import percentTicksGenerator from './percent-ticks-generator';
import * as formatters from '../utilities/formatters';

export default {
  [AxisDataType.networkUsage]: (start, end, canvasSize) =>
    defaultTicksGenerator(start, end, canvasSize, 2, 3, formatters.networkUsage),
  [AxisDataType.bytes]: (start, end, canvasSize) =>
    defaultTicksGenerator(start, end, canvasSize, 2, 3, formatters.memoryUsage),
  [AxisDataType.mBytes]: (start, end, canvasSize) =>
    defaultTicksGenerator(
      start,
      end,
      canvasSize,
      2,
      3,
      o => formatters.memoryUsage(o * 1024 * 1024)
    ),
  [AxisDataType.percent]: percentTicksGenerator,
  default: defaultTicksGenerator
};
