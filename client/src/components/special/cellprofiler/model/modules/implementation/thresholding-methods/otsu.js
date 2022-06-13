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

import thresholdMethods from './methods';
import {ListParameter} from '../../../parameters';
import visibilityHandlerGenerator from './visibility-handler-generator';

const otsuMethodTypes = {
  twoClass: 'Two classes',
  threeClass: 'Three classes'
};

const assignPixelsTo = {
  background: 'Background',
  foreground: 'Foreground'
};

export default [
  [ListParameter, {
    name: 'otsuMethodType',
    parameterName: 'Two-class or three-class thresholding?',
    title: 'Two-class or Tree-class',
    values: [
      otsuMethodTypes.twoClass,
      otsuMethodTypes.threeClass
    ],
    visibilityHandler: visibilityHandlerGenerator(thresholdMethods.otsu),
    value: otsuMethodTypes.twoClass
  }],
  [ListParameter, {
    name: 'assignPixels',
    title: 'Assign pixels in the middle intensity class to',
    parameterName:
      'Assign pixels in the middle intensity class to the foreground or the background?',
    visibilityHandler: visibilityHandlerGenerator(
      thresholdMethods.otsu,
      (cpModule) => cpModule.getParameterValue('otsuMethodType') === otsuMethodTypes.threeClass
    ),
    values: [
      assignPixelsTo.background,
      assignPixelsTo.foreground
    ],
    value: assignPixelsTo.background
  }]
];
