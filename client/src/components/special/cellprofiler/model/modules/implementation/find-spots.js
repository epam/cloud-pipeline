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
  identify: 'Identify',
  watershed: 'Watershed',
  enhanceAndIdentify: 'Enhance & Identify',
  enhanceAndWatershed: 'Enhance & Watershed'
};

const {
  parameters,
  values
} = thresholding({
  strategy: 'Adaptive',
  thresholdingMethod: 'Robust Background'
});

const findSpots = {
  name: 'FindSpots',
  output: 'output|object',
  sourceImageParameter: 'input',
  composed: true,
  parameters: [
    'Parent objects|object|ALIAS parentObject|EMPTY None',
    'Select the input image|file|ALIAS input|REQUIRED',
    'Objects name|string|Spots|ALIAS output|REQUIRED',
    `Method|[${Object.values(methods).map(o => `"${o}"`).join(',')}]|"${methods.enhanceAndWatershed}"|ALIAS mainMethod`,
    'Deviations|integer|{deviations}|IF thresholdingMethod=="Robust Background"|COMPUTED',
    'Rescale input image intensity|flag|false|ALIAS rescale|ADVANCED',
    `Enhance feature by|units|2|ADVANCED|IF mainMethod=="${methods.enhanceAndIdentify}" OR mainMethod=="${methods.enhanceAndWatershed}"|ALIAS featureSize|PARAMETER Feature size`,
    `Typical diameter of objects (Min,Max)|units[0,Infinity]|[0.1, 5]|ALIAS=diameterRange|ADVANCED|IF mainMethod=="${methods.enhanceAndIdentify}" OR mainMethod==${methods.identify}|PARAMETER Typical diameter of objects, in pixel units (Min,Max)`,
    'Handling of overlapping objects|[Keep,Remove,Keep overlapping region,Remove depending on overlap]|Remove|ALIAS overlap|IF parentObject!=None',
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
    const thresholdModule = input => ({
      module: 'Threshold',
      alias: 'threshold',
      values: {
        input,
        output: '{this.id}_threshold|COMPUTED',
        ...values
      }
    });
    const rescaleFn = input => ({
      alias: 'rescale',
      module: 'RescaleIntensity',
      values: {
        input,
        output: '{this.id}_rescaled|COMPUTED',
        method: 'Stretch each image to use the full intensity range'
      }
    });
    const parentObject = cpModule.getParameterValue('parentObject');
    const parentObjectIsSet = parentObject && !/^none$/i.test(parentObject);
    let objectUtilityName;
    const identifyFn = input => {
      let output = '{this.id}_identified|COMPUTED';
      if (!parentObjectIsSet) {
        output = '{parent.output}|COMPUTED';
      }
      objectUtilityName = 'identify.name';
      return {
        alias: 'identify',
        module: 'IdentifyPrimaryObjects',
        values: {
          input,
          name: output,
          diameterRange: '{parent.diameterRange}|COMPUTED',
          ...values
        }
      };
    };
    const watershedFn = input => {
      let output = '{this.id}_watershed|COMPUTED';
      if (!parentObjectIsSet) {
        output = '{parent.output}|COMPUTED';
      }
      objectUtilityName = 'watershed.output';
      return {
        alias: 'watershed',
        module: 'Watershed',
        values: {
          input,
          output,
          generate: 'Distance',
          footprint: 8,
          downsample: 1
        }
      };
    };

    const method = cpModule.getParameterValue('mainMethod');
    const rescaleInput = cpModule.getBooleanParameterValue('rescale');
    const result = [];
    switch (method) {
      case methods.identify:
        if (rescaleInput) {
          result.push(rescaleFn('{parent.input}|COMPUTED'));
          result.push(identifyFn('{rescale.output}|COMPUTED'));
        } else {
          result.push(identifyFn('{parent.input}|COMPUTED'));
        }
        break;
      case methods.enhanceAndIdentify:
        result.push(enhance);
        if (rescaleInput) {
          result.push(rescaleFn('{enhance.output}|COMPUTED'));
          result.push(identifyFn('{rescale.output}|COMPUTED'));
        } else {
          result.push(identifyFn('{enhance.output}|COMPUTED'));
        }
        break;
      case methods.enhanceAndWatershed:
        result.push(enhance);
        result.push(thresholdModule('{enhance.output}|COMPUTED'));
        if (rescaleInput) {
          result.push(rescaleFn('{threshold.output}|COMPUTED'));
          result.push(watershedFn('{rescale.output}|COMPUTED'));
        } else {
          result.push(watershedFn('{threshold.output}|COMPUTED'));
        }
        break;
      default:
      case methods.watershed:
        result.push(thresholdModule('{parent.input}|COMPUTED'));
        if (rescaleInput) {
          result.push(rescaleFn('{threshold.output}|COMPUTED'));
          result.push(watershedFn('{rescale.output}|COMPUTED'));
        } else {
          result.push(watershedFn('{threshold.output}|COMPUTED'));
        }
        break;
    }
    if (parentObjectIsSet) {
      result.push({
        alias: 'mask',
        module: 'MaskObjects',
        values: {
          input: `{${objectUtilityName}}|COMPUTED`,
          output: '{parent.output}|COMPUTED',
          method: 'Objects',
          maskingObject: '{parent.parentObject}|COMPUTED',
          overlap: '{parent.overlap}|COMPUTED'
        }
      });
      result.push({
        alias: 'relate',
        module: 'RelateObjects',
        values: {
          parent: '{parent.parentObject}|COMPUTED',
          child: '{parent.output}|COMPUTED'
        }
      });
    }
    return result;
  }
};

export default findSpots;
