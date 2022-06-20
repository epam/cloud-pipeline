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

import thresholdMethods from './methods';
import {
  BooleanParameter,
  FloatParameter,
  FloatRangeParameter,
  IntegerParameter,
  ListParameter
} from '../../../parameters';

const thresholdStrategies = {
  global: 'Global',
  adaptive: 'Adaptive'
};

const thresholdStrategiesParameters = [
  [ListParameter, {
    name: 'thresholdStrategy',
    parameterName: 'Threshold strategy',
    title: 'Threshold strategy',
    values: [
      thresholdStrategies.global,
      thresholdStrategies.adaptive
    ],
    value: thresholdStrategies.global
  }],
  [ListParameter, {
    name: 'thresholdingMethod',
    parameterName: 'Thresholding method',
    title: 'Thresholding method',
    values: (cpModule) => {
      if (cpModule.getParameterValue('thresholdStrategy') === thresholdStrategies.adaptive) {
        return [
          thresholdMethods.minimumCrossEntropy,
          thresholdMethods.otsu,
          thresholdMethods.robustBackground,
          thresholdMethods.sauvola
        ];
      }
      return [
        thresholdMethods.minimumCrossEntropy,
        thresholdMethods.otsu,
        thresholdMethods.robustBackground,
        thresholdMethods.measurement,
        thresholdMethods.manual
      ];
    },
    value: thresholdMethods.minimumCrossEntropy
  }]
];

const thresholdGeneralParameters = [
  [FloatParameter, {
    name: 'thresholdSmoothingScale',
    parameterName: 'Threshold smoothing scale',
    title: 'Threshold smoothing scale'
  }],
  [FloatParameter, {
    name: 'thresholdCorrectionFactor',
    parameterName: 'Threshold correction factor',
    title: 'Threshold correction factor',
    /**
     * @param {AnalysisModule} cpModule
     */
    visibilityHandler: (cpModule) => {
      return [
        thresholdMethods.minimumCrossEntropy,
        thresholdMethods.otsu,
        thresholdMethods.robustBackground,
        thresholdMethods.measurement,
        thresholdMethods.sauvola
      ].includes(cpModule.getParameterValue('thresholdingMethod'));
    }
  }],
  [FloatRangeParameter, {
    name: 'thresholdBounds',
    parameterName: 'Lower and upper bounds on threshold',
    title: 'Lower and upper bounds on threshold',
    range: {min: 0, max: 1},
    /**
     * @param {AnalysisModule} cpModule
     */
    visibilityHandler: (cpModule) => {
      return [
        thresholdMethods.minimumCrossEntropy,
        thresholdMethods.otsu,
        thresholdMethods.robustBackground,
        thresholdMethods.measurement,
        thresholdMethods.sauvola
      ].includes(cpModule.getParameterValue('thresholdingMethod'));
    }
  }],
  [IntegerParameter, {
    name: 'adaptiveSize',
    parameterName: 'Size of adaptive window',
    title: 'Size of adaptive window',
    /**
     * @param {AnalysisModule} cpModule
     */
    visibilityHandler: (cpModule) => {
      return cpModule.getParameterValue('thresholdStrategy') === thresholdStrategies.adaptive;
    }
  }],
  [BooleanParameter, {
    name: 'logTransform',
    parameterName: 'Log transform before thresholding?',
    title: 'Log transform before thresholding',
    value: false,
    /**
     * @param {AnalysisModule} cpModule
     */
    visibilityHandler: (cpModule) => {
      return [
        thresholdMethods.minimumCrossEntropy,
        thresholdMethods.otsu
      ].includes(cpModule.getParameterValue('thresholdingMethod'));
    }
  }]
];

export {
  thresholdStrategies,
  thresholdStrategiesParameters,
  thresholdGeneralParameters
};
