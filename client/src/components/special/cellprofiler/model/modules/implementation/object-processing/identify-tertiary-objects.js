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

export default {
  name: 'IdentifyTertiaryObjects',
  group: 'Object Processing',
  output: 'output|object',
  parameters: [
    'Select the larger identified objects|object|ALIAS large|REQUIRED',
    'Select the smaller identified objects|object|ALIAS small|REQUIRED',
    'Name the tertiary objects to be identified|string|Cytoplasm|ALIAS output|REQUIRED',
    'Shrink smaller object prior to subtraction?|flag|false|ALIAS shrink'
  ]
};
