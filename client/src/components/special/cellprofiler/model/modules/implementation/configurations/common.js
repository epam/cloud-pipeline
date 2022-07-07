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

export function getParentParameter (modules, parameter) {
  if (!modules) {
    return undefined;
  }
  const parent = modules.parent;
  if (!parent) {
    return undefined;
  }
  return parent.getParameterValue(parameter);
}

function getThresholdingMethod (modules) {
  return getParentParameter(modules, 'thresholdingMethod');
}

const globalThresholdingIf = `(thresholdingMethod=="Minimum Cross-Entropy" OR
 thresholdingMethod=="Otsu (two classes)" OR
 thresholdingMethod=="Otsu (three classes)" OR
 thresholdingMethod=="Manual")`;

const adaptiveThresholdingIf = `(thresholdingMethod!=="Minimum Cross-Entropy" AND
 thresholdingMethod!=="Otsu (two classes)" AND
 thresholdingMethod!=="Otsu (three classes)" AND
 thresholdingMethod!=="Manual")`;

const thresholding = (options = {}) => {
  const {
    method = 'Adaptive Otsu (three classes)',
    manualDefault = 1,
    manualThresholdRange
  } = options;
  let manualThresholdType = 'float';
  if (manualThresholdRange && manualThresholdRange.length === 2) {
    manualThresholdType = `float(${manualThresholdRange[0]},${manualThresholdRange[1]})`;
  }
  return {
    getThresholdingMethod,
    parameters: [
      'Configure threshold|flag|false|ALIAS configureThreshold|ADVANCED',
      `Thresholding method|["Manual","Minimum Cross-Entropy","Adaptive Minimum Cross-Entropy","Otsu (two classes)","Otsu (three classes)","Adaptive Otsu (two classes)","Adaptive Otsu (three classes)",Savuola]|${method}|ADVANCED|ALIAS thresholdingMethod|IF configureThreshold==true`,
      // Thresholding - common
      'Threshold smoothing scale|float(0,2)|1.3488|ALIAS thresholdSmoothingScale|ADVANCED|IF configureThreshold==true',
      'Threshold correction factor|float(0,2)|1.0|IF thresholdingMethod!==Manual AND configureThreshold==true|ALIAS thresholdCorrectionFactor|ADVANCED',
      `Adaptive size|integer|50|IF configureThreshold==true AND ${adaptiveThresholdingIf}|ALIAS adaptive|ADVANCED`,
      `Manual threshold|${manualThresholdType}|${manualDefault}|IF ${globalThresholdingIf}|ALIAS manualThreshold|ADVANCED`
    ],
    values: {
      strategy: (cpModule, modules) => {
        const method = getThresholdingMethod(modules);
        switch (method) {
          case 'Adaptive Minimum Cross-Entropy':
          case 'Adaptive Otsu (two classes)':
          case 'Adaptive Otsu (three classes)':
          case 'Savuola':
            return 'Adaptive';
          default:
            return 'Global';
        }
      },
      thresholdingMethod: (cpModule, modules) => {
        const method = getThresholdingMethod(modules);
        switch (method) {
          case 'Minimum Cross-Entropy':
          case 'Adaptive Minimum Cross-Entropy':
            return 'Minimum Cross-Entropy';
          case 'Otsu (two classes)':
          case 'Adaptive Otsu (two classes)':
          case 'Otsu (three classes)':
          case 'Adaptive Otsu (three classes)':
            return 'Otsu';
          case 'Savuola':
            return 'Savuola';
          case 'Manual':
            return 'Manual';
          default:
            return 'Minimum Cross-Entropy';
        }
      },
      otsuMethodType: (cpModule, modules) => {
        const method = getThresholdingMethod(modules);
        switch (method) {
          case 'Otsu (two classes)':
          case 'Adaptive Otsu (two classes)':
            return 'Two classes';
          case 'Otsu (three classes)':
          case 'Adaptive Otsu (three classes)':
            return 'Three classes';
          default:
            return 'Two classes';
        }
      },
      otsuThreePixels: 'Foreground|COMPUTED',
      thresholdSmoothingScale: '{parent.thresholdSmoothingScale}|COMPUTED',
      thresholdCorrectionFactor: '{parent.thresholdCorrectionFactor}|COMPUTED',
      adaptive: '{parent.adaptive}|COMPUTED',
      manualThreshold: '{parent.manualThreshold}|COMPUTED',
      clumpedObjectsMethod: 'Shape'
    }
  };
};

export {thresholding};
