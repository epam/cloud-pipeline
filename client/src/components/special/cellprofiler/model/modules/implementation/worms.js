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

const identifyDeadWorms = {
  name: 'IdentifyDeadWorms',
  group: 'Worm Toolbox',
  output: 'name|object',
  parameters: [
    'Select the input image|file',
    'Name the dead worm objects to be identified|string|DeadWorms|ALIAS name',
    'Worm width|integer|10',
    'Worm length|integer|100',
    'Number of angles|integer|32',
    'Automatically calculate distance parameters?|boolean|true|ALIAS calculate',
    'Spatial distance|float|5.0|IF calculate==false',
    'Angular distance|float|300.0|IF calculate==false'
  ]
};

const straightenWorms = {
  name: 'StraightenWorms',
  group: 'Worm Toolbox',
  outputs: ['worms|object', 'fileName|file|IF input!==""'],
  parameters: [
    'Select the input untangled worm objects|object',
    'Name the output straightened worm objects|string|StraightenedWorms|ALIAS worms',
    'Worm width|integer|20',
    // 'Training set file location|[Elsewhere...,Default Input Folder,Default Output Folder,Default Input Folder sub-folder,Default Output Folder sub-folder,URL]|Elsewhere...|ALIAS=location',
    // 'Sub-folder:|string|IF (location!="Default Input Folder" AND location!="Default Output Folder")',
    // 'Training set file name|string|TrainingSet.xml',
    'Measure intensity distribution?|boolean|true',
    'Number of transverse segments|integer|4',
    'Number of longitudinal stripes|integer|3',
    'Align worms?|[Do not align,Top brightest,Bottom brightest,Flip manually]|Do not align',
    'Select an input image to straighten|file|ALIAS input',
    'Name the output straightened image|ALIAS fileName|IF input!==""'
  ]
};

const untangleWorms = {
  name: 'UntangleWorms',
  group: 'Worm Toolbox',
  output: 'object ("Name the output non-overlapping worm objects")',
  outputs: [
    'overlapping|object|IF (overlap==Both OR overlap=="With overlap")',
    'overlappedImage|file|IF (retain==true AND (overlap==Both OR overlap=="With overlap"))',
    'nonOverlapping|object|IF (overlap==Both OR overlap=="Without overlap")',
    'nonOverlappedImage|file|IF (retainNonOverlapping==true AND (overlap==Both OR overlap=="Without overlap"))'
  ],
  parameters: [
    'Train or untangle worms?|[Untangle,Train]|Untangle',
    'Select the input binary image',
    'Overlap style|[Both,With overlap,Without overlap]|Both|ALIAS overlap',
    'Name the output overlapping worm objects|ALIAS overlapping|IF (overlap==Both OR overlap=="With overlap")',
    'Retain outlines of the overlapping objects?|boolean|false|ALIAS retain|IF (overlap==Both OR overlap=="With overlap")',
    'Name the overlapped outline image|ALIAS overlappedImage|IF (retain==true AND (overlap==Both OR overlap=="With overlap"))',
    'Name the output non-overlapping worm objects|ALIAS nonOverlapping|IF (overlap==Both OR overlap=="Without overlap")',
    'Retain outlines of the non-overlapping worms?|boolean|false|ALIAS retainNonOverlapping|IF (overlap==Both OR overlap=="Without overlap")',
    'Name the non-overlapped outlines image|ALIAS nonOverlappedImage|IF (retainNonOverlapping==true AND (overlap==Both OR overlap=="Without overlap"))',
    'Maximum complexity|[Medium,High,Very high,Process all clusters,Custom]|High|ALIAS complexity',
    'Custom complexity|integer|400|IF complexity==Custom',
    // 'Training set file location|[Elsewhere...,Default Input Folder,Default Output Folder,Default Input Folder sub-folder,Default Output Folder sub-folder,URL]|Elsewhere...|ALIAS=location',
    // 'Sub-folder:|string|IF (location!="Default Input Folder" AND location!="Default Output Folder")',
    // 'Training set file name|string|TrainingSet.xml',
    'Use training set weights?|boolean|true|ALIAS useWeights',
    'Overlap weight|float|5.0|IF useWeights==false',
    'Leftover weight|float|10.0|IF useWeights==false'
  ]
};

export default [
  identifyDeadWorms,
  straightenWorms,
  untangleWorms
];
