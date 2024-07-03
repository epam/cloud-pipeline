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

import {ObjectProperty} from './object-properties';

const PropertyFunctions = {
  mean: 'Mean',
  median: 'Median',
  stdDev: 'StdDev',
  cv: 'CV %',
  max: 'Max',
  min: 'Min',
  sum: 'Sum'
};

const PropertyFunctionNames = {
  [PropertyFunctions.mean]: 'Mean',
  [PropertyFunctions.median]: 'Median',
  [PropertyFunctions.stdDev]: 'StdDev',
  [PropertyFunctions.cv]: 'CV %',
  [PropertyFunctions.max]: 'Max',
  [PropertyFunctions.min]: 'Min',
  [PropertyFunctions.sum]: 'Sum'
};

const PropertyFunctionHints = {
  [PropertyFunctions.mean]: 'Mean value of the property',
  [PropertyFunctions.median]: 'Median value of the property',
  [PropertyFunctions.stdDev]: 'Standard deviation',
  [PropertyFunctions.cv]: 'Coefficient of variation (standard deviation divided by mean)',
  [PropertyFunctions.max]: 'Maximum value of the property',
  [PropertyFunctions.min]: 'Minimum value of the property',
  [PropertyFunctions.sum]: 'Summary of all values of the selected property'
};

function getObjectPropertyFunction (objectProperty) {
  switch (objectProperty) {
    case ObjectProperty.numberOfObjects:
    case undefined:
    case null:
      return [];
    default:
      return Object.values(PropertyFunctions);
  }
}

const AllStats = [
  PropertyFunctions.mean,
  PropertyFunctions.median,
  PropertyFunctions.stdDev,
  PropertyFunctions.cv,
  PropertyFunctions.max,
  PropertyFunctions.min,
  PropertyFunctions.sum
];

export {
  AllStats,
  PropertyFunctions,
  PropertyFunctionNames,
  PropertyFunctionHints,
  getObjectPropertyFunction
};
