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

const findMembrane = {
  name: 'FindMembrane',
  composed: true,
  output: 'output|object',
  parameters: [
    'Cells objects|object|ALIAS cells|REQUIRED|DEFAULT_FROM FindCells',
    'Objects name|string|Membrane|ALIAS output|REQUIRED',
    'Outer border distance|units|0.5|REQUIRED|ALIAS outer',
    'Inner border distance|units|0.5|REQUIRED|ALIAS inner'
  ],
  subModules: [
    {
      alias: 'expand',
      module: 'ExpandOrShrinkObjects',
      values: {
        input: '{parent.cells}|COMPUTED',
        output: '{this.id}_outer|COMPUTED',
        operation: 'Expand objects by a specified number of pixels',
        size: '{parent.outer}|COMPUTED'
      }
    },
    {
      alias: 'shrink',
      module: 'ExpandOrShrinkObjects',
      values: {
        input: '{parent.cells}|COMPUTED',
        output: '{this.id}_inner|COMPUTED',
        operation: 'Shrink objects by a specified number of pixels',
        size: '{parent.inner}|COMPUTED'
      }
    },
    {
      alias: 'result',
      module: 'IdentifyTertiaryObjects',
      values: {
        large: '{expand.output}|COMPUTED',
        small: '{shrink.output}|COMPUTED',
        output: '{parent.output}|COMPUTED',
        shrink: false
      }
    }
  ]
};

export default findMembrane;
