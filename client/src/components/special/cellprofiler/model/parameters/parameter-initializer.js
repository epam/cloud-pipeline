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

import {AnalysisTypes} from '../common/analysis-types';
import {ObjectParameter} from './object-parameter';
import {FileParameter} from './file-parameter';
import {ChannelsParameter} from './channels-parameter';
import {ModuleParameter} from './base';
import {IntegerRangeParameter} from './range-parameter';
import {FloatParameter} from './simple-parameters';

/**
 * @param {ModuleParameterOptions} [options]
 */
export default function parameterInitializer (options = {}) {
  switch (options.type) {
    case AnalysisTypes.object:
      return new ObjectParameter(options);
    case AnalysisTypes.file:
      return new FileParameter(options);
    case AnalysisTypes.channel:
      return new ChannelsParameter(options);
    case AnalysisTypes.integer: {
      if (options.isRange) {
        return new IntegerRangeParameter(options);
      }
      return new ModuleParameter(options);
    }
    case AnalysisTypes.float: {
      if (options.isRange) {
        return new FloatParameter(options);
      }
      return new ModuleParameter(options);
    }
    default:
      return new ModuleParameter(options);
  }
}
