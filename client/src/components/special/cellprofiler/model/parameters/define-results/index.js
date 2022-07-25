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
/* eslint-disable max-len */

import React from 'react';
import {AnalysisTypes} from '../../common/analysis-types';
import DefineResultsRenderer from './renderer';
import {
  getMeasureObjectIntensityTargets,
  getMeasureObjectSizeTargets,
  getSpecs
} from './get-measurements-configuration';

const DefineResultsParameter = {
  name: 'configuration',
  parameterName: 'configuration',
  type: AnalysisTypes.custom,
  showTitle: false,
  valueParser: value => typeof value === 'string' ? JSON.parse(value) : value,
  renderer: (moduleParameterValue, className, style) => (
    <DefineResultsRenderer
      parameterValue={moduleParameterValue}
      className={className}
      style={style}
    />
  )
};

const ObjectSizes = (cpModule) => {
  const configuration = cpModule.getParameterValue('configuration') || [];
  const sizes = getMeasureObjectSizeTargets(cpModule.pipeline, configuration);
  if (sizes.length > 0) {
    return {
      alias: 'objectsSize',
      module: 'MeasureObjectSizeShape',
      values: {
        objects: sizes,
        zernike: false,
        advancedFeatures: false
      }
    };
  }
  return undefined;
};

const ObjectsIntensities = (cpModule) => {
  const configuration = cpModule.getParameterValue('configuration') || [];
  const intensities = getMeasureObjectIntensityTargets(cpModule.pipeline, configuration);
  return intensities.map(intensity => ({
    alias: `intensity_${intensity.image}`,
    module: 'MeasureObjectIntensity',
    values: {
      objects: intensity.objects,
      images: [intensity.image]
    }
  }));
};

const DefineResults = (cpModule) => {
  const configuration = cpModule.getParameterValue('configuration') || [];
  const specs = getSpecs(cpModule.pipeline, configuration);
  return {
    alias: 'define',
    module: 'DefineResults',
    values: {
      specs,
      grouping: ['Well', 'Timepoint']
    }
  };
};

const defineResultsSubModules = (cpModule) => {
  const configuration = cpModule.getParameterValue('configuration') || [];
  if (!configuration || !configuration.length) {
    return [];
  }
  return [
    ...ObjectsIntensities(cpModule),
    ObjectSizes(cpModule),
    DefineResults(cpModule)
  ].filter(Boolean);
};

export {
  DefineResultsParameter,
  defineResultsSubModules
};
