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

const expandOrShrinkObjects = {
  name: 'ExpandOrShrinkObjects',
  group: 'Object Processing',
  output: 'output|object',
  parameters: [
    'Select the input objects|object|REQUIRED|ALIAS input',
    'Name the output objects|string|REQUIRED|ALIAS output',
    'Select the operation|["Shrink objects to a point","Expand objects until touching","Add partial dividing lines between objects","Shrink objects by a specified number of pixels","Expand objects by a specified number of pixels","Skeletonize each object","Remove spurs"]|ALIAS operation',
    'Size by which to expand or shrink|units|PARAMETER Number of pixels by which to expand or shrink|ALIAS size|IF operation=="Shrink objects by a specified number of pixels" OR operation=="Expand objects by a specified number of pixels" OR operation=="Remove spurs"',
    'Fill holes in objects|flag|false|ALIAS shrink|PARAMETER Fill holes in objects so that all objects shrink to a single point|IF operation=="Shrink objects to a point" OR operation=="Shrink objects by a specified number of pixels"'
  ]
};

export default expandOrShrinkObjects;
