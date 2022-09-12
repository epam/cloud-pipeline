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

import advanced from './advanced';
import fileProcessing from './file-processing';
import objectsProcessing from './object-processing';
import {defineResults, defineResultsInternal} from './define-results';
import findCells from './find-cells';
import findCytoplasm from './find-cytoplasm';
import findMembrane from './find-membrane';
import findNuclei from './find-nuclei';
import findNeurites from './find-neurites';
import findSpots from './find-spots';
import imageProcessing from './image-processing';
import measurement from './measurement';
// import worms from './worms';

const cellProfilerModules = [
  ...fileProcessing,
  ...objectsProcessing,
  ...advanced,
  ...imageProcessing,
  ...measurement,
  // ...worms,
  defineResultsInternal
];

export {cellProfilerModules};

export default [
  ...[
    findNuclei,
    findCells,
    findCytoplasm,
    findMembrane,
    findNeurites,
    findSpots,
    defineResults
  ].map(o => ({group: 'Main', ...o})),
  ...cellProfilerModules
];
