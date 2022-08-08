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

const closing = {
  name: 'Closing',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|LOCAL|ALIAS shape',
    'Size|integer|1|LOCAL|ALIAS size',
    'Structuring element|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

const dilateImage = {
  name: 'DilateImage',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|LOCAL|ALIAS shape',
    'Size|integer|1|LOCAL|ALIAS size',
    'Structuring element|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

const erodeImage = {
  name: 'ErodeImage',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|LOCAL|ALIAS shape',
    'Size|units|1|LOCAL|ALIAS size',
    'Structuring element|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

const dilateObjects = {
  name: 'DilateObjects',
  group: 'Advanced',
  output: 'name|object',
  parameters: [
    'Select the input object|object',
    'Name the output object|ALIAS name',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|LOCAL|ALIAS shape',
    'Size|integer|1|LOCAL|ALIAS size',
    'Structuring element|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

const erodeObjects = {
  name: 'ErodeObjects',
  group: 'Advanced',
  output: 'output|object',
  parameters: [
    'Select the input object|object|ALIAS input',
    'Name the output object|ALIAS output',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|LOCAL|ALIAS shape',
    'Size|integer|1|LOCAL|ALIAS size',
    'Prevent object removal|boolean|true',
    'Relabel resulting objects|boolean|false',
    'Structuring element|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

const fillObjects = {
  name: 'FillObjects',
  group: 'Advanced',
  output: 'name|object',
  parameters: [
    'Select the input object|object',
    'Name the output object|ALIAS name',
    'Minimum hole size|float|64.0',
    'Planewise fill|boolean|false'
  ]
};

const gaussianFilter = {
  name: 'GaussianFilter',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name',
    'Sigma|integer|1'
  ]
};

const matchTemplate = {
  name: 'MatchTemplate',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Image|file',
    'Template|file',
    'Output|ALIAS name'
  ]
};

const medialAxis = {
  name: 'MedialAxis',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name'
  ]
};

const medianFilter = {
  name: 'MedianFilter',
  group: 'Advanced',
  output: 'output|file',
  parameters: [
    'Select the input image|ALIAS input|file',
    'Name the output image|string|MedianFilter|ALIAS output',
    'Window|integer|3|ALIAS value'
  ]
};

const morphologicalSkeleton = {
  name: 'MorphologicalSkeleton',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name'
  ]
};

const opening = {
  name: 'Opening',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|LOCAL|ALIAS shape',
    'Size|integer|1|LOCAL|ALIAS size',
    'Structuring element|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

const reduceNoise = {
  name: 'ReduceNoise',
  group: 'Advanced',
  output: 'name|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS name',
    'Size|integer|7',
    'Distance|integer|11',
    'Cut-off distance|float|0.1'
  ]
};

const removeHoles = {
  name: 'RemoveHoles',
  group: 'Advanced',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|RemoveHoles|ALIAS output',
    'Size of holes to fill|float|1.0|ALIAS value'
  ]
};

const shrinkToObjectCenters = {
  name: 'ShrinkToObjectCenters',
  group: 'Advanced',
  output: 'name|object',
  parameters: [
    'Select the input object|object',
    'Name the output object|ALIAS name'
  ]
};

const watershed = {
  name: 'Watershed',
  group: 'Advanced',
  output: 'output|object',
  parameters: [
    'Select the input image|file|ALIAS input|REQUIRED',
    'Name the output object|string|Watershed|ALIAS output|REQUIRED',
    'Generate from|[Distance,Markers]|Distance|ALIAS generate',
    'Markers|file|IF generate==Markers|ALIAS markers',
    'Mask|file|IF generate==Markers|ALIAS mask|EMPTY Left blank',
    'Connectivity|integer|1|ADVANCED|ALIAS connectivity|IF generate==Markers',
    'Compactness|float|0.0|ADVANCED|ALIAS compactness|ADVANCED|IF generate==Markers',
    'Separate watershed labels|flag|false|ALIAS separateLabels|ADVANCED|IF generate==Markers',
    'Footprint|integer|8|ALIAS footprint',
    'Downsample|integer|1|ALIAS downsample',
    'Declump method|[Shape,Intensity]|Shape|ADVANCED',
    'Segmentation distance transform smoothing factor|float|1.0|ADVANCED',
    'Minimum distance between seeds|integer|1|ADVANCED',
    'Minimum absolute internal distance|float|0.0|ADVANCED',
    'Pixels from border to exclude|integer|0|ADVANCED',
    'Maximum number of seeds|integer|-1|ADVANCED',
    'Shape|[Ball,Cube,Diamond,Disk,Octahedron,Square,Star]|Disk|ADVANCED|LOCAL|ALIAS shape',
    'Size|integer|1|ADVANCED|LOCAL|ALIAS size',
    'Structuring element for seed dilation|string|{shape},{size}|COMPUTED|HIDDEN'
  ]
};

export default [
  closing,
  dilateImage,
  erodeImage,
  dilateObjects,
  erodeObjects,
  fillObjects,
  gaussianFilter,
  matchTemplate,
  medialAxis,
  medianFilter,
  morphologicalSkeleton,
  opening,
  reduceNoise,
  removeHoles,
  shrinkToObjectCenters,
  watershed
];
