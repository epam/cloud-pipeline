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

const methods = {
  enhanceAndIdentify: 'Enhance & Identify',
  enhanceAndWatershed: 'Enhance & Watershed'
};

const {
  parameters,
  values
} = thresholding({
  method: 'Manual',
  manualDefault: 0.075
});

const findSpots = {
  name: 'FindSpots',
  output: 'output|object',
  sourceImageParameter: 'input',
  composed: true,
  parameters: [
    'Parent objects|object|ALIAS parentObject|REQUIRED',
    'Select the input image|file|ALIAS input|REQUIRED',
    'Objects name|string|Spots|ALIAS output|REQUIRED',
    `Method|[${[methods.enhanceAndIdentify].map(o => `"${o}"`).join(',')}]|"${methods.enhanceAndIdentify}"|ALIAS mainMethod`,
    'Enhance feature by|units|2|ADVANCED|ALIAS featureSize|PARAMETER Feature size',
    `Typical diameter of objects (Min,Max)|units[0,Infinity]|[0.1, 5]|ALIAS=diameterRange|ADVANCED|IF mainMethod==${methods.enhanceAndIdentify}|PARAMETER Typical diameter of objects, in pixel units (Min,Max)`,
    ...parameters
  ],
  subModules: (cpModule) => {
    const enhance = {
      module: 'EnhanceOrSuppressFeatures',
      alias: 'enhance',
      values: {
        input: '{parent.input}|COMPUTED',
        output: '{this.id}_{parent.input}_enhanced|COMPUTED',
        operation: 'Enhance',
        feature: 'Speckles',
        featureSize: '{parent.featureSize}|COMPUTED'
      }
    };
    const rescale = {
      alias: 'rescale',
      module: 'RescaleIntensity',
      values: {
        input: '{enhance.output}|COMPUTED',
        output: '{enhance.output}_rescaled|COMPUTED',
        method: 'Stretch each image to use the full intensity range'
      }
    };

    const method = cpModule.getParameterValue('mainMethod');
    switch (method) {
      case methods.enhanceAndWatershed:
        return [
          enhance,
          rescale
        ];
      default:
      case methods.enhanceAndIdentify:
        return [
          enhance,
          rescale,
          {
            alias: 'identify',
            module: 'IdentifyPrimaryObjects',
            values: {
              input: '{rescale.output}|COMPUTED',
              name: '{this.id}_identified|COMPUTED',
              diameterRange: '{parent.diameterRange}|COMPUTED',
              ...values
            }
          },
          {
            alias: 'mask',
            module: 'MaskObjects',
            values: {
              input: '{identify.name}|COMPUTED',
              output: '{parent.output}|COMPUTED',
              method: 'Objects',
              maskingObject: '{parent.parentObject}|COMPUTED',
              overlap: 'Keep overlapping region'
            }
          },
          {
            alias: 'relate',
            module: 'RelateObjects',
            values: {
              parent: '{parent.parentObject}|COMPUTED',
              child: '{parent.output}|COMPUTED'
            }
          }
        ];
    }
  }
};

export default findSpots;
