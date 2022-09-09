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

export default {
  name: 'IdentifySecondaryObjects',
  group: 'Object Processing',
  outputs: ['name|object', 'newName|object|IF (discardPrimaryObjects==true AND discardObjectsTouchingBorder==true)'],
  sourceImageParameter: 'input',
  parameters: [
    'Select the input image|file|ALIAS input|REQUIRED',
    'Select the input objects|object|ALIAS inputObjects|REQUIRED|DEFAULT_FROM IdentifyPrimaryObjects',
    'Name the objects to be identified|string|IdentifySecondaryObjects|ALIAS=name|REQUIRED',
    'Select the method to identify the secondary objects|[Propagation,Watershed - Gradient,Watershed - Image,Distance - N,Distance - B]|Distance - N|ALIAS method',

    // Thresholding
    'Threshold strategy|[Global,Adaptive]|ADVANCED|ALIAS strategy|IF method!=="Distance - N"',
    `Thresholding method|[Minimum Cross-Entropy,Otsu,Robust Background,Savuola|IF strategy==Adaptive,Measurement|IF strategy!=Adaptive,Manual|IF strategy!=Adaptive]|Minimum Cross-Entropy|ALIAS thresholdingMethod|ADVANCED|IF method!=="Distance - N"`,

    // Thresholding > Otsu
    'Two-class or three-class thresholding?|[Two classes, Three classes]|Two classes|IF thresholdingMethod==Otsu|ADVANCED|ALIAS otsuMethodType|IF method!=="Distance - N"|ALIAS otsuMethodType',
    'Assign pixels in the middle intensity class to the foreground or the background?|[Background,Foreground]|Background|IF (thresholdingMethod==Otsu AND otsuMethodType=="Three classes" AND method!=="Distance - N")|ADVANCED|ALIAS otsuThreePixels',

    // Thresholding > Robust Background
    'Lower outlier fraction|float|ADVANCED|IF (thresholdingMethod=="Robust Background" AND method!=="Distance - N")|ALIAS lowerOutlierFraction',
    'Upper outlier fraction|float|ADVANCED|IF (thresholdingMethod=="Robust Background" AND method!=="Distance - N")|ALIAS upperOutlierFraction',
    'Averaging method|[Mean,Median,Mode]|ADVANCED|IF (thresholdingMethod=="Robust Background" AND method!=="Distance - N")|ALIAS robustAveragingMethod',
    'Variance method|[Standard deviation,Median absolute deviation]|ADVANCED|IF (thresholdingMethod=="Robust Background" AND method!=="Distance - N")|ALIAS varianceMethod',
    '# of deviations|integer|ADVANCED|IF (thresholdingMethod=="Robust Background" AND method!=="Distance - N")|ALIAS deviations',

    // Thresholding > Measurement
    {
      title: 'Select the measurement to threshold with',
      parameterName: 'Select the measurement to threshold with',
      isList: true,
      advanced: true,
      /**
       * @param {AnalysisModule} cpModule
       * @returns {{title: *, value: string}[]}
       */
      values: (cpModule) => {
        let inputName = 'input123';
        if (cpModule) {
          const outputs = cpModule.channels || [];
          if (outputs.length) {
            inputName = outputs[0].name;
          }
        }
        return ['FileName', 'Frame', 'Height', 'MD5Digest', 'PathName', 'Scaling', 'Series', 'URL', 'Width']
          .map(method => ({title: method, value: `${method}_${inputName}`}));
      },
      visibilityHandler: (cpModule) =>
        cpModule.getParameterValue('thresholdingMethod') === 'Measurement' &&
        cpModule.getParameterValue('method') !== 'Distance - N'
    },

    // Thresholding > Manual
    'Manual threshold|float|ADVANCED|IF (thresholdingMethod==Manual AND strategy==Global AND method!=="Distance - N")|ALIAS manualThreshold',

    // Thresholding - common
    'Threshold smoothing scale|float|1.3488|ALIAS thresholdSmoothingScale|ADVANCED|IF method!=="Distance - N"',
    'Threshold correction factor|float|1.0|IF (thresholdingMethod!==Manual AND method!=="Distance - N")|ALIAS thresholdCorrectionFactor|ADVANCED',
    'Lower and upper bounds on threshold|float[]|[0.0,1.0]|IF (thresholdingMethod!==Manual AND method!=="Distance - N")|ADVANCED|ALIAS bounds',
    'Size of adaptive window|integer|50|IF (strategy==Adaptive AND method!=="Distance - N")|ADVANCED|ALIAS adaptive',
    'Log transform before thresholding?|flag|false|ADVANCED|IF (method!=="Distance - N" AND (thresholdingMethod==Otsu OR thresholdingMethod=="Minimum Cross-Entropy"))|ALIAS logTransform',

    // ----
    'Regularization factor|float|0.05|IF method==Propagation',
    'Number of pixels by which to expand the primary objects|integer|IF method=="Distance - N"',
    'Fill holes in identified objects?|flag|true|ALIAS fillHoles',
    'Discard secondary objects touching the border of the image?|flag|false|ALIAS discardObjectsTouchingBorder',
    'Discard the associated primary objects?|flag|false|IF discardObjectsTouchingBorder==true|ALIAS discardPrimaryObjects',
    'Name the new primary objects|IF (discardPrimaryObjects==true AND discardObjectsTouchingBorder==true)|ALIAS newName'
  ]
};
