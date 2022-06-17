/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {
  FloatParameter, IntegerParameter,
  ListParameter
} from '../../../parameters';
import thresholdMethods from './methods';
import visibilityHandlerGenerator from './visibility-handler-generator';

export default [
  [FloatParameter, {
    name: 'lowerOutlierFraction',
    title: 'Lower outlier fraction',
    parameterName: 'Lower outlier fraction',
    visibilityHandler: visibilityHandlerGenerator(thresholdMethods.robustBackground)
  }],
  [FloatParameter, {
    name: 'upprtOutlierFraction',
    title: 'Upper outlier fraction',
    parameterName: 'Upper outlier fraction',
    visibilityHandler: visibilityHandlerGenerator(thresholdMethods.robustBackground)
  }],
  [ListParameter, {
    name: 'averagingMethod',
    title: 'Averaging method',
    parameterName: 'Averaging method',
    values: ['Mean', 'Median', 'Mode'],
    value: 'Mean',
    visibilityHandler: visibilityHandlerGenerator(thresholdMethods.robustBackground)
  }],
  [ListParameter, {
    name: 'varianceMethod',
    title: 'Variance method',
    parameterName: 'Variance method',
    values: ['Standard deviation', 'Median absolute deviation'],
    value: 'Standard deviation',
    visibilityHandler: visibilityHandlerGenerator(thresholdMethods.robustBackground)
  }],
  [IntegerParameter, {
    name: 'numberOfDeviations',
    title: '# of deviations',
    parameterName: '# of deviations',
    visibilityHandler: visibilityHandlerGenerator(thresholdMethods.robustBackground)
  }]
];
