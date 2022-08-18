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
import {thresholding} from './common';
import {
  filterObjectsBySizeParameters,
  wrapLastModuleWithFilterObjectsModule
} from './object-processing/filter-objects';

const {
  parameters: thresholdingParameters,
  values: thresholdingValues
} = thresholding({
  strategy: 'Adaptive',
  thresholdingMethod: 'Minimum Cross-Entropy'
});

const findNuclei = {
  name: 'FindNuclei',
  parameters: [
    'Select the input image|file|ALIAS input|REQUIRED',
    'Objects name|string|Nuclei|ALIAS name|REQUIRED',
    ...filterObjectsBySizeParameters,
    'Downsample|flag|false|ADVANCED|ALIAS downsample',
    'Downsample factor, %|float(0, 100)|50|ADVANCED|IF downsample==true|ALIAS downsampleFactor',
    'Fill holes of size|units|10|ADVANCED|ALIAS holeSize|PARAMETER Remove holes of size, px',
    ...thresholdingParameters
  ],
  output: 'name|object',
  sourceImageParameter: 'input',
  composed: true,
  subModules: (cpModule) => [
    {
      alias: 'rescale',
      module: 'RescaleIntensity',
      values: {
        input: '{parent.input}|COMPUTED',
        output: '{this.id}_{parent.input}_rescaled|COMPUTED',
        method: 'Stretch each image to use the full intensity range'
      }
    },
    {
      alias: 'resize',
      module: 'Resize',
      values: {
        input: '{rescale.output}|COMPUTED',
        output: '{this.id}_{rescale.output}_resized|COMPUTED',
        method: 'Resize by a fraction or multiple of the original size',
        factor: (module, modules) => {
          const parent = modules.parent;
          if (parent) {
            const downsample = parent.getParameterValue('downsample');
            const downsampleFactor = Number(parent.getParameterValue('downsampleFactor'));
            if (`${downsample}` === 'false') {
              return 1.0;
            }
            if (!Number.isNaN(downsampleFactor) && downsampleFactor > 0) {
              return 1.0 - (downsampleFactor / 100.0);
            }
          }
          return 0.5;
        }
      }
    },
    {
      alias: 'median',
      module: 'MedianFilter',
      values: {
        input: '{resize.output}|COMPUTED',
        output: '{this.id}_{resize.output}_median|COMPUTED',
        value: 3
      }
    },
    {
      alias: 'threshold',
      module: 'Threshold',
      values: {
        input: '{median.output}|COMPUTED',
        output: '{this.id}_{median.output}_threshold|COMPUTED',
        ...thresholdingValues
      }
    },
    {
      alias: 'removeHoles',
      module: 'RemoveHoles',
      values: {
        input: '{threshold.output}|COMPUTED',
        output: '{this.id}_{threshold.output}_remove_holes|COMPUTED',
        value: '{parent.holeSize}|COMPUTED'
      }
    },
    {
      alias: 'watershed',
      module: 'Watershed',
      values: {
        input: '{removeHoles.output}|COMPUTED',
        output: '{this.id}_watershed|COMPUTED',
        footprint: 10,
        downsample: 2
      }
    },
    ...wrapLastModuleWithFilterObjectsModule(
      cpModule,
      {
        alias: 'resizeObjects',
        module: 'ResizeObjects',
        values: {
          input: '{watershed.output}|COMPUTED',
          output: '{parent.name}|COMPUTED',
          factor: (module, modules) => {
            const resize = modules.resize;
            if (resize) {
              const value = Number(resize.getParameterValue('factor'));
              if (!Number.isNaN(value) && value > 0) {
                return 1.0 / value;
              }
            }
            return 1;
          }
        }
      }
    )
  ]
};

export default findNuclei;
