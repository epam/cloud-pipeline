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
  name: 'IdentifyPrimaryObjects',
  group: 'Object Processing',
  output: 'name|object',
  sourceImageParameter: 'input',
  parameters: [
    'Use advanced settings?|flag|true|HIDDEN',
    'Select the input image|file|ALIAS input|REQUIRED',
    'Name the primary objects to be identified|string|IdentifyPrimaryObjects|ALIAS=name|REQUIRED',
    'Typical diameter of objects (Min,Max)|units[0,Infinity]|[1, 6]|ALIAS=diameterRange|PARAMETER Typical diameter of objects, in pixel units (Min,Max)',
    'Discard objects outside the diameter range?|flag|true|ALIAS discardObjectsOutside',
    'Discard objects touching the border of the image?|flag|true|ALIAS discardObjectsTouchingBorder',

    // Thresholding
    'Threshold strategy|[Global,Adaptive]|ADVANCED|ALIAS strategy',
    `Thresholding method|[Minimum Cross-Entropy,Otsu,Robust Background,Savuola|if strategy==Adaptive,Measurement|if strategy!=Adaptive,Manual|if strategy!=Adaptive]|Minimum Cross-Entropy|ALIAS thresholdingMethod|ADVANCED`,

    // Thresholding > Otsu
    'Two-class or three-class thresholding?|[Two classes, Three classes]|Two classes|IF thresholdingMethod==Otsu|ADVANCED|ALIAS otsuMethodType',
    'Assign pixels in the middle intensity class to the foreground or the background?|[Background,Foreground]|Background|IF (thresholdingMethod==Otsu AND otsuMethodType=="Three classes")|ADVANCED|ALIAS otsuThreePixels',

    // Thresholding > Robust Background
    'Lower outlier fraction|float|ADVANCED|IF thresholdingMethod=="Robust Background"|ALIAS lowerOutlierFraction',
    'Upper outlier fraction|float|ADVANCED|IF thresholdingMethod=="Robust Background"|ALIAS upperOutlierFraction',
    'Averaging method|[Mean,Median,Mode]|ADVANCED|IF thresholdingMethod=="Robust Background"|ALIAS robustAveragingMethod',
    'Variance method|[Standard deviation,Median absolute deviation]|ADVANCED|IF thresholdingMethod=="Robust Background"|ALIAS varianceMethod',
    '# of deviations|integer|ADVANCED|IF thresholdingMethod=="Robust Background"|ALIAS deviations',

    // Thresholding > Measurement
    {
      name: 'Select the measurement to threshold with',
      parameterName: 'Select the measurement to threshold with',
      isList: true,
      advanced: true,
      /**
       * @param {AnalysisModule} cpModule
       * @returns {{title: *, value: string}[]}
       */
      values: (cpModule) => {
        let inputName = 'input';
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
        cpModule.getParameterValue('thresholdingMethod') === 'Measurement'
    },

    // Thresholding > Manual
    'Manual threshold|float|ADVANCED|IF thresholdingMethod==Manual AND strategy==Global|ALIAS manualThreshold',

    // Thresholding - common
    'Threshold smoothing scale|float|1.3488|ALIAS thresholdSmoothingScale|ADVANCED',
    'Threshold correction factor|float|1.0|IF thresholdingMethod!==Manual|ALIAS thresholdCorrectionFactor|ADVANCED',
    'Lower and upper bounds on threshold|float[]|[0.0,1.0]|IF thresholdingMethod!==Manual|ADVANCED|ALIAS bounds',
    'Size of adaptive window|units|25|IF strategy==Adaptive|ADVANCED|ALIAS adaptive',
    'Log transform before thresholding?|flag|false|ADVANCED|IF thresholdingMethod==Otsu OR thresholdingMethod=="Minimum Cross-Entropy"|ALIAS logTransform',

    // ----
    'Method to distinguish clumped objects|[Intensity,Shape,None]|Intensity|ADVANCED|ALIAS clumpedObjectsMethod',
    'Method to draw dividing lines between clumped objects|[None,Intensity,Shape,Propagate]|Intensity|ADVANCED|ALIAS clumpedObjectsDrawMethod|IF clumpedObjectsMethod!=None',
    'Automatically calculate size of smoothing filter for declumping?|flag|true|ADVANCED|IF clumpedObjectsMethod!=None AND clumpedObjectsDrawMethod!=None|ALIAS declumpingAutoSize',
    'Size of smoothing filter|integer|IF declumpingAutoSize==true',
    'Automatically calculate minimum allowed distance between local maxima?|flag|true|ADVANCED|ALIAS declumpingAutoMinDistance|IF clumpedObjectsMethod!=None AND clumpedObjectsDrawMethod!=None',
    'Suppress local maxima that are closer than this minimum allowed distance|integer|ADVANCED|IF declumpingAutoMinDistance==false',
    'Speed up by using lower-resolution image to find local maxima?|flag|true|ADVANCED|IF clumpedObjectsMethod!=None AND clumpedObjectsDrawMethod!=None',
    'Display accepted local maxima?|flag|false|ALIAS displayLocalMaxima|ADVANCED|IF clumpedObjectsMethod!=None AND clumpedObjectsDrawMethod!=None',
    'Select maxima color|color|#FFFFFF|ADVANCED|IF displayLocalMaxima==true',
    'Select maxima size|integer|1|ADVANCED|IF displayLocalMaxima==true',
    'Fill holes in identified objects?|[After both thresholding and declumping,After declumping only,Never]|ADVANCED',
    'Handling of objects if excessive number of objects identified|[Continue,Erase]|Continue|ADVANCED|ALIAS excessiveMethod',
    'Maximum number of objects|integer|500|ADVANCED|IF excessiveMethod==Erase'
  ]
};
