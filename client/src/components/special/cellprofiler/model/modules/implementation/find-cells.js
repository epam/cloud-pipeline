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
import {thresholding, getParentParameter} from './common';
import {AnalysisTypes} from '../../common/analysis-types';
import ImageMathImages, {
  SINGLE_FILE_OPERATIONS,
  VALUE_SUPPORTED_OPERATIONS
} from '../../parameters/image-selectors/image-math';

const {
  parameters: thresholdingParameters,
  values: thresholdingValues
} = thresholding({
  strategy: 'Adaptive',
  thresholdingMethod: 'Otsu',
  otsuMethodType: 'Three classes',
  otsuThreePixels: 'Foreground',
  condition: 'configureThresholdCombine==true AND mainMethod!="Suppress features"',
  prefix: 'main_'
});

const {
  parameters: thresholdingParametersSuppress,
  values: thresholdingValuesSuppress
} = thresholding({
  strategy: 'Global',
  thresholdingMethod: 'Minimum Cross-Entropy',
  thresholdSmoothingScale: 0,
  condition: 'configureThresholdSuppress==true AND mainMethod=="Suppress features"',
  prefix: 'suppress_'
});

const findCells = {
  name: 'FindCells',
  composed: true,
  parameters: [
    'Nuclei objects|object|ALIAS nuclei|REQUIRED|DEFAULT_FROM FindNuclei',
    'Image|file|ALIAS input|IF multipleChannels==false OR mainMethod!=="Combine channels"|REQUIRED',
    'Objects name|string|Cells|ALIAS name|REQUIRED',
    'Method|[Default,Suppress features,Combine channels]|Default|ALIAS mainMethod',
    'Feature size|units|10|IF mainMethod=="Suppress features"|ALIAS featureSize',
    'Configure threshold|flag|false|IF mainMethod=="Default" OR mainMethod=="A" OR mainMethod=="Combine channels" OR mainMethod=="B"|ALIAS configureThresholdCombine|ADVANCED',
    'Configure threshold|flag|false|IF mainMethod=="Suppress features"|ALIAS configureThresholdSuppress|ADVANCED',
    'Use multiple channels|flag|false|ADVANCED|ALIAS multipleChannels|IF mainMethod=="Combine channels"',
    'Erode shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|ALIAS erodeShape|IF erode==true',
    'Erode size|integer|1|ALIAS erodeSize|IF erode==erode',
    {
      name: 'inputImage',
      hidden: true,
      /**
       * @param {AnalysisModule} cpModule
       */
      computed: (cpModule) => {
        const method = cpModule.getParameterValue('mainMethod');
        switch (method) {
          case 'B':
          case 'Combine channels':
            return `combined input ${cpModule.id}`;
          default:
            return cpModule.getParameterValue('input');
        }
      }
    },

    'imageMathOperation|string|Add|HIDDEN|COMPOSED',
    {
      name: 'inputs',
      title: 'Channels to combine',
      parameterName: 'images',
      type: AnalysisTypes.custom,
      /**
       * @param {AnalysisModule} cpModule
       * @returns {*[]|{name: *, value: number}[]}
       */
      value: (cpModule) => {
        if (!cpModule) {
          return [];
        }
        return cpModule.channels.map(name => ({
          name,
          value: 1
        }));
      },
      valueParser: value => value,
      valueFormatter: (value, cpModule) => {
        const operation = cpModule ? cpModule.getParameterValue('imageMathOperation') : 'Add';
        const multiplySupported = VALUE_SUPPORTED_OPERATIONS.includes(operation);
        const images = (value || [])
          .filter(({name}) => !!name)
          .map(({name, value}) => ({
            type: 'Image',
            value: name,
            factor: multiplySupported ? value : 1
          }));
        if (SINGLE_FILE_OPERATIONS.includes(operation)) {
          return images.slice(0, 1);
        }
        return images;
      },
      renderer: (moduleParameterValue, className, style) => (
        <ImageMathImages
          parameterValue={moduleParameterValue}
          className={className}
          style={style}
        />
      ),
      visibilityHandler: (module) => module &&
        module.getBooleanParameterValue('multipleChannels') &&
        (
          module.getParameterValue('mainMethod') === 'Combine channels' ||
          module.getParameterValue('mainMethod') === 'B'
        )
    },
    ...thresholdingParameters,
    ...thresholdingParametersSuppress,

    // Remove holes
    'Holes size|units|10|ALIAS holesSize|ADVANCED|IF mainMethod=="Combine channels" OR mainMethod=="B"'
  ],
  output: 'name|object',
  sourceImageParameter: 'inputImage',
  subModules: (parentModule) => {
    const mainMethod = parentModule.getParameterValue('mainMethod') || 'Default';
    if (['a', 'default'].includes(mainMethod.toLowerCase())) {
      return [
        {
          alias: 'secondary',
          module: 'IdentifySecondaryObjects',
          values: {
            input: '{parent.input}|COMPUTED',
            inputObjects: '{parent.nuclei}|COMPUTED',
            name: '{parent.name}|COMPUTED',
            'Select the method to identify the secondary objects': 'Watershed - Image',
            ...thresholdingValues
          }
        }
      ];
    }
    if (['suppress features'].includes(mainMethod.toLowerCase())) {
      return [
        {
          alias: 'suppress',
          module: 'EnhanceOrSuppressFeatures',
          values: {
            input: '{parent.input}|COMPUTED',
            output: '{this.id}_suppressed|COMPUTED',
            operation: 'Suppress',
            featureSize: '{parent.featureSize}|COMPUTED'
          }
        },
        {
          alias: 'secondary',
          module: 'IdentifySecondaryObjects',
          values: {
            input: '{suppress.output}|COMPUTED',
            inputObjects: '{parent.nuclei}|COMPUTED',
            name: '{parent.name}|COMPUTED',
            'Select the method to identify the secondary objects': 'Watershed - Image',
            ...thresholdingValuesSuppress
          }
        }
      ];
    }
    const erode = parentModule.getBooleanParameterValue('erode');
    let nucleiSeeds;
    const prepareNucleiModules = [];
    if (erode) {
      prepareNucleiModules.push(
        {
          alias: 'erodeNuclei',
          module: 'ErodeObjects',
          values: {
            input: '{parent.nuclei}|COMPUTED',
            output: '{this.id}_nuclei_eroded|COMPUTED',
            shape: '{parent.erodeShape}|COMPUTED',
            size: '{parent.erodeSize}|COMPUTED'
          }
        }
      );
      prepareNucleiModules.push({
        alias: 'convertNuclei',
        module: 'ConvertObjectsToImage',
        values: {
          input: '{erodeNuclei.output}|COMPUTED',
          output: '{this.id}_nuclei_seeds|COMPUTED',
          format: 'uint16'
        }
      });
      nucleiSeeds = '{convertNuclei.output}|COMPUTED';
    } else {
      prepareNucleiModules.push({
        alias: 'convertNuclei',
        module: 'ConvertObjectsToImage',
        values: {
          input: '{parent.nuclei}|COMPUTED',
          output: '{this.id}_nuclei_seeds|COMPUTED',
          format: 'uint16'
        }
      });
      nucleiSeeds = '{convertNuclei.output}|COMPUTED';
    }
    return [
      ...prepareNucleiModules,
      {
        alias: 'math1',
        module: 'ImageMath',
        values: {
          images: (cpModule, modules) => {
            if (!modules) {
              return [];
            }
            const parent = modules.parent;
            if (!parent) {
              return [];
            }
            const multipleChannels = `${getParentParameter(modules, 'multipleChannels')}` === 'true';
            if (!multipleChannels) {
              const input = getParentParameter(modules, 'input');
              return [{
                name: input,
                value: 1,
                type: 'Image'
              }];
            }
            return getParentParameter(modules, 'inputs');
          },
          output: '{this.id}_imageMath|COMPUTED',
          operation: '{parent.imageMathOperation}|COMPUTED'
        }
      },
      {
        alias: 'rescale',
        module: 'RescaleIntensity',
        values: {
          input: '{math1.output}|COMPUTED',
          output: '{this.id}_imageMath_rescaled|COMPUTED'
        }
      },
      {
        alias: 'threshold',
        module: 'Threshold',
        values: {
          input: '{rescale.output}|COMPUTED',
          output: '{this.id}_threshold|COMPUTED',
          ...thresholdingValues
        }
      },
      {
        alias: 'removeHoles',
        module: 'RemoveHoles',
        values: {
          input: '{threshold.output}|COMPUTED',
          output: '{this.id}_cells_image|COMPUTED',
          value: '{parent.holesSize}|COMPUTED'
        }
      },
      {
        alias: 'watershed',
        module: 'Watershed',
        values: {
          input: '{removeHoles.output}|COMPUTED',
          output: '{parent.name}|COMPUTED',
          generate: 'Markers',
          markers: nucleiSeeds,
          mask: '{removeHoles.output}|COMPUTED'
        }
      }
    ];
  }
};

export default findCells;
