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

const MEASUREMENTS_GROUP = 'Measurement';

const measureObjectSizeShape = {
  name: 'MeasureObjectSizeShape',
  group: MEASUREMENTS_GROUP,
  output: '',
  parameters: [
    'Select object sets to measure|object|MULTIPLE|ALIAS objects',
    'Calculate the Zernike features?|flag|true|ALIAS zernike',
    'Calculate the advanced features?|flag|false|ALIAS advancedFeatures'
  ]
};

const measureObjectIntensity = {
  name: 'MeasureObjectIntensity',
  group: MEASUREMENTS_GROUP,
  parameters: [
    'Select images to measure|file|MULTIPLE|ALIAS images',
    'Select objects to measure|object|MULTIPLE|ALIAS objects'
  ]
};

const measureImageAreaOccupied = {
  name: 'MeasureImageAreaOccupied',
  group: MEASUREMENTS_GROUP,
  parameters: [
    'Measure the area occupied by|[Binary Image,Objects,Both]|Binary Image|ALIAS method',
    'Select binary images to measure|file|MULTIPLE|ALIAS images|IF method=="Binary Image" OR method=="Both"',
    'Select object sets to measure|object|MULTIPLE|ALIAS objects|IF method=="Objects" OR method=="Both"'
  ]
};

const measureImageIntensity = {
  name: 'MeasureImageIntensity',
  group: MEASUREMENTS_GROUP,
  parameters: [
    'Select images to measure|file|MULTIPLE|ALIAS images',
    'Measure the intensity only from areas enclosed by objects?|flag|false|ALIAS measureFromAreas',
    'Select input object sets|object|MULTIPLE|ALIAS objects|IF measureFromAreas==true',
    'Calculate custom percentiles|flag|false|ALIAS percentiles',
    'Specify percentiles to measure|string|10,90|IF percentiles==true'
  ]
};

const measureObjectSkeleton = {
  name: 'MeasureObjectSkeleton',
  group: MEASUREMENTS_GROUP,
  parameters: [
    'Select the seed objects|object|Nuclei|REQUIRED|ALIAS seed',
    'Select the skeletonized image|file|REQUIRED|ALIAS image'
  ]
};

export default [
  measureImageAreaOccupied,
  measureImageIntensity,
  measureObjectIntensity,
  measureObjectSizeShape,
  measureObjectSkeleton
];
