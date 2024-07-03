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

const {
  parameters: thresholdingParameters,
  values: thresholdingValues
} = thresholding({
  strategy: 'Adaptive',
  thresholdingMethod: 'Robust Background',
  thresholdSmoothingScale: 0,
  lowerOutlierFraction: 0,
  upperOutlierFraction: 0,
  deviations: 0
});

const findNeurites = {
  name: 'FindNeurites',
  composed: true,
  sourceImageParameter: 'input',
  output: 'output|object',
  parameters: [
    'Select the soma/cell objects|object|Cells|REQUIRED|ALIAS soma|DEFAULT_FROM FindCells',
    'Select the input image|file|ALIAS input|REQUIRED|ALIAS input',
    'Neurites objects name|string|Neurites|REQUIRED|ALIAS output',
    'Rescale input image intensity|flag|true|ADVANCED|ALIAS rescaleBefore',
    'Enhancement method|[Tubeness,Line structures]|Tubeness|ADVANCED|ALIAS enhanceMethod',
    'Smoothing scale|float|0.5|IF (enhanceMethod==Tubeness)|ADVANCED|ALIAS smoothingScale',
    'Feature size|units|2|IF (enhanceMethod=="Line structures")|ADVANCED|ALIAS featureSize',
    'Rescale result image|flag|true|ADVANCED|ALIAS rescaleResult',
    ...thresholdingParameters
  ],
  /**
   * @param {AnalysisModule} cpModule
   * @returns {*[]}
   */
  subModules: (cpModule) => {
    const getValue = prop => cpModule ? cpModule.getParameterValue(prop) : undefined;
    const rescaleBefore = getValue('rescaleBefore');
    const modules = [];
    let input = '{parent.input}|COMPUTED';
    if (rescaleBefore) {
      modules.push({
        alias: 'prepare',
        module: 'RescaleIntensity',
        values: {
          input,
          output: '{this.id}_rescaled|COMPUTED'
        }
      });
      input = '{prepare.output}|COMPUTED';
    }
    return [
      ...modules,
      {
        alias: 'enhance',
        module: 'EnhanceOrSuppressFeatures',
        values: {
          input,
          output: '{this.id}_enhanced|COMPUTED',
          operation: 'Enhance',
          feature: 'Neurites',
          method: '{parent.enhanceMethod}|COMPUTED',
          smoothingScale: '{parent.smoothingScale}|COMPUTED',
          featureSize: '{parent.featureSize}|COMPUTED',
          rescaleResult: '{parent.rescaleResult}|COMPUTED'
        }
      },
      {
        alias: 'identify',
        module: 'IdentifySecondaryObjects',
        values: {
          inputObjects: '{parent.soma}|COMPUTED',
          input: '{enhance.output}|COMPUTED',
          name: '{parent.output}|COMPUTED',
          method: 'Watershed - Image',
          ...thresholdingValues,
          fillHoles: false,
          discardObjectsTouchingBorder: true,
          discardPrimaryObjects: false
        }
      },
      {
        alias: 'convert',
        module: 'ConvertObjectsToImage',
        values: {
          input: '{parent.output}|COMPUTED',
          output: '{this.id}_converted|COMPUTED',
          format: 'Grayscale'
        }
      },
      {
        alias: 'morph',
        module: 'Morph',
        values: {
          input: '{convert.output}|COMPUTED',
          output: '{this.id}_morph|COMPUTED',
          operation: 'skelpe',
          numberOfTimes: 'Once'
        }
      },
      {
        alias: 'measureObjectSkeleton',
        module: 'MeasureObjectSkeleton',
        values: {
          seed: '{parent.soma}|COMPUTED',
          image: '{morph.output}|COMPUTED'
        }
      }
    ];
  }
};

export default findNeurites;
