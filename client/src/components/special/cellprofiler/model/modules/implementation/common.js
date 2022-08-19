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

const thresholdingOld = (options = {}) => {
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
      'Threshold smoothing scale|float|1.3488|ALIAS thresholdSmoothingScale|ADVANCED|IF configureThreshold==true',
      'Threshold correction factor|float|1.0|IF thresholdingMethod!==Manual AND configureThreshold==true|ALIAS thresholdCorrectionFactor|ADVANCED',
      `Adaptive size|units|10|IF configureThreshold==true AND ${adaptiveThresholdingIf}|ALIAS adaptive|ADVANCED`,
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

const thresholding = (options = {}) => {
  const {
    strategy = 'Global',
    thresholdingMethod = 'Minimum Cross-Entropy',
    manualDefault = 1,
    otsuMethodType = 'Two classes',
    otsuThreePixels = 'Background',
    lowerOutlierFraction = 0.05,
    upperOutlierFraction = 0.05,
    robustAveragingMethod = 'Mean',
    varianceMethod = 'Standard deviation',
    deviations = 2,
    manualThreshold = manualDefault,
    manualThresholdRange,
    thresholdSmoothingScale = 1.3488,
    thresholdCorrectionFactor = 1.0,
    bounds = [0.0, 1.0],
    adaptive = 10,
    logTransform = false,
    condition,
    prefix = ''
  } = options;
  let manualThresholdType = 'float';
  if (manualThresholdRange && manualThresholdRange.length === 2) {
    manualThresholdType = `float(${manualThresholdRange[0]},${manualThresholdRange[1]})`;
  }
  const property = name => `{parent.${prefix}${name}}|COMPUTED`;
  const displayCondition = condition ? `(${condition})` : `${prefix}configureThreshold==true`;
  return {
    parameters: [
      condition ? undefined : `Configure threshold|flag|false|ALIAS ${prefix}configureThreshold`,
      // Thresholding
      `Threshold strategy|[Global,Adaptive]|${strategy}|ALIAS ${prefix}strategy|IF ${displayCondition}`,
      `Thresholding method|[Minimum Cross-Entropy,Otsu,Robust Background,Savuola|IF ${prefix}strategy==Adaptive,Manual|IF ${prefix}strategy!=Adaptive]|${thresholdingMethod}|IF ${displayCondition}|ALIAS ${prefix}thresholdingMethod`,

      // Thresholding > Otsu
      `Two-class or three-class thresholding?|[Two classes, Three classes]|${otsuMethodType}|IF (${prefix}thresholdingMethod==Otsu AND ${displayCondition})|ALIAS ${prefix}otsuMethodType`,
      `Assign pixels in the middle intensity class to the foreground or the background?|[Background,Foreground]|${otsuThreePixels}|IF (${prefix}thresholdingMethod==Otsu AND ${prefix}otsuMethodType=="Three classes" AND ${displayCondition})|ALIAS ${prefix}otsuThreePixels`,

      // Thresholding > Robust Background
      `Lower outlier fraction|float|${lowerOutlierFraction}|IF (${prefix}thresholdingMethod=="Robust Background" AND ${displayCondition})|ALIAS ${prefix}lowerOutlierFraction`,
      `Upper outlier fraction|float|${upperOutlierFraction}|IF (${prefix}thresholdingMethod=="Robust Background" AND ${displayCondition})|ALIAS ${prefix}upperOutlierFraction`,
      `Averaging method|[Mean,Median,Mode]|${robustAveragingMethod}|IF (${prefix}thresholdingMethod=="Robust Background" AND ${displayCondition})|ALIAS ${prefix}robustAveragingMethod`,
      `Variance method|[Standard deviation,Median absolute deviation]|${varianceMethod}|IF (${prefix}thresholdingMethod=="Robust Background" AND ${displayCondition})|ALIAS ${prefix}varianceMethod`,
      `# of deviations|integer|${deviations}|IF (${prefix}thresholdingMethod=="Robust Background" AND ${displayCondition})|ALIAS ${prefix}deviations`,

      // Thresholding > Manual
      `Manual threshold|${manualThresholdType}|${manualThreshold}|IF (${prefix}thresholdingMethod==Manual AND ${prefix}strategy==Global AND ${displayCondition})|ALIAS ${prefix}manualThreshold`,

      // Thresholding - common
      `Threshold smoothing scale|float|${thresholdSmoothingScale}|ALIAS ${prefix}thresholdSmoothingScale|IF ${displayCondition}`,
      `Threshold correction factor|float|${thresholdCorrectionFactor}|IF (${prefix}thresholdingMethod!==Manual AND ${displayCondition})|ALIAS ${prefix}thresholdCorrectionFactor`,
      `Lower and upper bounds on threshold|float[]|[${bounds.join(',')}]|IF (${prefix}thresholdingMethod!==Manual AND ${displayCondition})|ALIAS ${prefix}bounds`,
      `Size of adaptive window|units|${adaptive}|IF (${prefix}strategy==Adaptive AND ${displayCondition})|ALIAS ${prefix}adaptive`,
      `Log transform before thresholding?|flag|${logTransform}|IF ((${prefix}thresholdingMethod==Otsu OR ${prefix}thresholdingMethod=="Minimum Cross-Entropy") AND ${displayCondition})|ALIAS ${prefix}logTransform`
    ].filter(Boolean),
    values: {
      strategy: property('strategy'),
      thresholdingMethod: property('thresholdingMethod'),
      otsuMethodType: property('otsuMethodType'),
      otsuThreePixels: property('otsuThreePixels'),
      lowerOutlierFraction: property('lowerOutlierFraction'),
      upperOutlierFraction: property('upperOutlierFraction'),
      robustAveragingMethod: property('robustAveragingMethod'),
      varianceMethod: property('varianceMethod'),
      deviations: property('deviations'),
      manualThreshold: property('manualThreshold'),
      thresholdSmoothingScale: property('thresholdSmoothingScale'),
      thresholdCorrectionFactor: property('thresholdCorrectionFactor'),
      bounds: property('bounds'),
      adaptive: property('adaptive'),
      logTransform: property('logTransform')
    }
  };
};

export {thresholding};
