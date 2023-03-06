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
  name: 'ConvertObjectsToImage',
  group: 'Object Processing',
  output: 'output|file',
  parameters: [
    'Select the input objects|object|ALIAS input|REQUIRED',
    'Name the output image|ALIAS output|REQUIRED',
    'Select the color format|[Color,"Binary (black & white)",Grayscale,uint16]|Color|ALIAS format',
    'Select the colormap|[Default]|Default|COMPUTED|HIDDEN'
  ]
};
