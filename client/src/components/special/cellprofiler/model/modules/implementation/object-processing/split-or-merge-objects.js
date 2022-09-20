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

const SplitOrMergeObjects = {
  name: 'SplitOrMergeObjects',
  group: 'Object Processing',
  output: 'output|object',
  parameters: [
    'Select the input objects|object|REQUIRED|ALIAS input',
    'Name the new objects|string|Relabeled|REQUIRED|ALIAS output',
    'Operation|[Merge,Split]|Merge|ALIAS operation',
    'Merging method|[Distance,Per-parent]|Distance|ALIAS mergingMethod|IF operation=="Merge"',
    'Maximum distance within which to merge objects|units|0|ALIAS maxDistance|IF operation=="Merge" AND mergingMethod=="Distance"',
    'Merge using grayscale image?|flag|false|IF operation=="Merge" AND mergingMethod=="Distance"|ALIAS useGrayScaleImage',
    'Select the grayscale image to guide merging|file|IF operation=="Merge" AND mergingMethod=="Distance" AND useGrayScaleImage==true|ALIAS grayScaleImage',
    'Minimum intensity fraction|float|0.9|ALIAS intensityFraction|IF operation=="Merge" AND mergingMethod=="Distance" AND useGrayScaleImage==true',
    'Method to find object intensity|[Closest point,Centroids]|Closest point|ALIAS grayScaleImageMethod|IF operation=="Merge" AND mergingMethod=="Distance" AND useGrayScaleImage==true',
    'Output object type|[Disconnected,Convex hull]|Disconnected|ALIAS outputObjectType|IF operation=="Merge" AND mergingMethod=="Per-parent"',
    'Select the parent object|object|ALIAS parentObject|IF operation=="Merge" AND mergingMethod=="Per-parent"'
  ]
};

export default SplitOrMergeObjects;
