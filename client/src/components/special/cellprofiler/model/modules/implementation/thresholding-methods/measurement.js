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

import {
  ListParameter
} from '../../../parameters';
import thresholdMethods from './methods';
import visibilityHandlerGenerator from './visibility-handler-generator';

const measurementMethods = {
  fileName: 'FileName',
  frame: 'Frame',
  height: 'Height',
  md5digest: 'MD5Digest',
  pathName: 'PathName',
  scaling: 'Scaling',
  series: 'Series',
  url: 'URL',
  width: 'Width'
};

export default [
  [
    ListParameter,
    {
      name: 'measurementMethod',
      title: 'Measurement to threshold with',
      parameterName: 'Select the measurement to threshold with',
      values: (module) => {
        let inputName = 'Input';
        if (module && module.analysis && module.analysis.namesAndTypes) {
          const outputs = module.analysis.namesAndTypes.outputs || [];
          if (outputs.length) {
            inputName = outputs[0].value;
          }
        }
        return [
          measurementMethods.fileName,
          measurementMethods.frame,
          measurementMethods.height,
          measurementMethods.md5digest,
          measurementMethods.pathName,
          measurementMethods.scaling,
          measurementMethods.series,
          measurementMethods.url,
          measurementMethods.width
        ].map(method => ({title: method, value: `${method}_${inputName}`}));
      },
      visibilityHandler: visibilityHandlerGenerator(thresholdMethods.measurement)
    }
  ]
];
