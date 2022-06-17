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

const findNuclei = {
  name: 'FindNuclei',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name|string|Nuclei|ALIAS name',
    'Downsample|flag|true|ADVANCED|ALIAS downsample',
    'Downsample factor, %|float(0, 100)|50|ADVANCED|IF downsample==true|ALIAS downsampleFactor',
    'Remove holes of size, px|float(0, 50)|5|ADVANCED|ALIAS holeSize',

    // Thresholding
    'Threshold strategy|[Global,Adaptive]|Global|ADVANCED|ALIAS strategy',
    `Thresholding method|[Minimum Cross-Entropy,Otsu,Robust Background,Savuola|if strategy==Adaptive,Measurement|if strategy!=Adaptive,Manual|if strategy!=Adaptive]|Minimum Cross-Entropy|ADVANCED|ALIAS thresholdingMethod`,

    // Thresholding > Manual
    'Manual threshold|float|0.0|IF thresholdingMethod==Manual AND strategy==Global|thresholdingMethod|ALIAS manualThreshold',

    // Thresholding - common
    'Threshold smoothing scale|float(0,1.3488)|0.0|ALIAS thresholdSmoothingScale|ADVANCED',
    'Threshold correction factor|float(0,10)|1.0|IF thresholdingMethod!==Manual|ALIAS thresholdCorrectionFactor|ADVANCED',
    // 'Lower and upper bounds on threshold|float[]|[0.0,1.0]|IF thresholdingMethod!==Manual|ADVANCED|ALIAS bounds'
  ],
  output: 'name|object',
  composed: true,
  pipeline: [
    {
      alias: 'rescale',
      module: 'RescaleIntensity',
      values: {
        input: '{parent.input}|COMPUTED',
        output: '{this.uuid}_{parent.input}_rescaled|COMPUTED',
        method: 'Stretch each image to use the full intensity range'
      }
    },
    {
      alias: 'resize',
      module: 'Resize',
      values: {
        input: '{rescale.output}|COMPUTED',
        output: '{this.uuid}_{rescale.output}_resized|COMPUTED',
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
        output: '{this.uuid}_{resize.output}_median|COMPUTED',
        value: 3
      }
    },
    {
      alias: 'threshold',
      module: 'Threshold',
      values: {
        input: '{median.output}|COMPUTED',
        output: '{this.uuid}_{median.output}_threshold|COMPUTED',
        strategy: '{parent.strategy}|COMPUTED',
        thresholdingMethod: '{parent.thresholdingMethod}|COMPUTED',
        manualThreshold: '{parent.manualThreshold}|COMPUTED',
        thresholdSmoothingScale: '{parent.thresholdSmoothingScale}|COMPUTED',
        thresholdCorrectionFactor: '{parent.thresholdCorrectionFactor}|COMPUTED',
        // bounds: '{parent.bounds}|COMPUTED'
      }
    },
    {
      alias: 'removeHoles',
      module: 'RemoveHoles',
      values: {
        input: '{threshold.output}|COMPUTED',
        output: '{this.uuid}_{threshold.output}_remove_holes|COMPUTED',
        value: '{parent.holeSize}|COMPUTED'
      }
    },
    {
      alias: 'watershed',
      module: 'Watershed',
      values: {
        input: '{removeHoles.output}|COMPUTED',
        output: '{this.uuid}_watershed|COMPUTED',
        footpring: 10,
        downsample: 2
      }
    },
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
  ]
};

export default findNuclei;
