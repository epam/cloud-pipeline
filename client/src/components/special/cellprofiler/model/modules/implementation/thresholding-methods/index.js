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
  thresholdStrategies,
  thresholdStrategiesParameters as strategyParameters,
  thresholdGeneralParameters as generalParameters
} from './strategies';
import otsu from './otsu';
import robustBackground from './robust-background';
import measurement from './measurement';
import manual from './manual';

function joinVisibilityCriteria (...criteria) {
  return (cpModule) => !(criteria.find(cr => typeof cr === 'function' ? !cr(cpModule) : false));
}

/**
 * @param {[*, ModuleParameterOptions][]} configuration
 * @param {function(AnalysisModule)} [predefinedCriteria]
 * @returns {function(boolean, ...function(AnalysisModule)): ModuleParameter[]}
 */
function build (configuration, ...predefinedCriteria) {
  return function parameters (advanced, ...criteria) {
    return configuration.map(([ParameterClass, options]) => new ParameterClass({
      ...options,
      advanced: options.advanced === undefined ? advanced : options.advanced,
      visibilityHandler: joinVisibilityCriteria(
        options.visibilityHandler,
        ...criteria,
        ...predefinedCriteria
      )
    }));
  };
}

const otsuParameters = build(otsu);
const manualParameters = build(
  manual,
  (cpModule) => cpModule.getParameterValue('thresholdStrategy') === thresholdStrategies.global
);
const robustBackgroundParameters = build(robustBackground);
const measurementParameters = build(measurement);

const thresholdStrategyParameters = build(strategyParameters);
const thresholdGeneralParameters = build(generalParameters);

/**
 * @param {boolean} advanced
 * @param {function(AnalysisModule)} criteria
 * @returns {ModuleParameter[]}
 */
function thresholdConfiguration (advanced = true, ...criteria) {
  return [
    ...thresholdStrategyParameters(advanced, ...criteria),
    ...otsuParameters(advanced, ...criteria),
    ...robustBackgroundParameters(advanced, ...criteria),
    ...measurementParameters(advanced, ...criteria),
    ...manualParameters(advanced, ...criteria),
    ...thresholdGeneralParameters(advanced, ...criteria)
  ];
}

export {
  thresholdMethods,
  thresholdStrategies,
  otsuParameters,
  robustBackgroundParameters,
  measurementParameters,
  manualParameters,
  thresholdStrategyParameters,
  thresholdGeneralParameters,
  thresholdConfiguration
};
